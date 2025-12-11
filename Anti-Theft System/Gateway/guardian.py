#Código que envia informações diretamente para o smartphone do user
import sensor_handler
from smbus import SMBus
import paho.mqtt.client as mqtt
import time
import math
import json

#---CONFIGURAÇÃO MQTT---
MQTT_BROKER = "172.20.0.130" #IP do PC Windows (Aqui funciona apenas como Broker)
TOPIC_CMD = "moto/seguranca/comando"  #Tópico que recebe "ARMAR", "DESARMAR"
TOPIC_ALERT = "moto/seguranca/alerta" #Tópico que publica "ALERTA DE ROUBO"

#Sensibilidade do Alarme (Diferença de aceleração)
SENSITIVITY = 0.15	#Valor muito baixo, ou seja, muito sensível (movimento mínimo lança logo alerta)

#Estado inicial do sistema
sistema_armado = False

#Connect MQTT
def on_connect(client, userdata, flags, rc):
    print("Guarda conectado ao Broker.")
    client.subscribe(TOPIC_CMD)

#Função que recebe os comandos via MQTT
def on_message(client, userdata, msg):
    global sistema_armado
    comando = msg.payload.decode().upper()

	#Se o comando for "ARMAR", ativa o "modo de estacionamento"
    if comando == "ARMAR":
        sistema_armado = True
        print("\nMODO ESTACIONAMENTO ATIVADO (SISTEMA ARMADO)")
        #Envia feedback
        client.publish(TOPIC_ALERT, json.dumps({"tipo": "INFO", "msg": "Sistema Armado"}))

	#Se o comando for "DESARMAR", desativa o "modo de estacionamento"
    elif comando == "DESARMAR":
        sistema_armado = False
        print("\nMODO CONDUÇÃO (SISTEMA DESARMADO)")
        client.publish(TOPIC_ALERT, json.dumps({"tipo": "INFO", "msg": "Sistema Desarmado"}))


def main():
    bus = SMBus(1)
    sensor_handler.init_mpu(bus)
    acc_bias, _ = sensor_handler.calibrate(bus)
	
	#Conxão MQTT
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1, "Moto_Guardian")
    client.on_connect = on_connect
    client.on_message = on_message
    client.connect(MQTT_BROKER, 1883, 60)
    client.loop_start()

    #Variáveis para deteção de movimento
    last_svm = 1.0 #Gravidade normal (~1g)

    print("Sistema de Segurança Iniciado. À espera de comandos...")

    try:
        while True:
            #1 - Ler Sensor
            #Lê apenas 1 amostra para ser rápido
            ax, ay, az, _, _, _ = sensor_handler.read_all(bus)

            #Converter para 'g' aproximado (sem a conversão complexa do handler para ser leve)
            #16384.0 é a sensibilidade default do MPU6050
            ax_g = ax / 16384.0
            ay_g = ay / 16384.0
            az_g = az / 16384.0

            #Calcular SVM
            curr_svm = math.sqrt(ax_g**2 + ay_g**2 + az_g**2)

            #2 - Lógica de Alarme
            if sistema_armado:
                delta = abs(curr_svm - last_svm)

                if delta > SENSITIVITY:
                    print(f"\n!!!MOVIMENTO DETETADO!!! Delta: {delta:.3f}g")

                    #Envia alerta JSON
                    payload = {
                        "tipo": "ROUBO",
                        "msg": "MOVIMENTO DETETADO NA MOTO!",
                        "val": f"{delta:.2f}g"
                    }
                    client.publish(TOPIC_ALERT, json.dumps(payload), qos=2)

                    #Pequena pausa para não spammar alertas
                    time.sleep(5)

            last_svm = curr_svm
            time.sleep(0.1) #10Hz monitorização

    except KeyboardInterrupt:
        pass
    finally:
        client.loop_stop()
        client.disconnect()

if __name__ == "__main__":
    main()
