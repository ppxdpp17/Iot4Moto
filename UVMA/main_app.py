import time
import os
import json
import sys
import config
from modules import alert_manager, video_player

#Iniciar Serviço de Alertas em Background
mqtt_client = alert_manager.start_listening()

#Envia comandos para a Mota via Cloud
def send_command(cmd_type):
    if mqtt_client:
        payload = {"cmd": cmd_type, "ts": time.time()}
        mqtt_client.publish(config.TOPIC_PUB_CMD, json.dumps(payload))
        print(f"\nComando enviado via Cloud: {cmd_type}")
        time.sleep(1.5)
    else:
        print("\nErro: Sem conexão à Cloud.")
        time.sleep(1)

def show_menu():
    #"UI" da app móvel
    os.system('clear')
    print("========================================")
    print("              A-MOVER UVMA              ")
    print("========================================")
    print(f"    Cloud: {config.MQTT_BROKER}        ")
    print("----------------------------------------")
    print("  1. Ligar Modo de Estacionamento"       )
    print("  2. Desligar Modo de Estacionamento"    )
    print("  3. Visualizar Feed da Câmera"          )
    print("  4. Sair"                               )
    print("----------------------------------------")

#---LOOP PRINCIPAL (interação)---
try:
    while True:
        show_menu()
        opt = input("Seleciona uma Opção: ")
        
        if opt == "1":
            send_command("ARM")
        elif opt == "2":
            send_command("DISARM")
        elif opt == "3":
            video_player.open_feed()
            input("\n[Pressiona Enter para voltar ao menu...]")
        elif opt == "4":
            print("\nA fechar aplicação...")
            break
        else:
            print("\nOpção inválida!")
            time.sleep(1)

except KeyboardInterrupt:
    print("\nFechado pelo utilizador.")
finally:
    if mqtt_client:
        mqtt_client.loop_stop()
        mqtt_client.disconnect()