import paho.mqtt.client as mqtt
import subprocess
import json
import threading
import sys
import os
import time

#---CONFIGURAÇÃO MQTT---
MQTT_BROKER = "172.20.0.130"  #IP do PC onde corre o Broker MQTT 
MQTT_PORT = 1883
TOPIC_ALERT = "fcw/alerta"  #Tópico de alertas FCW

#Controlo de Vibração Contínua
VIBRATE_STOP = threading.Event()
VIBRATE_THREAD = None

#Para enviar notificção de vibração
def vibrate_pattern():
    """Vibra continuamente (loop) até ordem de paragem"""
    while not VIBRATE_STOP.is_set():
        try: 
            subprocess.run(["termux-vibrate", "-d", "500", "-f"], check=False)
        except: 
            pass
        VIBRATE_STOP.wait(0.5)

#Funções de controlo de vibração
def start_vibration():
    global VIBRATE_THREAD
    if VIBRATE_THREAD and VIBRATE_THREAD.is_alive(): 
        return
    VIBRATE_STOP.clear()
    VIBRATE_THREAD = threading.Thread(target=vibrate_pattern, daemon=True)
    VIBRATE_THREAD.start()

def stop_vibration():
    VIBRATE_STOP.set()

#Conexão MQTT
def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print("FCW Display Conectado.")
        client.subscribe(TOPIC_ALERT)
    else:
        print(f"Erro conexão MQTT (Código {rc})")

#Função principal de receção de mensagens
def on_message(client, userdata, msg):
    try:
        data = json.loads(msg.payload.decode())
        #Se não houver alerta, mostra caminho livre
        level = data.get("alert_level", "GREEN")
        
        os.system('clear')
        
        if level == "GREEN":
            stop_vibration()
            print("\n" + "🟩"*30)
            print("         CAMINHO LIVRE")
            print("🟩"*30 + "\n")
            return

        #Mostra detalhes do alerta - sensor, distância, ttc, mensagem
        source = data.get("source", "SENSOR")
        dist = data.get("dist_m", 0)
        ttc = data.get("ttc_s", 0)
        mensagem = data.get("msg", "CUIDADO")
        
        #Mostra alerta no terminal
        print("\n" + "🟥"*30)
        print(f" ⚠️  {mensagem}  ⚠️")
        print("🟥"*30)
        print(f"\nDistância: {dist}m")
        print(f"Fonte: {source}")
        print(f"TTC: {ttc}s\n")
        print("🟥"*30 + "\n")

        #Ações conforme nível de alerta
        if level == "RED":
            start_vibration()
            #Notificação de alta prioridade
            try:
                subprocess.run(["termux-notification", "--title", "TRAVA!", "--content", f"{dist}m - {source}", "--priority", "high", "--id", "101"], check=False)
            except: pass
        
        elif level == "YELLOW":
            stop_vibration()
            #Notificação de média prioridade (AVISO)
            try:
                subprocess.run(["termux-vibrate", "-d", "200"], check=False)
            except: pass

    except Exception as e:
        print(f"Erro ao processar mensagem: {e}")


client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1, "Termux_FCW")
client.on_connect = on_connect
client.on_message = on_message

print(f"A tentar conectar a {MQTT_BROKER}...")

try:
    client.connect(MQTT_BROKER, MQTT_PORT, 60)
    client.loop_forever()

except TimeoutError:
    print("\nERRO: Timeout de conexão!")
    print(f"O telemóvel não consegue chegar a {MQTT_BROKER}.")

except KeyboardInterrupt:
    stop_vibration()
    print("\nSair.")
except Exception as e:
    print(f"\nErro fatal: {e}")