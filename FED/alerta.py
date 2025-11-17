#alerta.py (Códogo que corre no Telemóvel/Termux com o intuito de ser o FED)
import paho.mqtt.client as mqtt
import time
import sys
import select
import threading

#---CONFIGURAÇÃO MQTT---
MQTT_BROKER = "192.168.1.183"  #IP DO PC WINDOWS (Servidor que processa os dados)
MQTT_PORT = 1883    #Porta do Broker MQTT
TOPIC_ALERTA = "motopi/alerta/emergencia"   #Topico do MQTT
# ------------------------------------

#Variiáveis para alerta
CALL_DURATION = 30 
is_alerting = False
start_countdown_signal = False #Sinaliza ao loop principal para iniciar a contagem

#---FUNÇÃO PRINCIPAL DA CONTAGEM---
def handle_countdown(client):
    global is_alerting, cancelled
    
    is_alerting = True
    cancelled = False
    
    #Mensagem de Início e Instruções
    print("\n" + "#"*50)
    print("!!! ALERTA DE QUEDA RECEBIDO !!!")
    print("A ligar para os serviços de emergência (Aguardar 30 segundos...)")
    print("Prima [ENTER] para CANCELAR.")
    print("#"*50)

    #Loop de Contagem Decrescente
    try:
        for i in range(CALL_DURATION, 0, -1): #Loop de 30 segundos
            if cancelled:
                break
                
            #select.select verifica se há dados disponíveis para leitura no sys.stdin.
            rlist, _, _ = select.select([sys.stdin], [], [], 1.0)
            
            #Se houver dados para ler (utilizador premir ENTER) o programa para
            if rlist:
                line = sys.stdin.readline().strip().lower()
                if line == "":
                    cancelled = True
                    break
            
            if not cancelled:
                #O uso do \r faz com que a linha seja reescrita
                print(f"A ligar para os serviços de emergência: {i} segundos restantes...     \r", end='', flush=True)
            
    except Exception as e:
        print(f"\nErro durante a contagem: {e}")
        cancelled = True

    #Resultado Final
    print("\n" + "="*50)
    if cancelled:
        print("!!! CHAMADA CANCELADA PELO UTILIZADOR !!!")
    else:
        print("!!! CHAMADA EFETUADA - SERVIÇOS DE EMERGÊNCIA ALERTADOS !!!")
    print("="*50)

    is_alerting = False #Agora permite novos alertas

#---FUNÇÕES MQTT---
def on_connect(client, userdata, flags, rc):
    print("Conectado ao Broker MQTT.")
    client.subscribe(TOPIC_ALERTA)
    print(f"A escutar o tópico de alerta: {TOPIC_ALERTA}")

def on_message(client, userdata, msg):
    global is_alerting, start_countdown_signal
    alerta = msg.payload.decode()
    print(f"\n[INFO MQTT] Mensagem de alerta recebida: {alerta}")
    
    #Se não estivermos em contagem, levantamos o sinal para o loop principal
    if not is_alerting:
        start_countdown_signal = True
    else:
        print("[INFO] Ignorando novo alerta enquanto a contagem está ativa.")

#---INICIO---
client = mqtt.Client(client_id="Termux_Alerter")
client.on_connect = on_connect
client.on_message = on_message

print(f"A tentar conectar-se a {MQTT_BROKER}:{MQTT_PORT}...")

try:
    client.connect(MQTT_BROKER, MQTT_PORT, 60)
    
    #Inicia o loop MQTT para escutar em uma thread separada
    client.loop_start() 
    
    print("Cliente MQTT iniciado. A aguardar mensagens de alerta...")
    
    #---LOOP PRINCIPAL DE CONTROLO---
    while True:
        #Se o sinal for levantado pelo on_message, iniciar a contagem.
        if start_countdown_signal and not is_alerting:
            start_countdown_signal = False #Desligar o sinal
            handle_countdown(client) 
        
        time.sleep(0.5) 

except KeyboardInterrupt:
    print("\nAplicação encerrada.")
    client.loop_stop()
    client.disconnect()
except Exception as e:
    print(f"Erro: {e}")