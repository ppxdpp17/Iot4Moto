import sys
import os
import json
import time
import requests
import numpy as np
import paho.mqtt.client as mqtt
from collections import deque
import config
from lib import ultrasonic_driver

#Importar módulos
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

#---SETUP GPIO---
ultrasonic_driver.setup_gpio(config.TRIG_PIN, config.ECHO_PIN)
cam_session = requests.Session()

#Histórico para cálculo de velocidade
history = deque(maxlen=6)   #Não olha apenas para o "agora", mas para as 6 últimas leituras para calcular a velocidade relativa

#MQTT Local (Para o FED via Cabo Ethernet)
client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1, "CSVU_FCW_Svc")
try:
    client.connect(config.MQTT_BROKER_LOCAL, config.MQTT_PORT, 60)
    client.loop_start()
    print("-Serviço FCW ligado ao FED (Local).")
except: print("Erro Broker Local")

#Função para color tracking de UnitV2
def get_camera_dist():
    try:
        #Conecta-se à câmara UnitV2 via HTTP
        with cam_session.post(config.UNITV2_URL, data={}, stream=True, timeout=0.2) as r:
            if r.status_code != 200: return None
            buf = ""
            for chunk in r.iter_content(chunk_size=1):
                buf += chunk.decode('utf-8', errors='ignore')
                if '}' in buf:
                    try:
                        d = json.loads(buf[buf.rfind('{'):])
                        if 'r' in d and d['r'] > 2:
                            #Distância = Largura_Real * Distância_Focal / (2 * Raio_em_Pixels)
                            return (config.REAL_WIDTH_OBJ * config.FOCAL_LENGTH) / (d['r']*2)
                    except: pass
                    break
    except: pass
    return None

print("FCW (HC-SR04 + UnitV2) Iniciado.")

try:
    while True:
        now = time.time()
        
        #Leitura das distâncias
        dist_us = ultrasonic_driver.read_distance(config.TRIG_PIN, config.ECHO_PIN)
        dist_cam = get_camera_dist()
        
        final_dist = None
        source = "NONE"
        
        #Fusão dos sensores
        if dist_us and dist_us < config.FCW_SWITCH_DIST:    #Quando alcança a distância limite, usa só o ultrassom
            final_dist = dist_us
            source = "ULTRASOUND"
        elif dist_cam:
            final_dist = dist_cam
            source = "CAMERA"
        #Fallback se só o ultrassom estiver disponível
        elif dist_us:
            final_dist = dist_us
            source = "US_FALLBACK"
            
        #Cálculo e Envio
        if final_dist:
            history.append((now, final_dist))
            
            #Cálculo Velocidade e TTC
            vel = 0.0
            ttc = 999.0
            if len(history) >= 3:
                #Usa Regressão Linear para calcular a velocidade
                #X=tempo, Y=distância, se a disância diminuir, a linha desce
                times = np.array([t for t, d in history])
                dists = np.array([d for t, d in history])
                slope, _ = np.polyfit(times, dists, 1)  #O slope (devlive) é a velocidade
                vel = -slope
                if vel > 0.02: ttc = final_dist / vel
            
            #Payload para o FED
            packet = {
                "source": source,
                "dist": round(final_dist, 2),
                "ttc": round(ttc, 1),
                "type": "INFO"
            }
            
            #Lógica de Alerta
            if ttc < 1.0: packet["type"] = "ACCIDENT" #Vermelho no FED - Colisão Iminente
            elif ttc < 2.5: packet["type"] = "WARNING" #Amarelo no FED - Aviso de Aproximação
            
            #Publica para o FED via MQTT
            client.publish(config.TOPIC_FCW_DATA, json.dumps(packet))
            print(f"FCW: {source} | {final_dist:.2f}m | TTC: {ttc:.1f}s")
            
        time.sleep(0.1)

except KeyboardInterrupt:
    pass