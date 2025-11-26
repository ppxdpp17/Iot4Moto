import paho.mqtt.client as mqtt
import json
import time
import sys
import os
import subprocess

# --- CONFIGURAÇÃO ---
MQTT_BROKER = "172.20.0.130"
TOPIC_SUB = "v2x/cruzamento/objetos"

# --- SIMULAÇÃO DA PRÓPRIA MOTO (B) ---
# Começamos a 2.5m do cruzamento (lado esquerdo)
my_x = -2.5 
my_speed = 0.15 # 15 cm/s (velocidade de simulação lenta)
sim_running = True

def notify_collision(dist_c):
    try:
        subprocess.run(["termux-vibrate", "-d", "1000", "-f"], check=False)
        # Comando TTS (Text-to-Speech) se instalado, ou notificação
        # subprocess.run(["termux-tts-speak", "Carro à direita!"], check=False) 
    except: pass

def on_connect(client, userdata, flags, rc):
    print("OBU Conectada. A receber dados da Infraestrutura...")
    client.subscribe(TOPIC_SUB)

def on_message(client, userdata, msg):
    global my_x
    try:
        data = json.loads(msg.payload.decode())
        
        # Dados do Outro Veículo (C)
        c_y = data.get("pos_y", -99)
        
        # --- ALGORITMO DE COLISÃO DE ROTAS ---
        
        # Distância de ambos ao centro (0,0)
        dist_me_to_center = abs(my_x)
        dist_c_to_center = abs(c_y)
        
        # Se ambos estiverem perto do centro (< 60cm) ao mesmo tempo
        if dist_me_to_center < 0.60 and dist_c_to_center < 0.60:
            
            os.system('clear')
            print("\n" + "🟥"*30)
            print(" ⚠️  PERIGO DE COLISÃO  ⚠️")
            print(f" -> Veículo detetado pela RSU")
            print(f" -> Vindo da DIREITA (Sul)")
            print(f" -> A {dist_c_to_center*100:.0f}cm do impacto")
            print("🟥"*30 + "\n")
            
            notify_collision(dist_c_to_center)
            
        else:
            # Feedback visual de aproximação
            print(f"Eu(X): {my_x:.2f}m  <-->  Ele(Y): {c_y:.2f}m")

    except Exception as e:
        print(e)

# --- MAIN ---
client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1, "OBU_Smartphone")
client.on_connect = on_connect
client.on_message = on_message
client.connect(MQTT_BROKER, 1883, 60)
client.loop_start()

print("A simular condução em direção ao cruzamento...")

try:
    while True:
        # Simulação de Física: A mota avança no tempo
        if my_x < 0.0:
            my_x += my_speed * 0.1 # Avança a cada 100ms
        else:
            # Se passou o cruzamento, reinicia a simulação para testares outra vez
            print("--- Cruzamento Passado. A reiniciar simulação ---")
            time.sleep(2)
            my_x = -2.5
            
        time.sleep(0.1)

except KeyboardInterrupt:
    print("Sair.")
    client.disconnect()