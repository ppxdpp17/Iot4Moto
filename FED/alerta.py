# alerter.py (No seu Telemóvel/Termux)
import paho.mqtt.client as mqtt
import time
import sys
import select
import threading

# --- CONFIGURAÇÃO MQTT (ADAPTAR) ---
MQTT_BROKER = "192.168.1.183"  # **IP DO SEU PC WINDOWS**
MQTT_PORT = 1883
TOPIC_ALERTA = "motopi/alerta/emergencia"
# ------------------------------------

CALL_DURATION = 30 
is_alerting = False
start_countdown_signal = False # NOVO: Sinaliza ao loop principal para iniciar a contagem

# --- FUNÇÃO PRINCIPAL DA CONTAGEM ---
def handle_countdown(client):
    global is_alerting, cancelled
    
    is_alerting = True
    cancelled = False
    
    # 1. Mensagem de Início e Instruções
    print("\n" + "#"*50)
    print("!!! ALERTA DE QUEDA RECEBIDO !!!")
    print("A ligar para os serviços de emergência (Aguardar 30 segundos...)")
    print("Prima [ENTER] ou 'C' seguido de [ENTER] para CANCELAR.")
    print("#"*50)

    # 2. Loop de Contagem Decrescente
    try:
        for i in range(CALL_DURATION, 0, -1):
            if cancelled:
                break
                
            # select.select verifica se há dados disponíveis para leitura no sys.stdin.
            rlist, _, _ = select.select([sys.stdin], [], [], 1.0)
            
            if rlist:
                line = sys.stdin.readline().strip().lower()
                if line == 'c' or line == "":
                    cancelled = True
                    break
            
            if not cancelled:
                # O uso do \r faz com que a linha seja reescrita
                print(f"A ligar para os serviços de emergência: {i} segundos restantes...     \r", end='', flush=True)
            
    except Exception as e:
        print(f"\nErro durante a contagem: {e}")
        cancelled = True

    # 3. Resultado Final
    print("\n" + "="*50)
    if cancelled:
        print("!!! CHAMADA CANCELADA PELO UTILIZADOR !!!")
    else:
        print("!!! CHAMADA EFETUADA - SERVIÇOS DE EMERGÊNCIA ALERTADOS !!!")
    print("="*50)

    is_alerting = False # Permite novos alertas

# --- FUNÇÕES MQTT ---
def on_connect(client, userdata, flags, rc):
    print("Conectado ao Broker MQTT.")
    client.subscribe(TOPIC_ALERTA)
    print(f"A escutar o tópico de alerta: {TOPIC_ALERTA}")

def on_message(client, userdata, msg):
    global is_alerting, start_countdown_signal
    alerta = msg.payload.decode()
    print(f"\n[INFO MQTT] Mensagem de alerta recebida: {alerta}")
    
    # Se não estivermos em contagem, levantamos o sinal para o loop principal
    if not is_alerting:
        start_countdown_signal = True
    else:
        print("[INFO] Ignorando novo alerta enquanto a contagem está ativa.")

# --- INICIALIZAÇÃO ---
client = mqtt.Client(client_id="Termux_Alerter")
client.on_connect = on_connect
client.on_message = on_message

print(f"A tentar conectar em {MQTT_BROKER}:{MQTT_PORT}...")

try:
    client.connect(MQTT_BROKER, MQTT_PORT, 60)
    
    # Inicia o loop MQTT para escutar em uma thread separada
    client.loop_start() 
    
    print("Cliente MQTT iniciado. Aguardando mensagens de alerta...")
    
    # --- NOVO LOOP PRINCIPAL DE CONTROLE ---
    while True:
        # Se o sinal for levantado pelo on_message, iniciar a contagem.
        if start_countdown_signal and not is_alerting:
            start_countdown_signal = False # Baixar o sinal
            # Chamar a função de contagem no thread principal.
            # Isto garante que o sys.stdin (input) funciona corretamente.
            handle_countdown(client) 
        
        # O sleep é essencial para não consumir 100% da CPU
        time.sleep(0.5) 

except KeyboardInterrupt:
    print("\nAplicação encerrada.")
    client.loop_stop()
    client.disconnect()
except Exception as e:
    print(f"Erro principal: {e}")