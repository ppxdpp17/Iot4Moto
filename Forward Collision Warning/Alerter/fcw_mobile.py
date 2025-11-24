# fcw_mobile.py (Termux / Android - FED)
import paho.mqtt.client as mqtt
import subprocess
import json
import threading
import sys
import os
import time

# --- CONFIGURAÇÃO ---
MQTT_BROKER = "192.168.1.130" # IP do PC Broker
MQTT_PORT = 1883
TOPIC_ALERT = "fcw/alerta"

# Controlo de Vibração Contínua
VIBRATE_STOP = threading.Event()
VIBRATE_THREAD = None

def vibrate_pattern():
    """Vibra continuamente (loop) até ordem de paragem"""
    while not VIBRATE_STOP.is_set():
        try: 
            # Vibra 500ms
            subprocess.run(["termux-vibrate", "-d", "500", "-f"], check=False)
        except: 
            pass
        # Pausa entre vibrações
        VIBRATE_STOP.wait(0.5)

def start_vibration():
    """Inicia a thread de vibração se não estiver a correr"""
    global VIBRATE_THREAD
    if VIBRATE_THREAD and VIBRATE_THREAD.is_alive(): 
        return
    VIBRATE_STOP.clear()
    VIBRATE_THREAD = threading.Thread(target=vibrate_pattern, daemon=True)
    VIBRATE_THREAD.start()

def stop_vibration():
    """Pára a vibração"""
    VIBRATE_STOP.set()

def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print("✅ FCW Display Conectado.")
        client.subscribe(TOPIC_ALERT)
    else:
        print(f"❌ Erro conexão: {rc}")

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

        # Dados do Alerta
        source = data.get("source", "SENSOR")
        dist = data.get("dist_m", 0)
        ttc = data.get("ttc_s", 0)
        mensagem = data.get("msg", "CUIDADO")
        
        # Interface Visual
        print("\n" + "🟥"*30)
        print(f" ⚠️  {mensagem}  ⚠️")
        print("🟥"*30)
        print(f"\nDistância: {dist}m")
        print(f"Fonte: {source}") # Mostra se foi Câmara ou Ultrassom
        print(f"TTC: {ttc}s\n")
        print("🟥"*30 + "\n")

        # Feedback Físico (Vibração e Notificação)
        if level == "RED":
            start_vibration()
            # Notificação de alta prioridade
            subprocess.run(["termux-notification", "--title", "TRAVA!", "--content", f"{dist}m - {source}", "--priority", "high", "--id", "101"])
        
        elif level == "YELLOW":
            stop_vibration()
            subprocess.run(["termux-vibrate", "-d", "200"]) # Vibra só uma vez

    except Exception as e:
        print(f"Erro ao processar mensagem: {e}")

# --- MAIN ---
client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1, "Termux_FCW")
client.on_connect = on_connect
client.on_message = on_message

try:
    print(f"A conectar a {MQTT_BROKER}...")
    client.connect(MQTT_BROKER, MQTT_PORT, 60)
    client.loop_forever()
except KeyboardInterrupt:
    stop_vibration()
    print("Sair.")