#Código do raspberry que lê os dados do IMU e publica-os numa thread para serem lidos e processados pelo server (PC Windows)
import sensor_handler
from smbus import SMBus
import time, math
import json
import paho.mqtt.client as mqtt

# --- CONFIGURAÇÃO MQTT ---
MQTT_BROKER = "172.20.0.130"  # IP DO SEU PC (SERVIDOR)
MQTT_PORT = 1883
TOPIC_TELEMETRY = "motopi/telemetria/ahrs"
TOPIC_ALERTA = "motopi/alerta/emergencia" # Tópico direto de alerta

# --- CONFIGURAÇÃO DETEÇÃO DE ACIDENTE (SVM + ÂNGULO) ---
G = 9.81
SVM_THRESHOLD = 3.0 * G       # 3g (aprox 29.4 m/s²)
ANGLE_THRESHOLD = 60.0        # Graus de inclinação para confirmar queda
WINDOW_TIME = 5.0             # Janela de tempo pós-impacto (segundos)

# Frequências
PUBLISH_FREQUENCY_HZ = 100    # Reduzi para 100Hz para dar tempo ao CPU de calcular lógica
PRINT_FREQUENCY_HZ = 2
PRINT_DIVIDER = PUBLISH_FREQUENCY_HZ // PRINT_FREQUENCY_HZ

# --- VARIÁVEIS DE ESTADO ---
impact_detected = False       # Houve um pico de 3g?
impact_timestamp = 0          # Quando foi o impacto?
last_ax, last_ay, last_az = 0, 0, 0 # Guardar aceleração para cálculo

def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print(f"MotoPi conectado ao Broker {MQTT_BROKER}.")
    else:
        print(f"Falha na conexão MQTT: {rc}")

def main():
    # 1. Inicialização
    bus = SMBus(1)
    sensor_handler.init_mpu(bus, dlpf=1, rate_hz=PUBLISH_FREQUENCY_HZ) 
    acc_bias, gyr_bias = sensor_handler.calibrate(bus)

    # 2. Configuração MQTT
    # FIX: Adicionado CallbackAPIVersion para compatibilidade
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1, "MotoPi_RPi")
    client.on_connect = on_connect
    
    try:
        client.connect(MQTT_BROKER, MQTT_PORT, 60)
        client.loop_start() 
    except Exception as e:
        print(f"ERRO MQTT: {e}")
        return

    # 3. Loop Principal
    period = 1.0/PUBLISH_FREQUENCY_HZ
    next_t = time.perf_counter()
    print_counter = 0
    
    # Variáveis globais de lógica
    global impact_detected, impact_timestamp, last_ax, last_ay, last_az

    print("-" * 50)
    print(f"SISTEMA DE DETEÇÃO DE ACIDENTE ATIVO")
    print(f"Gatilho de Impacto: > {SVM_THRESHOLD:.1f} m/s² (3g)")
    print(f"Confirmação de Queda: > {ANGLE_THRESHOLD}º em {WINDOW_TIME}s")
    print("-" * 50)

    try:
        while True:
            # Obtém dados (Aceleração Bruta e AHRS Processado)
            rows = sensor_handler.get_sensor_data(bus, acc_bias, gyr_bias)
            
            # Variáveis temporárias para este ciclo
            curr_pitch, curr_roll = 0.0, 0.0
            
            for sensor, ts, x, y, z in rows:
                
                # --- A. CAPTURAR ACELERAÇÃO BRUTA (Para o SVM) ---
                if sensor == "ACEL":
                    last_ax, last_ay, last_az = x, y, z
                    
                    # 1. Calcular Magnitude do Vetor de Aceleração (SVM)
                    svm = math.sqrt(x**2 + y**2 + z**2)
                    
                    # 2. Detetar o Pico de Impacto (> 3g)
                    if svm > SVM_THRESHOLD and not impact_detected:
                        impact_detected = True
                        impact_timestamp = time.time()
                        print(f"\n💥 IMPACTO DETETADO! SVM: {svm:.1f} m/s². À espera de inclinação...")

                # --- B. CAPTURAR ÂNGULOS (Para confirmar a queda) ---
                elif sensor == "AHRS":
                    curr_pitch, curr_roll, curr_yaw = x, y, z
                    
                    # 3. Lógica Pós-Impacto
                    if impact_detected:
                        elapsed = time.time() - impact_timestamp
                        
                        # Se ainda estamos dentro dos 5 segundos...
                        if elapsed < WINDOW_TIME:
                            # Verifica se a mota está deitada (> 60º)
                            if abs(curr_roll) > ANGLE_THRESHOLD or abs(curr_pitch) > ANGLE_THRESHOLD:
                                
                                # === ACIDENTE CONFIRMADO ===
                                msg_alerta = (f"SOS: ACIDENTE DETETADO! "
                                              f"Impacto {SVM_THRESHOLD/G:.1f}g seguido de inclinação {curr_roll:.1f}º.")
                                
                                print("\n" + "🟥"*30)
                                print(msg_alerta)
                                print("🟥"*30 + "\n")
                                
                                # Envia Alerta IMEDIATO para o telemóvel
                                client.publish(TOPIC_ALERTA, msg_alerta, qos=2)
                                
                                # Reset para evitar spam de alertas
                                impact_detected = False
                                time.sleep(5) # Pausa para não reenviar logo
                                
                        else:
                            # Passaram 5 segundos e a mota não caiu?
                            # Foi só um buraco ou lomba. Reset.
                            print("Tempo esgotado. Falso alarme (Mota manteve-se estável).")
                            impact_detected = False

                    # 4. Publicar Telemetria Normal e Logs
                    if print_counter % PRINT_DIVIDER == 0:
                        status = "ALERTA" if impact_detected else "OK"
                        print(f"Status: {status} | Roll: {curr_roll:.1f}º | SVM: {math.sqrt(last_ax**2+last_ay**2+last_az**2):.1f}")

                    # Envia dados normais para o servidor (para gráficos, etc)
                    data = {
                        "ts": ts,
                        "roll": round(curr_roll, 2),
                        "pitch": round(curr_pitch, 2),
                        "svm": round(math.sqrt(last_ax**2+last_ay**2+last_az**2), 2)
                    }
                    client.publish(TOPIC_TELEMETRY, json.dumps(data), qos=0)
                    
                    print_counter += 1
                    break 
                
            # Agendamento preciso
            next_t += period
            delay = next_t - time.perf_counter()
            if delay > 0:
                time.sleep(delay)
            else:
                next_t = time.perf_counter()
                
    except KeyboardInterrupt:
        print("\nSistema encerrado.")
    finally:
        client.loop_stop()
        client.disconnect()
        bus.close()

if __name__ == "__main__":
    main()
