# /home/pi/amover_project/services/svc_antitheft.py
import sys
import os
import json
import time
import math
import paho.mqtt.client as mqtt
from smbus import SMBus

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import config
from lib import imu_driver

# --- ESTADO ---
sistema_armado = False
last_g_force = 1.0

# --- MQTT CALLBACKS ---
def on_connect(client, userdata, flags, rc):
    print("✅ Serviço ANTI-ROUBO ligado à Cloud.")
    client.subscribe(config.TOPIC_CMD_SECURITY)

def on_message(client, userdata, msg):
    global sistema_armado
    try:
        payload = json.loads(msg.payload.decode())
        cmd = payload.get("cmd")
        
        if cmd == "ARM":
            sistema_armado = True
            print("🔒 SISTEMA ARMADO")
        elif cmd == "DISARM":
            sistema_armado = False
            print("🔓 SISTEMA DESARMADO")
    except: pass

# --- SETUP ---
bus = SMBus(1)
imu_driver.init_mpu(bus)

client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1, "CSVU_Security_Svc")
client.on_connect = on_connect
client.on_message = on_message

try:
    client.connect(config.MQTT_BROKER_CLOUD, 1883, 60)
    client.loop_start()
except:
    print("⚠️ Erro conexão Cloud")

print("👮 Serviço Anti-Roubo Ativo (Aguardando Comando...)")

try:
    while True:
        # Só lê sensor se estiver armado para poupar recursos
        if sistema_armado:
            data = imu_driver.read_raw_data(bus)
            if data:
                # Calcula Magnitude G (aprox 1.0 em repouso)
                # O driver devolve SVM em m/s2, convertemos para G
                curr_g = data['svm'] / 9.81
                
                delta = abs(curr_g - last_g_force)
                
                if delta > config.THEFT_SENSITIVITY:
                    print(f"⚠️ MOVIMENTO: {delta:.2f}g")
                    alert = {
                        "alert": "SECURITY",
                        "type": "THEFT",
                        "msg": "MOVIMENTO NÃO AUTORIZADO!",
                        "val": f"{delta:.2f}g"
                    }
                    client.publish(config.TOPIC_ALERT_THEFT, json.dumps(alert), qos=1)
                    time.sleep(5) # Debounce
                
                last_g_force = curr_g
                
        time.sleep(0.1)

except KeyboardInterrupt:
    bus.close()