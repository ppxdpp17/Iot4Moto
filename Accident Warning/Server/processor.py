import paho.mqtt.client as mqtt
import json
import csv
import time
from pathlib import Path

# --- CONFIGURAÇÃO ---
MQTT_BROKER = "127.0.0.1"
MQTT_PORT = 1883
CLIENT_ID = "Server_IMU_BlackBox"

# Tópicos do IMU
TOPIC_TELEMETRY = "motopi/telemetria/ahrs"
TOPIC_ALERT = "motopi/alerta/emergencia"

# Configuração de Log
DATA_DIR = Path.cwd() / "Accident Warning/Server/logs_acidentes" # Cria a pasta no diretório atual
DATA_DIR.mkdir(exist_ok=True)
LOG_FILE = DATA_DIR / f"blackbox_imu_{int(time.time())}.csv"

# Variável de estado para garantir que o cabeçalho é escrito
header_written = False

print(f"--- SERVIDOR CAIXA NEGRA IMU ---")
print(f"A gravar dados em: {LOG_FILE.resolve()}")

def write_header_if_needed():
    """Garante que o cabeçalho do CSV é escrito apenas uma vez."""
    global header_written
    if not header_written:
        with LOG_FILE.open("w", newline="", encoding="utf-8") as f:
            writer = csv.writer(f)
            writer.writerow(["Timestamp", "Tópico", "Roll", "Pitch", "SVM (G-Force)", "Mensagem"])
        header_written = True

def on_connect(client, userdata, flags, rc):
    print("Conectado ao Broker. A escutar IMU...")
    write_header_if_needed() # Escreve o cabeçalho assim que se conecta
    client.subscribe([(TOPIC_TELEMETRY, 0), (TOPIC_ALERT, 2)])

def on_message(client, userdata, msg):
    try:
        topic = msg.topic
        payload_str = msg.payload.decode()
        current_time = time.time() # Timestamp de alta precisão
        
        # Linha base para o CSV
        row = [current_time, topic, "", "", "", payload_str]

        if topic == TOPIC_TELEMETRY:
            # Desmontar o JSON da telemetria
            data = json.loads(payload_str)
            row[2] = data.get('roll', 0)
            row[3] = data.get('pitch', 0)
            # O SVM é um valor crítico e deve ser bem formatado
            row[4] = f"{data.get('svm', 0):.2f}" 
            
        elif topic == TOPIC_ALERT:
            # ALERTA CRÍTICO
            print("\n" + "!"*40)
            print(f"🚨 ALERTA RECEBIDO NO SERVIDOR: {payload_str}")
            print("!"*40 + "\n")

        # Gravar no CSV (modo 'a' para append)
        with LOG_FILE.open("a", newline="", encoding="utf-8") as f:
            writer = csv.writer(f)
            writer.writerow(row)

    except Exception as e:
        print(f"Erro ao processar mensagem: {e}")

# Setup MQTT
client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1, CLIENT_ID)
client.on_connect = on_connect
client.on_message = on_message

try:
    client.connect(MQTT_BROKER, MQTT_PORT, 60)
    client.loop_forever()
except KeyboardInterrupt:
    print("\nServidor Encerrado.")