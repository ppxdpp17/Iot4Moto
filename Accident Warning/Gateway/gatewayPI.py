import sensor_handler
from smbus import SMBus
import time, math
import json
import paho.mqtt.client as mqtt

# --- CONFIGURAÇÃO ---
MQTT_BROKER = "172.20.0.130" 
MQTT_PORT = 1883

# Tópicos
TOPIC_TELEMETRY = "motopi/telemetria" # Dados contínuos
TOPIC_EVENT = "motopi/evento"         # Eventos de lógica (Impacto, Queda, Cancelar)

# Parâmetros
G = 9.81
SVM_THRESHOLD = 3.0 * G 
ANGLE_THRESHOLD = 60.0
WINDOW_TIME = 5.0

PUBLISH_FREQUENCY_HZ = 50 # 50Hz é suficiente e mais leve
acc_bias, gyr_bias = None, None

# Estado
impact_detected = False
impact_timestamp = 0
last_svm = 0

def on_connect(client, userdata, flags, rc):
    print(f"Conectado ao Broker (rc:{rc})")

def main():
    global impact_detected, impact_timestamp, last_svm
    
    bus = SMBus(1)
    sensor_handler.init_mpu(bus, dlpf=1, rate_hz=PUBLISH_FREQUENCY_HZ)
    acc_bias, gyr_bias = sensor_handler.calibrate(bus)
    
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1, "MotoPi_Sensor")
    client.on_connect = on_connect
    
    try:
        client.connect(MQTT_BROKER, MQTT_PORT, 60)
        client.loop_start()
    except Exception as e:
        print(f"Erro MQTT: {e}")
        return

    period = 1.0/PUBLISH_FREQUENCY_HZ
    next_t = time.perf_counter()
    
    print(f"--- MONITORIZAÇÃO ATIVA (Impacto > {SVM_THRESHOLD/G:.1f}g) ---")

    try:
        while True:
            rows = sensor_handler.get_sensor_data(bus, acc_bias, gyr_bias)
            
            for sensor, ts, x, y, z in rows:
                if sensor == "ACEL":
                    last_svm = math.sqrt(x**2 + y**2 + z**2)
                    
                    # 1. DETEÇÃO DE IMPACTO (Gatilho Inicial)
                    if last_svm > SVM_THRESHOLD and not impact_detected:
                        impact_detected = True
                        impact_timestamp = time.time()
                        print(f"💥 IMPACTO ({last_svm/G:.1f}g). A avisar servidor...")
                        
                        # Envia aviso IMEDIATO para o servidor (Caso a mota morra a seguir)
                        payload = {
                            "tipo": "IMPACTO",
                            "svm": round(last_svm, 2),
                            "ts": ts
                        }
                        client.publish(TOPIC_EVENT, json.dumps(payload), qos=1)

                elif sensor == "AHRS":
                    pitch, roll, yaw = x, y, z
                    
                    # 2. LÓGICA PÓS-IMPACTO (Confirmação de Queda)
                    if impact_detected:
                        elapsed = time.time() - impact_timestamp
                        
                        if elapsed < WINDOW_TIME:
                            if abs(roll) > ANGLE_THRESHOLD or abs(pitch) > ANGLE_THRESHOLD:
                                print("⤵️ QUEDA CONFIRMADA.")
                                payload = {
                                    "tipo": "QUEDA_CONFIRMADA",
                                    "svm": round(last_svm, 2),
                                    "roll": round(roll, 2),
                                    "pitch": round(pitch, 2),
                                    "msg": "Queda detetada após impacto."
                                }
                                client.publish(TOPIC_EVENT, json.dumps(payload), qos=2)
                                
                                impact_detected = False
                                time.sleep(5) # Debounce
                        else:
                            # Tempo esgotou sem queda -> Falso Alarme (Buraco)
                            print("✅ Falso alarme (Tempo esgotado).")
                            payload = {"tipo": "CANCELAR", "msg": "Falso alarme (estável)."}
                            client.publish(TOPIC_EVENT, json.dumps(payload), qos=1)
                            impact_detected = False

                    # 3. TELEMETRIA (Para Blackbox)
                    # Envia apenas 1 em cada 10 frames para não entupir, ou todos se preferires
                    if int(time.time()*100) % 10 == 0: 
                        data = {
                            "ts": ts,
                            "roll": round(roll, 2),
                            "pitch": round(pitch, 2),
                            "svm": round(last_svm, 2)
                        }
                        client.publish(TOPIC_TELEMETRY, json.dumps(data), qos=0)

            # Timing preciso
            next_t += period
            delay = next_t - time.perf_counter()
            if delay > 0: time.sleep(delay)
            else: next_t = time.perf_counter()

    except KeyboardInterrupt:
        print("Sair.")
    finally:
        client.loop_stop()
        bus.close()

if __name__ == "__main__":
    main()
