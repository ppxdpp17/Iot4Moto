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

#---VÍDEO (ACESSO REMOTO)---
#Casa (mesmo Wi-Fi): "http://192.168.1.XXX:5000/video"
NGROK = "https://37tN1Gt7z5Y1y18QVZLcJ2XIODr_7td813kXMFrjdnM1kfQiw.app/video?key=!ppxdpp123!"
VIDEO_STREAM_URL = "http://192.168.1.217:5000/video"    #IP da CSVU