# ~/amover_uvma/main_app.py
import time
import os
import json
import sys
import config
from modules import alert_manager, video_player

# 1. Iniciar Serviço de Alertas em Background
mqtt_client = alert_manager.start_listening()

def send_command(cmd_type):
    """Envia comandos para a Mota via Cloud"""
    if mqtt_client:
        payload = {"cmd": cmd_type, "ts": time.time()}
        mqtt_client.publish(config.TOPIC_PUB_CMD, json.dumps(payload))
        print(f"\n ✅ Comando enviado via Cloud: {cmd_type}")
        time.sleep(1.5)
    else:
        print("\n ❌ Erro: Sem conexão à Cloud.")
        time.sleep(1)

def show_menu():
    # Limpa ecrã para parecer uma App
    os.system('clear')
    print("========================================")
    print("      A-MOVER UVMA (Mobile App)         ")
    print("========================================")
    print(f"☁️  Cloud: {config.MQTT_BROKER}")
    print("----------------------------------------")
    print("  1. 🔒  ARMAR SISTEMA (Modo Estacionamento)")
    print("  2. 🔓  DESARMAR SISTEMA (Modo Condução)")
    print("  3. 📹  VER CÂMARA EM DIRETO")
    print("  4. ❌  SAIR")
    print("----------------------------------------")

# --- LOOP PRINCIPAL (UI) ---
try:
    while True:
        show_menu()
        opt = input("Selecione Opção: ")
        
        if opt == "1":
            send_command("ARM")
        elif opt == "2":
            send_command("DISARM")
        elif opt == "3":
            video_player.open_feed()
            input("\n[Pressione Enter para voltar ao menu...]")
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