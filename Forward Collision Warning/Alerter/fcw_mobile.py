# fcw_mobile_log.py
import paho.mqtt.client as mqtt
import json
import subprocess

MQTT_BROKER = "172.20.0.130"

def notify(title, msg):
    try: subprocess.run(["termux-notification", "-t", title, "-c", msg, "--id", "101"], check=False)
    except: pass

def on_message(client, userdata, msg):
    try:
        data = json.loads(msg.payload.decode())
        lvl = data.get('alert_level')
        
        # Formatação de linha única
        log_line = f"DIST: {data.get('dist_m')}m | TTC: {data.get('ttc_s')}s | {data.get('msg')}"
        
        if lvl == "RED":
            print(f"🛑 {log_line}")
            subprocess.run(["termux-vibrate", "-d", "500", "-f"])
            notify("PERIGO", data.get('msg'))
        elif lvl == "YELLOW":
            print(f"⚠️  {log_line}")
        # else: print(f"✅ {log_line}") # Descomenta se quiseres ver o verde também
            
    except: pass

client = mqtt.Client(protocol=mqtt.MQTTv311)
client.connect(MQTT_BROKER, 1883)
client.subscribe("fcw/alerta")
print("Monitorização FCW iniciada...")
client.loop_forever()