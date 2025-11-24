#!/usr/bin/env python3
"""
termux_gps_v2v.py
- Envia GPS (telemetria/B)
- Recebe Alertas (alerta/B)
- Compatível com Paho MQTT 2.0+
"""
import subprocess, json, time, sys, os
import paho.mqtt.client as mqtt

# -------------- CONFIG --------------
BROKER = "172.20.0.130" # <-- CONFIRMA ESTE IP
PORT = 1883
DEVICE_ID = "B"
TOPIC_PUB = "telemetria/B"
TOPIC_SUB_ALERTA = "alerta/B"
PUBLISH_INTERVAL = 1.0 # Mais rápido para V2V (1Hz)
# ------------------------------------

def run_termux_location(provider="gps"):
    # Tenta obter localização via CLI
    try:
        cmd = ["termux-location", "-p", provider]
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=5)
        data = json.loads(proc.stdout)
        return data if isinstance(data, dict) else data[0]
    except:
        return None

def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print(f"[MQTT] Conectado! A enviar GPS para {TOPIC_PUB}")
        print(f"[MQTT] A escutar alertas em {TOPIC_SUB_ALERTA}")
        client.subscribe(TOPIC_SUB_ALERTA)
    else:
        print(f"Erro conexão: {rc}")

def on_message(client, userdata, msg):
    # CALLBACK DE ALERTA
    alerta = msg.payload.decode()
    
    # Visualização de Alerta
    os.system('clear')
    print("\n" + "!"*40)
    print("   ⚠️  ALERTA DE COLISÃO V2V  ⚠️")
    print("!"*40)
    print(f"\nMSG: {alerta}")
    print("\n" + "!"*40 + "\n")
    
    # Tenta vibrar o telemóvel
    try:
        subprocess.run(["termux-vibrate", "-d", "1000", "-f"])
        # Tenta notificação no sistema
        subprocess.run([
            "termux-notification", 
            "--title", "ALERTA V2V", 
            "--content", alerta, 
            "--priority", "high",
            "--vibrate", "1000"
        ])
    except:
        pass

def main():
    # FIX: Adicionado CallbackAPIVersion para Paho MQTT 2.0+
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1, f"Termux_{DEVICE_ID}")
    client.on_connect = on_connect
    client.on_message = on_message

    try:
        client.connect(BROKER, PORT, 60)
        client.loop_start() # Loop em background para receber alertas

        while True:
            # 1. Ler GPS
            loc = run_termux_location("gps")
            if not loc: 
                loc = run_termux_location("network")

            # 2. Enviar se válido
            if loc and 'latitude' in loc:
                payload = {
                    "device_id": DEVICE_ID,
                    "ts": time.time(),
                    "lat": loc['latitude'],
                    "lon": loc['longitude'],
                    "speed": loc.get('speed', 0), # m/s
                    "bearing": loc.get('bearing', 0)
                }
                client.publish(TOPIC_PUB, json.dumps(payload), qos=0)
                # print(".", end="", flush=True) # Feedback minimalista
            
            time.sleep(PUBLISH_INTERVAL)

    except KeyboardInterrupt:
        print("\nParado.")
    finally:
        client.loop_stop()
        client.disconnect()

if __name__ == "__main__":
    main()