import paho.mqtt.client as mqtt
import json
import csv
import time
import os
from pathlib import Path

# --- CONFIGURAÇÃO ---
MQTT_BROKER = "127.0.0.1"
MQTT_PORT = 1883
CLIENT_ID = "Server_IMU_BlackBox"

# Tópicos do IMU
TOPIC_TELEMETRY = "motopi/telemetria/ahrs"
TOPIC_ALERT = "motopi/alerta/emergencia"

# Configuração de Log
DATA_DIR = Path("logs_acidentes")
DATA_DIR.mkdir(exist_ok=True)
LOG_FILE = DATA_DIR / f"blackbox_imu_{int(time.time())}.csv"

# Inicializar ficheiro CSV
if not LOG_FILE.exists():
    with LOG_FILE.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["Timestamp", "Tópico", "Roll", "Pitch", "SVM (G-Force)", "Mensagem"])

print(f"--- SERVIDOR CAIXA NEGRA IMU ---")
print(f"A gravar dados em: {LOG_FILE}")

def on_connect(client, userdata, flags, rc):
    print("Conectado ao Broker. A escutar IMU...")
    client.subscribe([(TOPIC_TELEMETRY, 0), (TOPIC_ALERT, 2)])

def on_message(client, userdata, msg):
    try:
        topic = msg.topic
        payload_str = msg.payload.decode()
        current_time = time.strftime('%H:%M:%S', time.localtime())
        
        # Dados para gravar
        row = [current_time, topic, "", "", "", payload_str]

        if topic == TOPIC_TELEMETRY:
            # Desmontar o JSON da telemetria para colunas bonitas
            data = json.loads(payload_str)
            row[2] = data.get('roll', 0)
            row[3] = data.get('pitch', 0)
            row[4] = data.get('svm', 0)
            # Não imprimimos telemetria na consola para não poluir, só gravamos
            
        elif topic == TOPIC_ALERT:
            # ALERTA CRÍTICO
            print("\n" + "!"*40)
            print(f"🚨 ALERTA RECEBIDO NO SERVIDOR: {payload_str}")
            print("!"*40 + "\n")

        # Gravar no CSV
        with LOG_FILE.open("a", newline="", encoding="utf-8") as f:
            writer = csv.writer(f)
            writer.writerow(row)

    except Exception as e:
        print(f"Erro ao processar: {e}")

# Setup MQTT
client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1, CLIENT_ID)
client.on_connect = on_connect
client.on_message = on_message

try:
    client.connect(MQTT_BROKER, MQTT_PORT, 60)
    client.loop_forever()
except KeyboardInterrupt:
    print("\nServidor Encerrado.")