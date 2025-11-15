#!/usr/bin/env python3
import sensor_handler
from smbus import SMBus
import time, paho.mqtt.client as mqtt
import json

# --- CONFIGURAÇÃO MQTT (ADAPTAR) ---
MQTT_BROKER = "192.168.1.183"  # **IP DO SEU PC WINDOWS**
MQTT_PORT = 1883
TOPIC_TELEMETRY = "motopi/telemetria/ahrs"
# ------------------------------------

# --- CONTROLE DE FREQUÊNCIA DE IMPRESSÃO ---
PUBLISH_FREQUENCY_HZ = 500
PRINT_FREQUENCY_HZ = 10 
PRINT_DIVIDER = PUBLISH_FREQUENCY_HZ // PRINT_FREQUENCY_HZ # 500 / 10 = 50
# --------------------------------------------

def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print(f"Raspberry Pi conectado ao Broker em {MQTT_BROKER}.")
    else:
        print(f"Falha na conexão, código de retorno: {rc}")

def main():
    # 1. Inicialização e Calibração
    bus = SMBus(1)
    # A linha abaixo assume que o file_handler não está mais em uso, mas ajusta a taxa de amostragem
    sensor_handler.init_mpu(bus, dlpf=1, rate_hz=PUBLISH_FREQUENCY_HZ) 
    acc_bias, gyr_bias = sensor_handler.calibrate(bus)

    # 2. Configuração MQTT
    client = mqtt.Client(client_id="MotoPi_RPi")
    client.on_connect = on_connect
    try:
        client.connect(MQTT_BROKER, MQTT_PORT, 60)
        client.loop_start() 
    except Exception as e:
        print(f"ERRO: Não foi possível conectar ao Broker MQTT: {e}.")
        return

    # 3. Loop Principal de Leitura e Publicação
    period = 1.0/PUBLISH_FREQUENCY_HZ
    next_t = time.perf_counter()
    print_counter = 0 
    
    print("-" * 50)
    print(f"A PUBLICAR dados AHRS em '{TOPIC_TELEMETRY}' a {PUBLISH_FREQUENCY_HZ} Hz...")
    print(f"A IMPRIMIR na consola a {PRINT_FREQUENCY_HZ} Hz.")
    print("-" * 50)

    try:
        while True:
            rows = sensor_handler.get_sensor_data(bus, acc_bias, gyr_bias)
            
            for sensor, ts, x, y, z in rows:
                if sensor == "AHRS":
                    
                    # 1. IMPRIMIR (Apenas a cada N leituras)
                    if print_counter % PRINT_DIVIDER == 0:
                        print(f"AHRS (ms: {ts}): Pitch={x:.2f}º | Roll={y:.2f}º | Yaw={z:.2f}º")
                    
                    # 2. PUBLICAR (A cada leitura)
                    data = {
                        "timestamp": ts, "pitch": round(x, 2),
                        "roll": round(y, 2), "yaw": round(z, 2)
                    }
                    client.publish(TOPIC_TELEMETRY, json.dumps(data), qos=0)
                    
                    print_counter += 1
                    break 
                
            # Agendamento
            next_t += period
            delay = next_t - time.perf_counter()
            if delay > 0:
                time.sleep(delay)
            else:
                next_t = time.perf_counter()
                
    except KeyboardInterrupt:
        print("\nPublicação interrompida pelo utilizador.")
    finally:
        client.loop_stop()
        client.disconnect()
        bus.close()

if __name__ == "__main__":
    main()
