# C:\Users\Pedro\Desktop\testest\FINAL\LCM SERVER\config.py

# --- CONEXÃO LOCAL (LCM <-> Broker Windows) ---
# O script conecta-se ao Mosquitto que está a correr no próprio PC
LOCAL_HOST = "127.0.0.1" 
LOCAL_PORT = 1883

# --- CONEXÃO PÚBLICA (LCM <-> App Móvel) ---
# Simula a Internet real para falar com o smartphone em 4G
PUBLIC_BROKER = "broker.hivemq.com"
PUBLIC_PORT = 1883

# --- API DE TÓPICOS (A-MOVER PROTOCOL) ---
# Prefixo Geral
TOPIC_PREFIX = "pedro_thesis/moto"

# Tópicos de DADOS (Sentido: Mota -> LCM -> App)
# O LCM subscreve tudo o que vem da mota
TOPIC_MOTO_IN = f"{TOPIC_PREFIX}/#"          
# O LCM reencaminha alertas críticos para a App
TOPIC_APP_OUT = f"{TOPIC_PREFIX}/app" 

# Tópicos de COMANDOS (Sentido: App -> LCM -> Mota)
# O LCM ouve comandos da App
TOPIC_APP_CMD_IN = f"{TOPIC_PREFIX}/cmd/#"
# O LCM envia o comando específico de segurança para a Mota
TOPIC_MOTO_CMD_OUT = f"{TOPIC_PREFIX}/cmd/security"