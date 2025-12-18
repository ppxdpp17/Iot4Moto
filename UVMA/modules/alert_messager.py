# ~/amover_uvma/modules/alert_manager.py
import paho.mqtt.client as mqtt
import json
import subprocess
import sys
import os
import time
import select # Biblioteca para ler input com timeout (Funciona no Termux)

# Adiciona a pasta pai ao path para importar config
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import config

# --- VARIÁVEIS DE ESTADO (Evitar sobreposição) ---
alert_in_progress = False

#Envia notificação para o Android
def trigger_android_notification(title, body, critical=False):
    try:
        duration = "2000" if critical else "500"
        subprocess.run(["termux-vibrate", "-d", duration, "-f"], check=False)
        
        priority = "high" if critical else "default"
        subprocess.run([
            "termux-notification",
            "--title", title,
            "--content", body,
            "--priority", priority,
            "--id", "amover_alert",
            "--action", f"termux-open-url {config.VIDEO_STREAM_URL}"
        ], check=False)
        
        if critical:
            subprocess.run(["termux-tts-speak", "Atenção! Acidente detetado."], check=False)
    except: pass

def handle_countdown(seconds, msg_text):
    """Gere a contagem decrescente com cancelamento"""
    global alert_in_progress
    
    os.system('clear')
    print("\n" + "*"*34)
    print(" ---DETEÇÃO DE ACIDENTE GRAVE--- ")
    print("*"*34)
    print(f"\nDetalhes: {msg_text}")
    print("-" * 34)
    print(f"CHAMADA AUTOMÁTICA 112 EM {seconds}s")
    print(">>> Pressiona [ENTER] para CANCELAR <<<")
    print("-" * 34 + "\n")

    cancelled = False
    
    # Loop da Contagem
    for i in range(seconds, 0, -1):
        # Mostra o tempo restante sem mudar de linha (\r)
        print(f"    -A ligar em {i} segundos...  ", end='\r', flush=True)
        
        # Espera 1 segundo por input do teclado
        # select([sys.stdin], [], [], 1.0) devolve true se alguém carregou numa tecla
        rlist, _, _ = select.select([sys.stdin], [], [], 1.0)
        
        if rlist:
            # Se alguém carregou no Enter, lemos a tecla e cancelamos
            sys.stdin.readline()
            cancelled = True
            break
            
    print("\n" + "="*34)
    
    if cancelled:
        print("ALERTA CANCELADO PELO UTILIZADOR.")
        trigger_android_notification("AMOVER", "SOS Cancelado pelo utilizador.")
    else:
        print("-A LIGAR PARA O 112... (Simulação)!")
        # Simula a chamada no Android
        trigger_android_notification("112", "A LIGAR PARA EMERGÊNCIA...", critical=True)
        try:
            # Comando para iniciar chamada (pede permissão no Android 1ª vez)
            subprocess.run(["termux-telephony-call", "112"], check=False)
        except:
            print("(Permissão de chamada não concedida)")

    print("="*34 + "\n")
    time.sleep(3) # Pausa para ler
    alert_in_progress = False # Liberta o sistema para novos alertas

#Gere o alerta de roubo (sem contagem)
def handle_theft_alert(msg_text):
    global alert_in_progress
    
    os.system('clear')
    print("\n" + "~"*30)
    print(" ---ALERTA DE SEGURANÇA (ROUBO)--- ")
    print("~"*30)
    print(f"\n{msg_text}")
    print("\nVerifica a câmara imediatamente.")
    print("-" * 30)
    
    trigger_android_notification("ALERTA ROUBO", msg_text)
    
    # Bloqueia o ecrã por 5 segundos para garantir que o user vê
    time.sleep(5)
    alert_in_progress = False

def on_message(client, userdata, msg):
    global alert_in_progress
    
    try:
        payload = json.loads(msg.payload.decode())
        alert_type = payload.get("alert")
        msg_text = payload.get("msg", "")

        # --- LÓGICA DE BLOQUEIO (DEBOUNCE) ---
        if alert_in_progress:
            # Se já estamos a tratar de um acidente, ignoramos novos dados
            # Mas podemos imprimir um pequeno aviso no log se quisermos
            return 

        # Se sistema livre, iniciamos o alerta
        alert_in_progress = True
        
        if alert_type == "CRITICAL": # Acidente
            trigger_android_notification("ACIDENTE!", "Contagem iniciada...", critical=True)
            handle_countdown(10, msg_text) # 10 Segundos
            
        elif alert_type == "SECURITY": # Roubo
            handle_theft_alert(msg_text)
            
    except Exception as e:
        print(f"Erro handler: {e}")
        alert_in_progress = False

def start_listening():
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1, "UVMA_Safety_Core")
    try:
        client.connect(config.MQTT_BROKER, config.MQTT_PORT, 60)
        client.subscribe(config.TOPIC_SUB_ALERTS)
        client.on_message = on_message
        client.loop_start()
        return client
    except Exception as e:
        print(f"Erro Net: {e}")
        return None