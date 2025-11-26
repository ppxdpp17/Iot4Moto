# fcw_server.py (Logger Limpo)
import paho.mqtt.client as mqtt
import json
import csv
import time
from pathlib import Path

MQTT_BROKER = "172.20.0.130"
LOG_FILE = Path.cwd() / "logs_fcw" / f"log_{int(time.time())}.csv"
LOG_FILE.parent.mkdir(exist_ok=True)

with LOG_FILE.open("w", newline="") as f:
    csv.writer(f).writerow(["ts", "topic", "dist", "vel", "ttc", "alert"])

print("--- SERVER LOGGER ---")

def on_message(client, userdata, msg):
    try:
        data = json.loads(msg.payload.decode())
        ts = time.strftime("%H:%M:%S")
        
        if msg.topic == "fcw/radar":
            print(f"[{ts}] RADAR: {data.get('source')} -> {data.get('dist_m')}m (Vel: {data.get('vel_ms')}m/s)")
            # Gravar CSV...
        elif msg.topic == "fcw/alerta":
            lvl = data.get('alert_level')
            if lvl != "GREEN": # Só mostra alertas reais na consola
                print(f"[{ts}] ⚠️ ALERTA {lvl}: {data.get('msg')} (TTC: {data.get('ttc_s')}s)")
                
    except: pass

client = mqtt.Client(protocol=mqtt.MQTTv311)
client.connect(MQTT_BROKER, 1883)
client.subscribe([("fcw/radar", 0), ("fcw/alerta", 0)])
client.loop_forever()