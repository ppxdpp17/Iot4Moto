#NO FUTURO (QUANDO CONSEGUIR UMA CÂMARA OU UMA FORMA DE LIGAR A M5STACK À CSVU), ESTE SCRIPT (OU OUTRO SEMELHANTE)
#PASSARÁ A SER CORRIDO NA CSVU, SENDO O FEED DA CÂMARA ENVIADO A PARTIR DE LÁ PARA O SMARTPHONE 
#!/bin/bash
# IP do teu broker MQTT na Variscite (Altera para o IP real da Variscite na vossa rede)
BROKER_IP="192.168.X.X" 

while true; do
    wget -q -O /tmp/frame.jpg http://10.254.239.1/api/video/frame

    if [ -s /tmp/frame.jpg ]; then
        base64 /tmp/frame.jpg | tr -d '\n' > /tmp/frame.b64
        mosquitto_pub -h $BROKER_IP -t "moto/camera" -f /tmp/frame.b64
    fi
    sleep 0.2
done