# alerter.py (No seu Telemóvel/Termux)
import paho.mqtt.client as mqtt

# --- CONFIGURAÇÃO MQTT (ADAPTAR ESTE BLOCO) ---
MQTT_BROKER = "192.168.1.183"  # **ALTERE PARA O IP DO SEU PC WINDOWS**
MQTT_PORT = 1883
TOPIC_ALERTA = "motopi/alerta/emergencia"
# ------------------------------------------------

# Função que corre quando a conexão é estabelecida
def on_connect(client, userdata, flags, rc):
    print("Conectado ao Broker MQTT.")
    client.subscribe(TOPIC_ALERTA)
    print(f"A escutar o tópico de alerta: {TOPIC_ALERTA}")

# Função que corre quando recebe uma mensagem
def on_message(client, userdata, msg):
    alerta = msg.payload.decode()
    print("\n" + "="*50)
    print("!!! MENSAGEM DE ALERTA RECEBIDA !!!")
    print(f"CONTEÚDO: {alerta}")
    print("="*50 + "\n")

# Main
client = mqtt.Client(client_id="Termux_Alerter")
client.on_connect = on_connect
client.on_message = on_message

print(f"A tentar conectar em {MQTT_BROKER}:{MQTT_PORT}...")
client.connect(MQTT_BROKER, MQTT_PORT, 60)

# Loop infinito
client.loop_forever()