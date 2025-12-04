import paho.mqtt.client as mqtt
import json
import csv
import time
from pathlib import Path

#---CONFIGURAÇÃO MQTT---
MQTT_BROKER = "127.0.0.1" #Localhost (o script corre no mesmo PC do broker)

#Tópicos MQTT
TOPIC_LOG = "fcw/radar" #Tópico de telemetria FCW
TOPIC_ALERT = "fcw/alerta"  #Tópico de alertas FCW

#Setup de ficheiros de logging
DATA_DIR = Path.cwd() / "logs_fcw"
DATA_DIR.mkdir(exist_ok=True)
LOG_FILE = DATA_DIR / f"fusion_log_{int(time.time())}.csv"

#Cria o ficheiro com cabeçalho
file = open(LOG_FILE, "w", newline="", encoding="utf-8")
writer = csv.writer(file)
writer.writerow(["timestamp_server", "timestamp_pi", "latency_ms", "topic", "source", "dist_m", "vel_ms", "ttc", "alert_msg"])
file.flush() # Garante que o cabeçalho é gravado

print(f"---FCW BLACKBOX ATIVADA---")
print(f"Log: {LOG_FILE.name}")
print("A aguardar telemetria...")

#Conexão MQTT
def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print("Conectado ao Broker MQTT.")
        client.subscribe([(TOPIC_LOG, 0), (TOPIC_ALERT, 1)])
    else:
        print(f"Falha na conexão (rc={rc})")

#Função principal de receção de mensagens
def on_message(client, userdata, msg):
    try:
        payload = json.loads(msg.payload.decode())
        server_ts = time.time()
        
        #Extrair Timestamp de origem (do Pi)
        pi_ts = payload.get("ts", server_ts)
        
        #Calcular Latência (Lag da rede) em milissegundos
        #Nota: Requer que os relógios do PC e do Pi estejam +- sincronizados para ser preciso
        latency = (server_ts - pi_ts) * 1000 
        
        #Prepara linha CSV
        #[ServerTime, PiTime, Latency, Topic, Source, Dist, Vel, TTC, AlertMsg]
        row = [
            f"{server_ts:.3f}", 
            f"{pi_ts:.3f}", 
            f"{latency:.1f}",
            msg.topic,
            "", "", "", "", "" # Placeholders
        ]
        
        if msg.topic == TOPIC_LOG:
            row[4] = payload.get("source", "N/A")
            row[5] = payload.get("dist_m")
            row[6] = payload.get("vel_ms")
            row[7] = payload.get("ttc")
            
            #Feedback na consola
            print(f"[{row[4]}] Dist: {row[5]}m | Vel: {row[6]}m/s | Latência: {latency:.0f}ms")
            
        #Alertas no caso de aviso não ser GREEN
        elif msg.topic == TOPIC_ALERT:
            alert_lvl = payload.get("alert_level")
            if alert_lvl != "GREEN":
                msg_txt = f"{alert_lvl} - {payload.get('msg')}"
                row[8] = msg_txt
                print(f">>> ALERTA RECEBIDO: {msg_txt}")

        #Gravar e forçar escrita no disco
        if msg.topic == TOPIC_LOG or (msg.topic == TOPIC_ALERT and payload.get("alert_level") != "GREEN"):
            writer.writerow(row)
            file.flush() 
            
    except Exception as e:
        print(f"Erro processamento: {e}")

client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1, "Blackbox_FCW_PC")
client.on_connect = on_connect
client.on_message = on_message

try:
    client.connect(MQTT_BROKER, 1883, 60)
    client.loop_forever()
except KeyboardInterrupt:
    print("\nBlackbox Encerrada.")
    file.close()
except Exception as e:
    print(f"Erro fatal: {e}")