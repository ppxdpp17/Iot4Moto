import paho.mqtt.client as mqtt
import json
import csv
import time
import threading
from pathlib import Path

# --- CONFIGURAÇÃO ---
MQTT_BROKER = "127.0.0.1"
MQTT_PORT = 1883

# Tópicos
TOPIC_TELEMETRY = "motopi/telemetria"
TOPIC_EVENT = "motopi/evento"
TOPIC_FINAL_ALERT = "motopi/alerta/final" # O que vai para o telemóvel

# Log
DATA_DIR = Path.cwd() / "logs_acidentes"
DATA_DIR.mkdir(exist_ok=True)
LOG_FILE = DATA_DIR / f"log_sistema_{int(time.time())}.csv"

# Variáveis de Lógica Guardião
impact_timer = None
GUARDIAN_TIMEOUT = 15.0 # Se não houver notícias em 15s após impacto -> ERRO CRÍTICO

# Setup CSV
with LOG_FILE.open("w", newline="", encoding="utf-8") as f:
    csv.writer(f).writerow(["Timestamp", "Tipo", "Roll", "Pitch", "SVM", "Msg/Detalhes"])

print(f"--- SERVIDOR GUARDIÃO ATIVO ---")

# --- LÓGICA DE TEMPORIZADOR ---
def guardian_timeout_trigger():
    """Chamado se o temporizador expirar (Mota deixou de responder)"""
    print("\n🚨 GUARDIÃO: TEMPO ESGOTADO! A Mota não reportou estado após impacto.")
    print("🚨 ASSUMINDO DESTRUIÇÃO/PERDA DE SINAL. ENVIANDO ALERTA CRÍTICO.\n")
    
    payload = {
        "msg": "ALERTA CRÍTICO: Perda de sinal após impacto (Provável destruição).",
        "svm": "N/A",
        "roll": "N/A"
    }
    # Publica para o telemóvel
    client.publish(TOPIC_FINAL_ALERT, json.dumps(payload), qos=2)
    log_event("ALERTA_TIMEOUT", 0, 0, 0, "Timeout do Guardião - Mota Off")

def start_guardian():
    global impact_timer
    if impact_timer and impact_timer.is_alive():
        impact_timer.cancel()
    
    print("⏳ IMPACTO DETETADO. Iniciando temporizador de segurança (15s)...")
    impact_timer = threading.Timer(GUARDIAN_TIMEOUT, guardian_timeout_trigger)
    impact_timer.start()

def stop_guardian():
    global impact_timer
    if impact_timer:
        impact_timer.cancel()
        print("✅ Temporizador cancelado (Situação resolvida).")

# --- MQTT HANDLERS ---
def log_event(tipo, roll, pitch, svm, msg):
    try:
        with LOG_FILE.open("a", newline="", encoding="utf-8") as f:
            writer = csv.writer(f)
            writer.writerow([time.strftime('%H:%M:%S'), tipo, roll, pitch, svm, msg])
    except: pass

def on_message(client, userdata, msg):
    try:
        payload = json.loads(msg.payload.decode())
        
        if msg.topic == TOPIC_TELEMETRY:
            # Apenas grava, não decide nada
            log_event("TELEMETRIA", payload.get('roll'), payload.get('pitch'), payload.get('svm'), "")
            
        elif msg.topic == TOPIC_EVENT:
            tipo = payload.get("tipo")
            svm = payload.get("svm", 0)
            
            print(f"EVENTO RECEBIDO: {tipo}")
            log_event(tipo, payload.get('roll'), payload.get('pitch'), svm, payload.get('msg', ''))

            # --- MÁQUINA DE ESTADOS DO GUARDIÃO ---
            if tipo == "IMPACTO":
                start_guardian()
                
            elif tipo == "QUEDA_CONFIRMADA":
                stop_guardian() # Já sabemos o que aconteceu
                print("🚨 ENCAMINHANDO ALERTA DE QUEDA PARA O TELEMÓVEL...")
                # Repassa para o telemóvel
                client.publish(TOPIC_FINAL_ALERT, json.dumps(payload), qos=2)
                
            elif tipo == "CANCELAR":
                stop_guardian() # Foi falso alarme, cancelar timer
                
    except Exception as e:
        print(f"Erro: {e}")

client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1, "Server_Guardian")
client.on_connect = lambda c,u,f,rc: c.subscribe([(TOPIC_TELEMETRY,0), (TOPIC_EVENT, 2)])
client.on_message = on_message

client.connect(MQTT_BROKER, MQTT_PORT, 60)
client.loop_forever()