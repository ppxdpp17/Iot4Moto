#FICHEIRO DE CONFIGURAÇÃO GLOBAL DO FED

#---CONEXÃO ETHERNET (Privada)---
#IP do CSVU na interface de cabo (eth0, neste caso)
MQTT_BROKER_IP = "127.0.0.1" 
MQTT_PORT = 1883

#---TÓPICOS SUBSCRITOS---
TOPIC_SUB = "moto/#"    #O FED ouve tudo


#---Varíaveis para UI---
WINDOW_SIZE = "800x480"     #Tamanho comum de ecrãs auto
BG_COLOR = "#121212"      #Fundo escuro (Automotive dark mode)
TEXT_COLOR = "#FFFFFF"
ALERT_COLOR_RED = "#FF3333"
ALERT_COLOR_YELLOW = "#FFCC00"
ALERT_COLOR_GREEN = "#33FF33"