import cv2
import base64
import time
import paho.mqtt.client as mqtt

#Configuração do Broker MQTT
BROKER = "127.0.0.1" 
TOPIC = "moto/camera"

client = mqtt.Client("CSVU_Camera_Streamer")
client.connect(BROKER, 1883, 60)

#ID 0
cap = cv2.VideoCapture(0)

#Forçar resolução baixa (crítico para não rebentar o MQTT)
cap.set(cv2.CAP_PROP_FRAME_WIDTH, 640)
cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)

print("[CSVU] A capturar câmara e a publicar no MQTT...")

try:
    while True:
        ret, frame = cap.read()
        if not ret:
            print("Erro ao ler frame da câmara.")
            time.sleep(1)
            continue

        #Comprime para JPEG (qualidade 50%) para reduzir o tamanho do payload
        _, buffer = cv2.imencode('.jpg', frame, [int(cv2.IMWRITE_JPEG_QUALITY), 50])
        
        #Converte para Base64 (como a App Android está à espera)
        base64_str = base64.b64encode(buffer).decode('utf-8')

        #Publica no broker
        client.publish(TOPIC, base64_str)

        #5 FPS máximo
        time.sleep(0.2) 

except KeyboardInterrupt:
    print("\n[CSVU] Streaming terminado.")
    cap.release()
    client.disconnect()