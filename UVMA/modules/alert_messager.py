# ~/amover_uvma/modules/alert_manager.py
import paho.mqtt.client as mqtt
import json
import subprocess
import sys
import os

# Adiciona a pasta pai ao path para importar config
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import config

def trigger_android_notification(title, body, critical=False):
    """Usa a API do Termux para interagir com o Android"""
    try:
        # 1. Vibração
        duration = "2000" if critical else "500" # 2s se for crítico
        subprocess.run(["termux-vibrate", "-d", duration, "-f"], check=False)
        
        # 2. Notificação Visual (Barra de Notificações)
        priority = "high" if critical else "default"
        subprocess.run([
            "termux-notification",
            "--title", title,
            "--content", body,
            "--priority", priority,
            "--id", "amover_alert",
            "--action", f"termux-open-url {config.VIDEO_STREAM_URL}" # Botão para abrir vídeo
        ], check=False)
        
        # 3. TTS (Text-to-Speech) - Opcional, fala o alerta
        if critical:
            subprocess.run(["termux-tts-speak", body], check=False)
            
    except Exception as e: 
        print(f"Erro Termux API: {e}")

def on_message(client, userdata, msg):
    try:
        payload = json.loads(msg.payload.decode())
        alert_type = payload.get("alert") # CRITICAL ou SECURITY
        msg_text = payload.get("msg", "Alerta desconhecido")
        
        # Desenha alerta no terminal (sobrepõe o menu)
        if alert_type == "CRITICAL": # Acidente
            print("\n" + "🟥"*30)
            print(f" 🚨 EMERGÊNCIA: {msg_text}")
            print("🟥"*30 + "\n")
            trigger_android_notification("⚠️ ACIDENTE MOTO", msg_text, critical=True)
            
        elif alert_type == "SECURITY": # Roubo
            print("\n" + "🟨"*30)
            print(f" 🕵️ SEGURANÇA: {msg_text}")
            print("🟨"*30 + "\n")
            trigger_android_notification("ALERTA ROUBO", msg_text, critical=False)
            
    except Exception as e:
        print(f"Erro ao processar alerta: {e}")

def start_listening():
    """Inicia a thread MQTT"""
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1, "UVMA_Background_Service")
    try:
        client.connect(config.MQTT_BROKER, config.MQTT_PORT, 60)
        client.subscribe(config.TOPIC_SUB_ALERTS)
        client.on_message = on_message
        client.loop_start() # Corre numa thread separada
        return client
    except Exception as e:
        print(f"Erro de conexão Internet: {e}")
        return None