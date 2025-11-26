# fcw_mobile.py (Termux / Android - FED)
import paho.mqtt.client as mqtt
import subprocess
import json
import threading
import sys
import os
import time

# --- CONFIGURAÇÃO ---
# ⚠️ ATENÇÃO: Confirma se este é o IP atual do teu PC com o comando 'ipconfig'
MQTT_BROKER = "172.20.0.130" 
MQTT_PORT = 1883
TOPIC_ALERT = "fcw/alerta"

# Controlo de Vibração Contínua
VIBRATE_STOP = threading.Event()
VIBRATE_THREAD = None

def vibrate_pattern():
    """Vibra continuamente (loop) até ordem de paragem"""
    while not VIBRATE_STOP.is_set():
        try: 
            subprocess.run(["termux-vibrate", "-d", "500", "-f"], check=False)
        except: 
            pass
        VIBRATE_STOP.wait(0.5)

def start_vibration():
    global VIBRATE_THREAD
    if VIBRATE_THREAD and VIBRATE_THREAD.is_alive(): 
        return
    VIBRATE_STOP.clear()
    VIBRATE_THREAD = threading.Thread(target=vibrate_pattern, daemon=True)
    VIBRATE_THREAD.start()

def stop_vibration():
    VIBRATE_STOP.set()

def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print("✅ FCW Display Conectado.")
        client.subscribe(TOPIC_ALERT)
    else:
        print(f"❌ Erro conexão MQTT (Código {rc})")

def on_message(client, userdata, msg):
    try:
        data = json.loads(msg.payload.decode())
        level = data.get("alert_level", "GREEN")
        
        os.system('clear')
        
        if level == "GREEN":
            stop_vibration()
            print("\n" + "🟩"*30)
            print("         CAMINHO LIVRE")
            print("🟩"*30 + "\n")
            return

        source = data.get("source", "SENSOR")
        dist = data.get("dist_m", 0)
        ttc = data.get("ttc_s", 0)
        mensagem = data.get("msg", "CUIDADO")
        
        print("\n" + "🟥"*30)
        print(f" ⚠️  {mensagem}  ⚠️")
        print("🟥"*30)
        print(f"\nDistância: {dist}m")
        print(f"Fonte: {source}")
        print(f"TTC: {ttc}s\n")
        print("🟥"*30 + "\n")

        if level == "RED":
            start_vibration()
            # Notificação de alta prioridade
            try:
                subprocess.run(["termux-notification", "--title", "TRAVA!", "--content", f"{dist}m - {source}", "--priority", "high", "--id", "101"], check=False)
            except: pass
        
        elif level == "YELLOW":
            stop_vibration()
            try:
                subprocess.run(["termux-vibrate", "-d", "200"], check=False)
            except: pass

    except Exception as e:
        print(f"Erro ao processar mensagem: {e}")

# --- MAIN ---
# CORREÇÃO AQUI: Adicionado CallbackAPIVersion.VERSION1
client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1, "Termux_FCW")
client.on_connect = on_connect
client.on_message = on_message

print(f"A tentar conectar a {MQTT_BROKER}...")

try:
    # Tenta conectar com timeout de 10s
    client.connect(MQTT_BROKER, MQTT_PORT, 60)
    client.loop_forever()

except TimeoutError:
    print("\n❌ ERRO: Timeout de conexão!")
    print(f"O telemóvel não consegue chegar a {MQTT_BROKER}.")
    print("1. Verifica se o IP do PC mudou (ipconfig).")
    print("2. Verifica se a Firewall do Windows permite a porta 1883.")
    print("3. Verifica se estão na mesma rede Wi-Fi.")

except KeyboardInterrupt:
    stop_vibration()
    print("\nSair.")
except Exception as e:
    print(f"\nErro fatal: {e}")