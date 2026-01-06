#FICHEIRO DE CONFIGURAÇÃO GLOBAL DA APLICAÇÃO MÓVEL

#---CONEXÃO CLOUD (Simulação 4G/5G)---
#Usa um Broker Público (hiveMQ) para garantir conectividade global
MQTT_BROKER = "broker.hivemq.com" 
MQTT_PORT = 1883

#---TÓPICOS MQTT---
#Ouve alertas vindos do LCM (Cloud)
TOPIC_SUB_ALERTS = "pedro_thesis/moto/app"

#Envia comandos para a Mota (Armar/Desarmar)
TOPIC_PUB_CMD = "pedro_thesis/moto/cmd/security"

#---VÍDEO (ACESSO REMOTO HÍBRIDO)---
#Link Local (Rápido, Wi-Fi de casa)
VIDEO_URL_LOCAL = "http://192.168.1.217:5000/video?key=!ppxdpp123!"

#Link Ngrok (Lento, 4G/Rua) - ATUALIZAR CADA VEZ QUE INICIA O NGROK (usar o link dado pelo comando "ngrok http 5000" no terminal do raspberry)
VIDEO_URL_GLOBAL = "https://xxxx-xxxx.ngrok-free.app/video?key=!ppxdpp123!"