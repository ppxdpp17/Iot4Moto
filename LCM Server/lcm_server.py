import paho.mqtt.client as mqtt
import json
import time
import sys
import os
import config

#---Ligação ao HiveMQ (público) para poder falar com a App---
def on_public_connect(client, userdata, flags, rc):
    print(f"-LCM conectado à Internet (HiveMQ). RC: {rc}")
    client.subscribe(config.TOPIC_APP_CMD_IN)   #Ouve comandos da App (Armar/Desarmar)

#Recebe comando da App e envia para a Mota
def on_public_message(client, userdata, msg):
    try:
        print(f"[APP -> LCM] Comando recebido via Cloud")
        #Reencaminha para a rede local (Mota)
        client_local.publish(config.TOPIC_MOTO_CMD_OUT, msg.payload, qos=1)
        print("     └── Enviado para a Mota (Local)")
    except Exception as e:
        print(f"Erro relay comando: {e}")

#Configuração MQTT Público (Internet)
client_public = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1, "LCM_Relay_Public")
client_public.on_connect = on_public_connect
client_public.on_message = on_public_message

#---Ligação à Mota (privada) (Mota <-> LCM)---
def on_local_connect(client, userdata, flags, rc):
    print(f"-LCM conectado ao Broker Local (Windows). RC: {rc}")
    #Ouve tudo o que a mota disser
    client.subscribe(config.TOPIC_MOTO_IN)

#Recebe dados da Mota (sensores) e envia para a App
def on_local_message(client, userdata, msg):
    if "cmd" in msg.topic: return   #Evita loops (ignora mensagens de comando que ele próprio enviou)

    try:
        payload_str = msg.payload.decode()
        data = json.loads(payload_str)
        
        print(f"[MOTA] Recebido: {data.get('type')} | Msg: {data.get('msg')}")
        
        #Filtra alertas importantes (Acidente, Roubo, Segurança)
        if data.get("type") in ["ACCIDENT", "THEFT", "SECURITY"]:
            print("     [LCM -> APP] A reencaminhar alerta via 4G...")
            
            #Cria o pacote para a App
            app_payload = {
                "alert": "CRITICAL" if data.get("type") == "ACCIDENT" else "SECURITY",
                "msg": data.get("msg", "Alerta"),
                "ts": time.time(),
                "val": data.get("val", "0")
            }

            #Envia para a Internet (HiveMQ) para a App receber
            client_public.publish(config.TOPIC_APP_OUT, json.dumps(app_payload), qos=1)

    except json.JSONDecodeError:
        pass 
    except Exception as e:
        print(f"Erro processamento local: {e}")

#Configuração MQTT Local (Mota <-> LCM)
client_local = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1, "LCM_Internal")
client_local.on_connect = on_local_connect
client_local.on_message = on_local_message

#---ARRANQUE---
print("---A INICIAR LCM SERVER (CONFIG CARREGADA)---")
try:
    #Liga à Cloud Pública (Internet)
    client_public.connect(config.PUBLIC_BROKER, config.PUBLIC_PORT, 60)
    client_public.loop_start()
    
    #Liga ao Broker Local (Onde a mota se conecta via Wi-Fi)
    client_local.connect(config.LOCAL_HOST, config.LOCAL_PORT, 60)
    client_local.loop_forever()
    
except Exception as e:
    print(f"ERRO FATAL: {e}")
    input("Pressione Enter para sair...")