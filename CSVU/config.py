# /home/pi/amover_project/config.py

# --- REDE ETHERNET (Privada - Cabo CSVU <-> FED) ---
MQTT_BROKER_LOCAL = "127.0.0.1"  # O Broker corre no próprio Pi
MQTT_PORT = 1883

# --- REDE CLOUD/WI-FI (Pública - CSVU <-> App) ---
# Usa um Broker Público para garantir que funciona via 4G/Internet real
# Ou usa o IP do teu PC se preferires local: "192.168.1.100"
MQTT_BROKER_CLOUD = "broker.hivemq.com" 

# --- TÓPICOS MQTT (API A-Mover) ---
# Prefixos
TOPIC_HAL_PREFIX = "moto/hardware"
TOPIC_ALERT_PREFIX = "moto/alerts"

# Tópicos Específicos
TOPIC_ALERT_ACCIDENT = "pedro_thesis/moto/alerts/accident"
TOPIC_ALERT_THEFT = "pedro_thesis/moto/security/alert"
TOPIC_CMD_SECURITY = "pedro_thesis/moto/cmd/security"
TOPIC_FCW_DATA = "moto/fcw/data" # Para o FED (Local)

# --- HARDWARE ---
# Pinos Ultrassom (BCM)
TRIG_PIN = 23
ECHO_PIN = 24

# Câmara UnitV2
UNITV2_URL = "http://10.254.239.1/func/result"
UNITV2_STREAM = "http://10.254.239.1/video_feed"

# Calibração Câmara (Ajustar com os teus valores!)
REAL_WIDTH_OBJ = 0.035 # 3.5cm
FOCAL_LENGTH = 457.0   # O valor que calculaste (r=8 -> F=457)

# --- FÍSICA E LIMIARES ---
G_FORCE = 9.81
SVM_THRESHOLD = 3.0 * G_FORCE # Impacto
ANGLE_THRESHOLD = 60.0        # Queda
THEFT_SENSITIVITY = 0.15      # Vibração (g)
FCW_SWITCH_DIST = 0.40        # Metros (Troca Cam -> US)