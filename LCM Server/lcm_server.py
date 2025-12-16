import paho.mqtt.client as mqtt
import json
import time

# --- CONFIGURAÇÃO ---
# O LCM conecta-se ao Broker Local do Windows
# (O Broker é que gere a rede, o script apenas consome)
LOCAL_HOST = "127.0.0.1" 
LOCAL_PORT = 1883

# LCM fala com a App na Internet
PUBLIC_BROKER = "broker.hivemq.com"
PUBLIC_PORT = 1883

# --- CORREÇÃO DOS TÓPICOS (CRUCIAL!) ---
# Agora o LCM ouve TUDO o que começa por "pedro_thesis/moto/"
TOPIC_MOTO_IN = "pedro_thesis/moto/#"          

# Tópico para onde enviamos alertas para a App
TOPIC_APP_OUT = "pedro_thesis/moto/app" 

# Tópicos de Comandos vindos da App
TOPIC_APP_CMD_IN = "pedro_thesis/moto/cmd/#"
TOPIC_MOTO_CMD_OUT = "pedro_thesis/moto/cmd/security"

# --- CLIENTE PÚBLICO (HiveMQ <-> LCM) ---
def on_public_connect(client, userdata, flags, rc):
    print(f"✅ LCM conectado à Internet (HiveMQ). RC: {rc}")
    client.subscribe(TOPIC_APP_CMD_IN)

def on_public_message(client, userdata, msg):
    """Recebe comando da App e manda para a Mota"""
    try:
        print(f"📱 [APP -> LCM] Comando recebido: {msg.topic}")
        # Reencaminha para a rede local (Mota)
        client_local.publish(TOPIC_MOTO_CMD_OUT, msg.payload, qos=1)
        print("   └── ⚡ Enviado para a Mota (Local)")
    except Exception as e:
        print(f"Erro relay comando: {e}")

client_public = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1, "LCM_Relay_Public")
client_public.on_connect = on_public_connect
client_public.on_message = on_public_message

# --- CLIENTE LOCAL (Mota <-> LCM) ---
def on_local_connect(client, userdata, flags, rc):
    print(f"✅ LCM conectado ao Broker Local. RC: {rc}")
    # Ouve tudo o que a mota disser
    client.subscribe(TOPIC_MOTO_IN)

def on_local_message(client, userdata, msg):
    """Recebe dados da Mota e manda para a App"""
    # Evita loops infinitos (não ouvir o que ele próprio enviou)
    if "cmd" in msg.topic: return 

    try:
        payload_str = msg.payload.decode()
        data = json.loads(payload_str)
        
        print(f"📥 [MOTO] Recebido: {data.get('type')} | Msg: {data.get('msg')}")
        
        # Filtra tipos de alerta para enviar para a App
        # Nota: O Pi envia type="THEFT" ou "ACCIDENT"
        if data.get("type") in ["ACCIDENT", "THEFT", "SECURITY"]:
            print("   🚀 [LCM -> APP] Reencaminhando alerta via 4G...")
            
            # Constrói o pacote final para a App
            app_payload = {
                "alert": "CRITICAL" if data.get("type") == "ACCIDENT" else "SECURITY",
                "msg": data.get("msg", "Alerta desconhecido"),
                "ts": time.time(),
                "val": data.get("val", "0")
            }
            client_public.publish(TOPIC_APP_OUT, json.dumps(app_payload), qos=1)

    except json.JSONDecodeError:
        pass # Ignora mensagens que não sejam JSON
    except Exception as e:
        print(f"Erro processamento local: {e}")

client_local = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1, "LCM_Internal")
client_local.on_connect = on_local_connect
client_local.on_message = on_local_message

# --- ARRANQUE ---
print("--- A INICIAR LCM SERVER ---")
try:
    # Liga primeiro à cloud para garantir saída
    client_public.connect(PUBLIC_BROKER, PUBLIC_PORT, 60)
    client_public.loop_start()
    
    # Liga ao local
    client_local.connect(LOCAL_HOST, LOCAL_PORT, 60)
    client_local.loop_forever()
    
except Exception as e:
    print(f"❌ ERRO FATAL: {e}")
    input("Pressione Enter para sair...")