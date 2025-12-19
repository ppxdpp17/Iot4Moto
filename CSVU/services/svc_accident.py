# /home/pi/amover_project/services/svc_accident.py
import sys
import os
import json
import time
import paho.mqtt.client as mqtt
from smbus import SMBus
import config
from lib import imu_driver

#Importar módulos
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

#---SETUP---
bus = SMBus(1)
imu_driver.init_mpu(bus)

#Cliente MQTT (LCM -> App)
client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1, "CSVU_Accident_Svc")
try:
    client.connect(config.MQTT_BROKER_CLOUD, 1883, 60)
    print(f"->Serviço ACIDENTE ligado à Cloud: {config.MQTT_BROKER_CLOUD}")
except:
    print("-Modo Offline (Cloud indisponível)")

client.loop_start()

#Estados
impact_detected = False
impact_ts = 0
last_svm = 0

try:
    while True:
        data = imu_driver.read_raw_data(bus)
        
        if data:
            svm = data['svm']
            
            #Monitoriza o SVG, e se ultrapassar o limite definido (3g), inicia o processo de deteção de acidente
            if svm > config.SVM_THRESHOLD and not impact_detected:
                impact_detected = True
                impact_ts = time.time()
                last_svm = svm
                print(f"***IMPACTO DETETADO: {svm:.1f} m/s²***")

            #Verifica se um impacto foi uma queda ou apenas um solavanco
            if impact_detected:
                if (time.time() - impact_ts) < 5.0: #Janela de 5s
                    #Se o ângulo de roll exceder o limiar, confirma queda
                    if abs(data['roll']) > config.ANGLE_THRESHOLD:
                        print("!!!QUEDA CONFIRMADA! A enviar alerta...!!!")
                        
                        payload = {
                            "type": "ACCIDENT",
                            "msg": "QUEDA DETETADA! A contactar 112...",
                            "svm": round(last_svm, 2),
                            "gps": "38.716, -9.139" # Simulado
                        }

                        #Publica para a App via MQTT
                        client.publish(config.TOPIC_ALERT_ACCIDENT, json.dumps(payload), qos=2)
                        
                        impact_detected = False
                        time.sleep(5)
                else:
                    impact_detected = False #Timeout
                    
        time.sleep(0.05) #20Hz - para não sobrecarregar o Raspberry

except KeyboardInterrupt:
    bus.close()