import paho.mqtt.client as mqtt
import json
import csv
import time
import threading
from pathlib import Path

#---CONFIGURAÇÃO MQTT---
MQTT_BROKER = "127.0.0.1" #Localhost
MQTT_PORT = 1883

#Tópicos
TOPIC_TELEMETRY = "motopi/telemetria"   #Regista dados do sensor continuamente
TOPIC_EVENT = "motopi/evento"       #Eventos importantes (impacto, queda, cancelar)
TOPIC_FINAL_ALERT = "motopi/alerta/final" #O que vai para o telemóvel

#Logging em CSV
DATA_DIR = Path.cwd() / "logs_acidentes"
DATA_DIR.mkdir(exist_ok=True)
LOG_FILE = DATA_DIR / f"log_sistema_{int(time.time())}.csv"

#Variáveis para controlar eventos
impact_timer = None
SECURITY_TIMER = 15.0 #Se não houver notícias em 15s após impacto -> ERRO CRÍTICO

#Setup CSV
with LOG_FILE.open("w", newline="", encoding="utf-8") as f:
    csv.writer(f).writerow(["Timestamp", "Tipo", "Roll", "Pitch", "SVM", "Msg/Detalhes"])

print(f"--- SERVIDOR GUARDIÃO ATIVO ---")

#---LÓGICA DO TEMPORIZADOR DE SEGURANÇA---
def security_timeout_trigger():
    "-É chamado se o temporizador expirar (Mota deixou de responder)-"
    print("\nSegurança Recursiva: TEMPO ESGOTADO! A Mota não reportou estado após impacto.")
    print("DESTRUIÇÃO/PERDA DE SINAL ASSUMIDA. A ENVIAR ALERTA CRÍTICO.\n")
    
    payload = {
        "msg": "ALERTA CRÍTICO: Perda de sinal após impacto (Provável destruição).",
        "svm": "N/A",
        "roll": "N/A"
    }

    #Publica no canal para o telemóvel
    client.publish(TOPIC_FINAL_ALERT, json.dumps(payload), qos=2)
    log_event("ALERTA_TIMEOUT", 0, 0, 0, "Timeout da Segurança Recursiva - Mota Off")

#Função para iniciar o sistema de segurança recursiva
def start_SR():
    global impact_timer
    if impact_timer and impact_timer.is_alive():
        impact_timer.cancel()
    
    print("->IMPACTO DETETADO. A iniciar o temporizador de segurança (15s)...")
    impact_timer = threading.Timer(SECURITY_TIMER, security_timeout_trigger)
    impact_timer.start()

#Função para parar o sistema de segurança recursiva
def stop_SR():
    global impact_timer
    if impact_timer:
        impact_timer.cancel()
        print("Temporizador cancelado (Situação resolvida).")

#---MQTT HANDLERS---
def log_event(tipo, roll, pitch, svm, msg):
    try:
        #Escreve no CSV
        with LOG_FILE.open("a", newline="", encoding="utf-8") as f:
            writer = csv.writer(f)
            writer.writerow([time.strftime('%H:%M:%S'), tipo, roll, pitch, svm, msg])
    except: pass

def on_message(client, userdata, msg):
    try:
        payload = json.loads(msg.payload.decode())
        
        if msg.topic == TOPIC_TELEMETRY:
            #Apenas grava, não decide nada
            log_event("TELEMETRIA", payload.get('roll'), payload.get('pitch'), payload.get('svm'), "")
            
        elif msg.topic == TOPIC_EVENT:
            tipo = payload.get("tipo")
            svm = payload.get("svm", 0)
            
            print(f"EVENTO RECEBIDO: {tipo}")
            log_event(tipo, payload.get('roll'), payload.get('pitch'), svm, payload.get('msg', ''))

            #---MÁQUINA DE ESTADOS DO SISTEMA DE SEGURANÇA RECURSIVA---
            if tipo == "IMPACTO":
                start_SR()
                
            elif tipo == "QUEDA_CONFIRMADA":
                stop_SR() #Porque já sabemos o que aconteceu
                print("🚨 ENCAMINHANDO ALERTA DE QUEDA PARA O TELEMÓVEL...")
                #Passa info para o telemóvel
                client.publish(TOPIC_FINAL_ALERT, json.dumps(payload), qos=2)
                
            elif tipo == "CANCELAR":
                stop_SR() #Foi falso alarme, cancelar timer
                
    except Exception as e:
        print(f"Erro: {e}")

#Configs MQTT
client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1, "Server_Guardian")
client.on_connect = lambda c,u,f,rc: c.subscribe([(TOPIC_TELEMETRY,0), (TOPIC_EVENT, 2)])
client.on_message = on_message

client.connect(MQTT_BROKER, MQTT_PORT, 60)
client.loop_forever()