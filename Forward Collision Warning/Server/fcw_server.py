# fcw_blackbox.py  (PC Windows - Logger)
# Requisitos: pip install paho-mqtt

import paho.mqtt.client as mqtt
import json
import csv
import time
from pathlib import Path

# --- CONFIGURAÇÃO ---
MQTT_BROKER = "172.20.0.130"  # IP do PC (o próprio PC)
MQTT_PORT = 1883
TOPIC_LOG = "fcw/radar"
TOPIC_ALERT = "fcw/alerta"

# Setup de Logging
DATA_DIR = Path.cwd() / "logs_fcw"
DATA_DIR.mkdir(exist_ok=True)
LOG_FILE = DATA_DIR / f"fusion_log_{int(time.time())}.csv"

with LOG_FILE.open("w", newline="", encoding="utf-8") as f:
    csv.writer(f).writerow(["timestamp", "topic", "source", "dist_m", "vel_ms", "ttc", "alert_msg"])

print(f"--- FCW BLACKBOX ---")
print(f"Gravando dados de fusão em: {LOG_FILE}")

def on_log(client, userdata, level, buf):
    print("MQTT-LOG:", level, buf)

def on_connect(client, userdata, flags, rc):
    print("Conectado ao Broker. rc=", rc)
    client.subscribe([(TOPIC_LOG, 0), (TOPIC_ALERT, 1)])

def on_message(client, userdata, msg):
    try:
        data = json.loads(msg.payload.decode())
        current_time = time.time()
        row = [current_time, msg.topic, "", "", "", "", ""]
        if msg.topic == TOPIC_LOG:
            row[2] = data.get("source", "N/A")
            row[3] = data.get("dist_m")
            row[4] = data.get("vel_ms")
            row[5] = data.get("ttc")
            print(f"REC: {row[2]} -> {row[3]}m (TTC: {row[5]}s)")
        elif msg.topic == TOPIC_ALERT:
            row[6] = f"{data.get('alert_level')} - {data.get('msg')}"
            print(f">>> ALERTA: {row[6]}")
        with LOG_FILE.open("a", newline="", encoding="utf-8") as f:
            csv.writer(f).writerow(row)
    except Exception as e:
        print("Erro ao processar mensagem:", e)

# --- MAIN ---
client = mqtt.Client(client_id="Blackbox_FCW", protocol=mqtt.MQTTv311)
client.on_connect = on_connect
client.on_message = on_message
client.on_log = on_log

try:
    client.connect(MQTT_BROKER, MQTT_PORT, 60)
    client.loop_forever()
except KeyboardInterrupt:
    print("Blackbox Encerrada.")
except Exception as e:
    print("Erro de conexão MQTT:", e)
