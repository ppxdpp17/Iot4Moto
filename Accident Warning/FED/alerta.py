#!/usr/bin/env python3
import paho.mqtt.client as mqtt
import subprocess
import os
import sys

# --- CONFIGURAÇÃO ---
BROKER = "172.20.0.131" # <-- CONFIRMA O IP DO TEU PC/RASPBERRY
PORT = 1883
TOPIC_ALERT = "motopi/alerta/emergencia"

def trigger_notification(msg):
    """Ativa vibração e notificação do Android via Termux-API"""
    try:
        # 1. Vibrar (1 segundo)
        subprocess.run(["termux-vibrate", "-d", "1000", "-f"])
        
        # 2. Notificação de Sistema (Push Notification)
        subprocess.run([
            "termux-notification",
            "--title", "🚨 ACIDENTE DETETADO",
            "--content", msg,
            "--priority", "high",
            "--id", "112",
            "--vibrate", "1000,1000,1000" # Padrão de vibração SOS
        ])
    except Exception as e:
        print(f"Erro ao chamar Termux API: {e}")

def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print("SISTEMA SOS CONECTADO.")
        print(f"A aguardar alertas em: {TOPIC_ALERT}")
        client.subscribe(TOPIC_ALERT)
    else:
        print(f"Erro conexão: {rc}")

def on_message(client, userdata, msg):
    alerta = msg.payload.decode()
    
    # 1. Interface Visual (Console)
    os.system('clear')
    print("\n" + "🟥"*20)
    print(" 🚑  EMERGÊNCIA  🚑")
    print("🟥"*20)
    print(f"\nMSG: {alerta}\n")
    print("A LIGAR AOS SERVIÇOS DE EMERGÊNCIA...") # Simulação
    print("🟥"*20 + "\n")

    # 2. Interface Física (Vibração/Notificação)
    trigger_notification(alerta)

# Setup MQTT
client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1, "Termux_SOS_Pager")
client.on_connect = on_connect
client.on_message = on_message

try:
    print(f"A conectar a {BROKER}...")
    client.connect(BROKER, PORT, 60)
    client.loop_forever()
except KeyboardInterrupt:
    print("Sair.")
except Exception as e:
    print(f"Erro fatal: {e}")