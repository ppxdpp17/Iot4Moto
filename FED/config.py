# C:\Users\Pedro\Projetos\AMOVER_FED\config.py

# --- CONEXÃO ETHERNET (Privada) ---
# IP do Raspberry Pi na interface de cabo (eth0)
MQTT_BROKER_IP = "192.168.50.1" 
MQTT_PORT = 1883

# --- TÓPICOS SUBSCRITOS ---
# O FED ouve tudo o que seja display ou alertas
TOPIC_SUB = "moto/#"

# --- CONFIGURAÇÃO VISUAL ---
WINDOW_SIZE = "800x480" # Tamanho comum de ecrãs auto
BG_COLOR = "#121212"    # Fundo escuro (Automotive dark mode)
TEXT_COLOR = "#FFFFFF"
ALERT_COLOR_RED = "#FF3333"
ALERT_COLOR_YELLOW = "#FFCC00"
ALERT_COLOR_GREEN = "#33FF33"