# processor.py (No seu PC Windows)
import paho.mqtt.client as mqtt
import json
import time

# --- CONFIGURAÇÃO MQTT ---
MQTT_BROKER = "127.0.0.1"  # O Broker está neste PC - localhost
MQTT_PORT = 1883
TOPIC_TELEMETRY = "motopi/telemetria/ahrs"
TOPIC_ALERTA = "motopi/alerta/emergencia"

# --- VARIÁVEIS DE DETEÇÃO DE QUEDA ---
ROLL_LIMIT = 65.0 # Ângulo de Roll (inclinação lateral) em graus
DURATION_LIMIT = 2.0 # Segundos que o Roll deve ser mantido para ser considerado uma queda
FALL_DETECTED = False
fall_start_time = 0

# Função que corre quando recebe uma mensagem
def on_message(client, userdata, msg):
    global FALL_DETECTED, fall_start_time
    
    # 1. Desserializar os dados
    try:
        data = json.loads(msg.payload.decode())
        roll = abs(data['roll'])
        pitch = abs(data['pitch'])
    except json.JSONDecodeError:
        print(f"Erro ao processar JSON: {msg.payload.decode()}")
        return

    # 2. Lógica de Deteção de Queda
    current_time = time.time()
    
    # Condição: Roll muito elevado (e a mota não está "de cabeça para baixo" em pitch)
    if roll > ROLL_LIMIT and not FALL_DETECTED:
        
        # Inicia ou continua a contagem
        if fall_start_time == 0:
            fall_start_time = current_time
            print(f"[{data['timestamp']}] Inclinação extrema ({roll:.2f}º). Início da contagem...")
        
        elif (current_time - fall_start_time) >= DURATION_LIMIT:
            # Deteção de Queda Confirmada
            FALL_DETECTED = True
            
            alert_message = f"🚨 ALERTA DE QUEDA CONFIRMADO! Roll: {roll:.2f}º por mais de {DURATION_LIMIT}s."
            print("\n" + "*"*50)
            print(alert_message)
            print("A enviar alerta para o telemóvel...")
            print("*"*50 + "\n")
            
            # Envia o alerta para o telemóvel
            client.publish(TOPIC_ALERTA, alert_message)
            
    # Condição: Ângulo voltou ao normal (mota levantada ou em movimento normal)
    elif roll < (ROLL_LIMIT - 10) and FALL_DETECTED: # -10 para ter um histerese
        FALL_DETECTED = False
        fall_start_time = 0
        print("Estado redefinido. Movimento normal detetado.")
        
    # Condição: Inclinação alta, mas não sustentada (se a mota voltou a inclinar menos antes do limite)
    elif roll < ROLL_LIMIT and fall_start_time != 0:
        print(f"Contagem reiniciada. Roll: {roll:.2f}º")
        fall_start_time = 0

# Função de inicialização
def main():
    client = mqtt.Client(client_id="MotoPi_Processor")
    client.on_message = on_message
    
    try:
        print(f"A conectar ao Broker em: {MQTT_BROKER}:{MQTT_PORT}")
        client.connect(MQTT_BROKER, MQTT_PORT, 60)
        client.subscribe(TOPIC_TELEMETRY)
        print(f"A Sub-escrever ao tópico: {TOPIC_TELEMETRY}")
        
        # Loop bloqueante para processar mensagens
        client.loop_forever() 
        
    except KeyboardInterrupt:
        print("Servidor de Processamento encerrado.")
    except Exception as e:
        print(f"Erro no processador: {e}")

if __name__ == "__main__":
    main()