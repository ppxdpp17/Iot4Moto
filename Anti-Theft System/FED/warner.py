#Código que irá correr no smartphone para alertar o utilizador de movimentos na moto e mostrar feed da câmara
# remote_key.py (Termux)
import paho.mqtt.client as mqtt
import json
import os
import sys
import subprocess
import threading

# --- CONFIGURAÇÃO ---
MQTT_BROKER = "172.20.0.130" # IP do PC (Broker)
IP_RASPBERRY = "172.20.0.131" # <-- COLOCA O IP DO RASPBERRY AQUI
VIDEO_URL = f"http://{IP_RASPBERRY}:5000/video"

TOPIC_CMD = "moto/seguranca/comando"
TOPIC_ALERT = "moto/seguranca/alerta"

def open_camera_feed():
    """Abre o stream de vídeo no browser ou player do Android"""
    print(f"A abrir câmara: {VIDEO_URL}")
    try:
        # Comando Termux para abrir URL no Android
        subprocess.run(["termux-open-url", VIDEO_URL])
    except Exception as e:
        print(f"Erro ao abrir vídeo: {e}")

def trigger_alarm_notification(msg):
    try:
        # Vibração forte
        subprocess.run(["termux-vibrate", "-d", "2000", "-f"])
        # Notificação
        subprocess.run([
            "termux-notification",
            "--title", "🚨 ALERTA DE ROUBO",
            "--content", msg,
            "--priority", "high",
            "--action", f"termux-open-url {VIDEO_URL}" # Botão na notificação para ver vídeo
        ])
    except: pass

def on_connect(client, userdata, flags, rc):
    print("Chave Digital Conectada.")
    client.subscribe(TOPIC_ALERT)

def on_message(client, userdata, msg):
    try:
        data = json.loads(msg.payload.decode())
        tipo = data.get("tipo")
        texto = data.get("msg")
        
        if tipo == "ROUBO":
            os.system('clear')
            print("\n" + "🚨"*15)
            print("!!! ALERTA DE SEGURANÇA !!!")
            print(f"{texto}")
            print("A ABRIR FEED DA CÂMARA...")
            print("🚨"*15 + "\n")
            
            trigger_alarm_notification(texto)
            open_camera_feed() # Abre o vídeo automaticamente!
            
        elif tipo == "INFO":
            print(f"[MOTO]: {texto}")
            
    except: pass

# --- INTERFACE DE COMANDO ---
def input_loop(client):
    print("\n--- CONTROLO REMOTO MOTO ---")
    print("Digite 'A' para ARMAR (Estacionar)")
    print("Digite 'D' para DESARMAR (Conduzir)")
    print("Digite 'V' para VER CÂMARA")
    print("----------------------------")
    
    while True:
        try:
            cmd = input("Comando: ").strip().upper()
            if cmd == 'A':
                client.publish(TOPIC_CMD, "ARMAR")
                print(">> Enviado: ARMAR SISTEMA")
            elif cmd == 'D':
                client.publish(TOPIC_CMD, "DESARMAR")
                print(">> Enviado: DESARMAR SISTEMA")
            elif cmd == 'V':
                open_camera_feed()
        except EOFError:
            break

# --- MAIN ---
client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1, "Termux_Key")
client.on_connect = on_connect
client.on_message = on_message

try:
    client.connect(MQTT_BROKER, 1883, 60)
    client.loop_start()
    
    # Executa o menu de input
    input_loop(client)
    
except KeyboardInterrupt:
    print("Sair.")
    client.loop_stop()
    client.disconnect()