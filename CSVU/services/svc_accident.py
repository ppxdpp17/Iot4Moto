import sys
import os
import json
import time
import paho.mqtt.client as mqtt
from smbus import SMBus

# --- SETUP CAMINHOS ---
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import config
from lib import imu_driver

# --- SETUP HARDWARE ---
bus = SMBus(1)
imu_driver.init_mpu(bus)

# --- SETUP MQTT ---
client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1, "CSVU_Accident_Svc")
try:
    client.connect(config.MQTT_BROKER_CLOUD, 1883, 60)
    print(f"-> Serviço ACIDENTE ligado: {config.MQTT_BROKER_CLOUD}")
except Exception as e:
    print(f"- Modo Offline: {e}")

client.loop_start()

# --- VARIAVEIS DE ESTADO ---
impact_detected = False
impact_ts = 0
last_svm = 0

print(">>> A monitorizar sensor... (Abana o sensor!)")

try:
    while True:
        data = imu_driver.read_raw_data(bus)
        
        if data:
            svm = data['svm']

            # DEBUG: Mostra leitura a cada segundo para veres se o sensor funciona
            if int(time.time() * 10) % 10 == 0: 
                print(f"Leitura Atual: {svm/9.81:.2f} G")
            
            # 1. DETEÇÃO DE IMPACTO (Mudámos para > 1.2G para ser sensível)
            if svm > config.SVM_THRESHOLD and not impact_detected:
                impact_detected = True
                impact_ts = time.time()
                last_svm = svm
                print(f"*** IMPACTO DETETADO: {svm:.1f} m/s² ***")

            # 2. VALIDAÇÃO DE QUEDA (Janela de 5s)
            if impact_detected:
                # Se passaram menos de 5s desde o impacto
                if (time.time() - impact_ts) < 5.0:
                    # Verifica inclinação (Roll)
                    if abs(data['roll']) > config.ANGLE_THRESHOLD:
                        print("!!! QUEDA CONFIRMADA! A enviar alerta... !!!")
                        
                        payload = {
                            "type": "ACCIDENT",
                            "msg": "QUEDA DETETADA!",
                            "svm": round(last_svm, 2),
                            "gps": "38.716, -9.139"
                        }

                        client.publish(config.TOPIC_ALERT_ACCIDENT, json.dumps(payload), qos=2)
                        
                        impact_detected = False
                        time.sleep(5) # Pausa para não enviar spam
                else:
                    impact_detected = False # Passaram 5s, foi só um buraco na estrada

        time.sleep(0.05) # 20Hz

except KeyboardInterrupt:
    bus.close()