import paho.mqtt.client as mqtt
import time
import sys
import select
import os
import json

#---CONFIGURAÇÃO MQTT---
MQTT_BROKER = "172.20.0.130" #IP DO SERVIDOR - PC windows
MQTT_PORT = 1883
TOPIC_FINAL = "motopi/alerta/final" #O telemóvel ouve o servidor (não o Pi)
CALL_DURATION = 10

#Variáveis para controlar o estado do alerta
is_alerting = False
cancelled = False
start_signal = False
current_msg = ""

def handle_countdown():
    global is_alerting, cancelled, current_msg
    is_alerting = True
    cancelled = False
    
    #Print de alerta
    os.system('clear')
    print("\n" + "🟥"*30)
    print("!!! ALERTA CRÍTICO RECEBIDO !!!")
    print(f"Motivo: {current_msg}")
    print("-" * 30)
    print(f"A LIGAR PARA O 112 EM {CALL_DURATION} SEGUNDOS.")
    print("Prime [ENTER] para CANCELAR.")
    print("🟥"*30 + "\n")

    try:
        #Loop para contagem decrescente
        for i in range(CALL_DURATION, 0, -1):
            if cancelled: break
            rlist, _, _ = select.select([sys.stdin], [], [], 1.0)
            if rlist:
                sys.stdin.readline()
                cancelled = True
                break
            if not cancelled:
                print(f"->LIGAÇÃO EM: {i}s...     \r", end='', flush=True)
    except:
        cancelled = True

    print("\n" + "="*35)
    
    #Se o user cancelar (clica no ENTER), não simula a chamada
    if cancelled:
        print("!!!CHAMADA CANCELADA PELO UTILIZADOR!!!")
    else:
        #Caso contrário, simula a chamada
        print("!!!A LIGAR 112...!!! (Simulação)")
    print("="*35 + "\n")
    is_alerting = False

def on_connect(client, userdata, flags, rc):
    print("Conectado. À espera do Servidor...")
    client.subscribe(TOPIC_FINAL)

def on_message(client, userdata, msg):
    global is_alerting, start_signal, current_msg
    try:
        # Agora lemos JSON
        payload = json.loads(msg.payload.decode())
        # Extraímos a mensagem de texto e dados se existirem
        texto = payload.get("msg", "Alerta sem detalhes")
        svm = payload.get("svm", "N/A")
        
        if not is_alerting:
            current_msg = f"{texto} (Impacto: {svm}g)"
            start_signal = True
    except:
        print("Erro ao ler alerta JSON")

#Conexão MQTT
client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1, "Termux_Final")
client.on_connect = on_connect
client.on_message = on_message

try:
    client.connect(MQTT_BROKER, MQTT_PORT, 60)
    client.loop_start()
    
    while True:
        if start_signal and not is_alerting:
            start_signal = False
            handle_countdown()
        time.sleep(0.5)
        
except KeyboardInterrupt:
    client.loop_stop()
    client.disconnect()