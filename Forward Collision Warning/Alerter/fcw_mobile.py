# fcw_mobile.py (Termux / Android - FED)
# Requisitos: pip install paho-mqtt ; instalar app Termux:API para comandos termux-vibrate / termux-notification

import paho.mqtt.client as mqtt
import subprocess
import json
import threading
import os
import time

# --- CONFIGURAÇÃO ---
MQTT_BROKER = "172.20.0.130"  # IP do PC com broker
MQTT_PORT = 1883
TOPIC_ALERT = "fcw/alerta"

# Vibração contí­nua controlada por thread
VIBRATE_STOP = threading.Event()
VIBRATE_THREAD = None

def vibrate_pattern():
    while not VIBRATE_STOP.is_set():
        try:
            subprocess.run(["termux-vibrate","-d","500","-f"], check=False)
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
        print("✅ FED Conectado.")
        client.subscribe(TOPIC_ALERT)
    else:
        print("❌ Erro conexão:", rc)

def on_message(client, userdata, msg):
    try:
        data = json.loads(msg.payload.decode())
        level = data.get("alert_level", "GREEN")
        os.system('clear')
        if level == "GREEN":
            stop_vibration()
            print("\n" + "🟩"*22)
            print("        CAMINHO LIVRE")
            print("🟩"*22 + "\n")
            return
        source = data.get("source","SENSOR")
        dist = data.get("dist_m", 0)
        ttc = data.get("ttc_s", 0)
        mensagem = data.get("msg","CUIDADO")
        print("\n" + "🟥"*22)
        print(f" ⚠️  {mensagem}  ⚠️")
        print("🟥"*22)
        print(f"\nDistância: {dist} m")
        print(f"Fonte: {source}")
        print(f"TTC: {ttc} s\n")
        print("🟥"*22 + "\n")
        if level == "RED":
            start_vibration()
            subprocess.run(["termux-notification","--title","TRAVA!","--content",f"{dist}m - {source}","--priority","high","--id","101"])
        elif level == "YELLOW":
            stop_vibration()
            subprocess.run(["termux-vibrate","-d","200"], check=False)
    except Exception as e:
        print("Erro ao processar mensagem:", e)

# --- MAIN ---
client = mqtt.Client(client_id="Termux_FCW", protocol=mqtt.MQTTv311)
client.on_connect = on_connect
client.on_message = on_message

try:
    print("A conectar a", MQTT_BROKER)
    client.connect(MQTT_BROKER, MQTT_PORT, 60)
    client.loop_forever()
except KeyboardInterrupt:
    stop_vibration()
    print("Sair.")


