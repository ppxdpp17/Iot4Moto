# ~/amover_uvma/config.py

# --- CONEXÃO CLOUD (Simulação 4G/5G) ---
# Usamos um Broker Público para garantir conectividade global
MQTT_BROKER = "broker.hivemq.com" 
MQTT_PORT = 1883

# --- TÓPICOS MQTT ---
# Ouvir alertas vindos do LCM (Cloud)
TOPIC_SUB_ALERTS = "pedro_thesis/moto/app"

# Enviar comandos para a Mota (Armar/Desarmar)
TOPIC_PUB_CMD = "pedro_thesis/moto/cmd/security"

# --- VÍDEO (ACESSO REMOTO) ---
# Se estiveres a testar em casa (mesmo Wi-Fi): "http://192.168.1.XXX:5000/video"
# Se estiveres a usar Ngrok (Tese): "https://xxxx.ngrok.io/video"
# Substitui pelo IP do Raspberry Pi na rede Wi-Fi!
VIDEO_STREAM_URL = "http://192.168.1.217:5000/video"