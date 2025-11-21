# fcw_mobile.py - Termux (Android)
import paho.mqtt.client as mqtt
import subprocess
import sys
import os

# --- CONFIGURAÇÃO ---
# Ajusta o IP para o do teu PC Windows
MQTT_BROKER = "10.10.9.216" 
TOPIC_SUB_ALERTA = "fcw/alerta"

def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print("Display Conectado. Pronto.")
        client.subscribe(TOPIC_SUB_ALERTA)
    else:
        print("Erro conexão.")

def on_message(client, userdata, msg):
    alerta = msg.payload.decode()
    
    # Comando Termux para Notificação (com vibração e título)
    comando_notificacao = [
        'termux-notification', 
        '--title', '⚠️ ALERTA DE COLISÃO FCW', 
        '--content', alerta, 
        '--id', '999', # ID Fixo, importante para não sobrecarregar
        '--vibrate', '1000' # Vibra por 1000 milissegundos
    ]
    
    try:
        # Usa subprocess.run para executar o comando com segurança
        subprocess.run(comando_notificacao, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    except subprocess.CalledProcessError as e:
        print(f"ERRO ao executar termux-notification: {e.stderr.decode()}")
    except FileNotFoundError:
        print("ERRO: termux-notification não encontrado. Instale 'pkg install termux-api'")
        
    # Limpa o ecrã e imprime a confirmação para o utilizador Termux
    os.system('clear') 
    print("\n" * 2)
    print(" " + "🟥"*14)
    print(" 🟥   ALERTA ENVIADO   🟥")
    print(" " + "🟥"*14)
    print(f"\n  MOTIVO: {alerta}")
    print("\n" * 2)

client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1, "Mobile_Display")
client.on_connect = on_connect
client.on_message = on_message

try:
    print(f"A conectar a {MQTT_BROKER}...")
    client.connect(MQTT_BROKER, 1883, 60)
    client.loop_forever()
except KeyboardInterrupt:
    print("Sair.")
except Exception as e:
    print(f"Erro: {e}")