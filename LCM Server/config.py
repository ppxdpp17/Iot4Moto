#FICHEIRO DE CONFIGURAÇÃO GLOBAL DO LCM

#---CONEXÃO LOCAL (LCM <-> Broker Windows)---
LOCAL_HOST = "127.0.0.1" 
LOCAL_PORT = 1883

#---CONEXÃO PÚBLICA (LCM <-> App Móvel)---
#Usa o hiveMQ público para garantir conectividade via 4G/Internet
PUBLIC_BROKER = "broker.hivemq.com"
PUBLIC_PORT = 1883

#---Tópico MQTT---
TOPIC_PREFIX = "pedro_thesis/moto"

#Tópicos de dados (Mota -> LCM -> App)
TOPIC_MOTO_IN = f"{TOPIC_PREFIX}/#" #O LCM subscreve tudo o que vem da mota          
TOPIC_APP_OUT = f"{TOPIC_PREFIX}/app" #O LCM reencaminha alertas críticos para a App

#Tópicos de comandos (App -> LCM -> Mota)
TOPIC_APP_CMD_IN = f"{TOPIC_PREFIX}/cmd/#"  #O LCM ouve comandos da App
TOPIC_MOTO_CMD_OUT = f"{TOPIC_PREFIX}/cmd/security" #O LCM envia o comando específico de segurança para a Mota