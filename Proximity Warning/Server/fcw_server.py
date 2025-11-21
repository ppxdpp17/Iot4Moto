# fcw_server.py - PC Windows
import paho.mqtt.client as mqtt
import json
import time

# --- CONFIGURAÇÃO ---
MQTT_BROKER = "127.0.0.1" # Localhost
TOPIC_SUB = "fcw/radar"
TOPIC_PUB_ALERTA = "fcw/alerta"

# Limites de Segurança
VELOCIDADE_PERIGO_CMS = 18.0  # Se aproximar > 40 cm/s
DISTANCIA_CRITICA_CM = 5.0   # Se estiver a menos de 5cm

# Variáveis de Estado
last_reading = None

def on_connect(client, userdata, flags, rc):
    print("Servidor FCW conectado. A aguardar dados do radar...")
    client.subscribe(TOPIC_SUB)

def on_message(client, userdata, msg):
    global last_reading
    
    try:
        data = json.loads(msg.payload.decode())
        curr_dist = data['dist']
        curr_ts = data['ts']

        if last_reading:
            # Calcular velocidade relativa (aproximação)
            delta_dist = last_reading['dist'] - curr_dist
            delta_time = curr_ts - last_reading['ts']

            if delta_time > 0:
                vel_aprox = delta_dist / delta_time # cm/s
                
                # Só mostra no log se houver aproximação significativa
                if vel_aprox > 10:
                    print(f"Dist: {curr_dist:.1f}cm | Aprox: {vel_aprox:.1f} cm/s")

                # --- LÓGICA DE DECISÃO ---
                msg_alerta = ""
                
                # 1. Travagem Brusca do carro da frente
                if vel_aprox > VELOCIDADE_PERIGO_CMS:
                    msg_alerta = f"TRAVA! Aprox. Rápida ({int(vel_aprox)} cm/s)"
                
                # 2. Distância de Segurança violada
                elif curr_dist < DISTANCIA_CRITICA_CM and vel_aprox > 5:
                    msg_alerta = f"MUITO PERTO ({int(curr_dist)} cm)!"

                if msg_alerta:
                    print(f">>> ENVIANDO ALERTA: {msg_alerta}")
                    client.publish(TOPIC_PUB_ALERTA, msg_alerta, qos=1)

        # Atualiza estado
        last_reading = {'dist': curr_dist, 'ts': curr_ts}

    except Exception as e:
        print(f"Erro processamento: {e}")

client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1, "Servidor_PC_Brain")
client.on_connect = on_connect
client.on_message = on_message

try:
    client.connect(MQTT_BROKER, 1883, 60)
    client.loop_forever()
except KeyboardInterrupt:
    print("Servidor desligado.")