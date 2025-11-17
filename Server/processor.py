#processor.py (Código que corre no PC windows para processar os dados e publicar alertas MQTT)
import paho.mqtt.client as mqtt
import json
import time

#---CONFIGURAÇÃO MQTT---
MQTT_BROKER = "127.0.0.1"  # Broker (Mosquitto) está no próprio PC (localhost)
MQTT_PORT = 1883    #Porta MQTT
TOPIC_TELEMETRY = "motopi/telemetria/ahrs" #Tópico onde os dados de telemetria são publicados (vindos do PI)
TOPIC_ALERTA = "motopi/alerta/emergencia" #Tópico onde os alertas de queda são publicados

#---VARIÁVEIS DE DETEÇÃO DE QUEDA---
ROLL_LIMIT = 65.0    #Ângulo de Roll (inclinação) em graus.
DURATION_LIMIT = 2.0 #Segundos que o Roll deve ser mantido

FALL_DETECTED = False #Booleano que define se a queda foi confirmada e o alerta enviado
fall_start_time = 0   #Timestamp do início da contagem
is_counting = False   #Booleano que define se estamos a contar o tempo de Roll > ROLL_LIMIT

#Função que corre quando recebe uma mensagem
def on_message(client, userdata, msg):
    global FALL_DETECTED, fall_start_time, is_counting
    
    #Desserialize os dados
    try:
        data = json.loads(msg.payload.decode())
        roll = abs(data['roll'])
        ts_ms = data['timestamp']
    except json.JSONDecodeError:
        return

    current_time = time.time()
    
    #ESTADO: Ângulo Acima do Limite (Começa ou Continua a Contar)
    if roll >= ROLL_LIMIT and not FALL_DETECTED:
        
        #INÍCIO DA CONTAGEM -> Se não está a contar
        if not is_counting:
            fall_start_time = current_time
            is_counting = True #Marca que a contagem começou
            print(f"[{ts_ms}] - ALERTA! Inclinação extrema ({roll:.2f}º). Início da contagem de queda...")
        
        #CONTINUAÇÃO DA CONTAGEM: Se a duração foi atingida (mais que 2 segundos)
        elif (current_time - fall_start_time) >= DURATION_LIMIT:
            
            #---CONFIRMAÇÃO DE QUEDA---
            FALL_DETECTED = True
            is_counting = False #A contagem para quando a queda é confirmada
            fall_start_time = 0 

            alert_message = f"ALERTA DE QUEDA CONFIRMADO! Roll: {roll:.2f}º sustentado por mais de {DURATION_LIMIT}s."
            
            print("\n" + "!"*50)
            print(alert_message)
            client.publish(TOPIC_ALERTA, alert_message)
            print("!"*50 + "\n")
            
    #ESTADO: Ângulo Abaixo do Limite ou Interrupção
    #Faz reset se o roll descer para menos do limite enquanto o sistema estava a contar/em alerta
    elif roll < ROLL_LIMIT and (FALL_DETECTED or is_counting):
        
        #O Roll caiu abaixo do limite, mas não vamos dar reset imediato para lidar com ruído
        #Só damos reset total se for muito baixo (< 35), ou se a flutuação persistir
        if roll < (ROLL_LIMIT - 30) or (is_counting and (current_time - fall_start_time) > 0.5 and roll < ROLL_LIMIT):
             
            if FALL_DETECTED:
                print("ALERTA CANCELADO. Mota levantada/em movimento.")
                 
            FALL_DETECTED = False
            is_counting = False
            fall_start_time = 0
        
#Função de inicialização
def main():
    client = mqtt.Client(client_id="MotoPi_Processor")
    client.on_message = on_message
    
    try:
        print(f"A iniciar Servidor de Processamento. A conectar ao Broker em: {MQTT_BROKER}:{MQTT_PORT}")
        client.connect(MQTT_BROKER, MQTT_PORT, 60)
        client.subscribe(TOPIC_TELEMETRY)
        print(f"A Sub-escrever ao tópico de telemetria: {TOPIC_TELEMETRY}")
        
        client.loop_forever() 
        
    except KeyboardInterrupt:
        print("\nServidor de Processamento encerrado.")
    except Exception as e:
        print(f"Erro no processador: {e}")

if __name__ == "__main__":
    main()