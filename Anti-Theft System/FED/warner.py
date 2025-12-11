#Código que irá correr no smartphone para alertar o utilizador de movimentos na moto e mostrar feed da câmara
import paho.mqtt.client as mqtt
import json
import os
import sys
import subprocess
import threading

#---CONFIGURAÇÃO MQTT---
MQTT_BROKER = "172.20.0.130" #IP do PC Windows (funciona apenas como Broker)
IP_RASPBERRY = "172.20.0.131" #IP do Raspberry Pi onde está a câmara
VIDEO_URL = f"http://{IP_RASPBERRY}:5000/video" #URL do feed da câmara

#Tópicos MQTT
TOPIC_CMD = "moto/seguranca/comando"
TOPIC_ALERT = "moto/seguranca/alerta"

#Função para abrir o feed de vídeo da câmara
def open_camera_feed():
    """Abre o stream de vídeo no browser ou player do Android"""
    print(f"A abrir câmara: {VIDEO_URL}")
    try:
        #Comando Termux para abrir URL no Android
        subprocess.run(["termux-open-url", VIDEO_URL])
    except Exception as e:
        print(f"Erro ao abrir vídeo: {e}")

#Função para disparar notificação de alarme
def trigger_alarm_notification(msg):
    try:
        #Vibração forte
        subprocess.run(["termux-vibrate", "-d", "2000", "-f"])

        #Notificação no telemóvel
        subprocess.run([
            "termux-notification",
            "--title", "🚨 ALERTA DE ROUBO",
            "--content", msg,
            "--priority", "high",
            "--action", f"termux-open-url {VIDEO_URL}" #Botão na notificação para ver vídeo
        ])
    except: pass

#Conexão MQTT
def on_connect(client, userdata, flags, rc):
    print("Chave Digital Conectada.")
    client.subscribe(TOPIC_ALERT)

#Função principal de receção de mensagens
def on_message(client, userdata, msg):
    try:
        data = json.loads(msg.payload.decode())
        tipo = data.get("tipo")
        texto = data.get("msg")
        
        #Se o alerta for "ROUBO", mostra alerta e abre feed da câmara
        if tipo == "ROUBO":
            os.system('clear')
            print("!!! ALERTA DE SEGURANÇA !!!")
            print(f"{texto}")
            print("A ABRIR FEED DA CÂMARA...")
            
            trigger_alarm_notification(texto)
            open_camera_feed() #Abre o vídeo automaticamente
            
        #Se for "INFO", apenas mostra no ecrã
        elif tipo == "INFO":
            print(f"[MOTO]: {texto}")
            
    except: pass

#---INTERFACE DE COMANDO---
def input_loop(client):
    print("\n--- CONTROLO REMOTO MOTO ---")
    print("'A' para ARMAR (Estacionar)")
    print("'D' para DESARMAR (Conduzir)")
    print("'V' para VER CÂMARA")
    print("----------------------------")
    
    #Ciclo de input
    while True:
        try:
            cmd = input("Comando: ").strip().upper()
            #Se for "A", envia "ARMAR" para o tópico MQTT
            if cmd == 'A':
                client.publish(TOPIC_CMD, "ARMAR")
                print(">> Enviado: ARMAR SISTEMA")
            #Se for "D", envia "DESARMAR" para o tópico MQTT
            elif cmd == 'D':
                client.publish(TOPIC_CMD, "DESARMAR")
                print(">> Enviado: DESARMAR SISTEMA")
            #Se for "V", corre função para abrir feed da câmara
            elif cmd == 'V':
                open_camera_feed()
        except EOFError:
            break

client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1, "Termux_Key")
client.on_connect = on_connect
client.on_message = on_message

try:
    client.connect(MQTT_BROKER, 1883, 60)
    client.loop_start()
    
    input_loop(client)
    
except KeyboardInterrupt:
    print("Sair.")
    client.loop_stop()
    client.disconnect()