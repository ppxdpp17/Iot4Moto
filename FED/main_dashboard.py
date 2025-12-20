import tkinter as tk
from tkinter import font
import paho.mqtt.client as mqtt
import json
import threading
import config

class DashboardApp:
    #Definir o layout da UI e arrancar os processos
    def __init__(self, root):
        self.root = root
        self.root.title("FED FULGORA")  #Título da janela

        #Tamanho e cor da janela    
        self.root.geometry(config.WINDOW_SIZE)
        self.root.configure(bg=config.BG_COLOR)
        

        #---Layout do UI---

        #Header
        self.lbl_title = tk.Label(root, text="Sistemas Iniciados", font=("Arial", 14), bg=config.BG_COLOR, fg="#888888")
        self.lbl_title.pack(pady=10)

        #Área Central (Velocidade e Alertas)
        self.frame_center = tk.Frame(root, bg=config.BG_COLOR)
        self.frame_center.pack(expand=True)
        self.lbl_speed_val = tk.Label(self.frame_center, text="0", font=("Helvetica", 80, "bold"), bg=config.BG_COLOR, fg=config.TEXT_COLOR)
        self.lbl_speed_val.pack()
        self.lbl_speed_unit = tk.Label(self.frame_center, text="km/h", font=("Arial", 16), bg=config.BG_COLOR, fg="#AAAAAA")
        self.lbl_speed_unit.pack()


        #Área de Alertas (Inicialmente vazia)
        self.lbl_alert = tk.Label(root, text="", font=("Arial", 24, "bold"), bg=config.BG_COLOR, fg=config.ALERT_COLOR_GREEN)
        self.lbl_alert.pack(pady=20, fill='x')


        #Rodapé (Detalhes de telemetria)
        self.frame_footer = tk.Frame(root, bg="#1E1E1E", height=40)
        self.frame_footer.pack(fill='x', side='bottom')
        self.lbl_telemetry = tk.Label(self.frame_footer, text="Roll: 0.0° | Pitch: 0.0° | SVM: 1.0g", font=("Consolas", 10), bg="#1E1E1E", fg="#00FF00")
        self.lbl_telemetry.pack(pady=10)

        #---MQTT START---
        self.start_mqtt()

    #Função que recebe os dados e define que ações tomar na UI
    def update_ui(self, data, topic):
        #Atualiza a interface baseado no tópico
        if "fcw" in topic:  #Forward Collision Warning
            dist = data.get("dist", 0)
            ttc = data.get("ttc", 99)
            tipo = data.get("type", "INFO")
            
            if tipo == "ACCIDENT": #Colisão Iminente -> cor vermelha
                self.root.configure(bg=config.ALERT_COLOR_RED)
                self.lbl_alert.config(text=f"!!!TRAVAR AGORA!!!\n{dist}m", bg=config.ALERT_COLOR_RED, fg="white")
            elif tipo == "WARNING": #Aviso de aproximação -> cor amarela
                self.lbl_alert.config(text=f"->APROXIMAÇÃO ({dist}m!!!)", fg=config.ALERT_COLOR_YELLOW, bg=config.BG_COLOR)
                self.root.configure(bg=config.BG_COLOR)
            else:   #Normal
                self.lbl_alert.config(text="", bg=config.BG_COLOR)
                self.root.configure(bg=config.BG_COLOR)

        elif "accident" in topic:   #Accident Warning
            if data.get("type") == "ACCIDENT":  #Queda Detetada -> cor vermelha
                self.lbl_alert.config(text="QUEDA DETETADA \nSOS ENVIADO", fg="white", bg="red")
                self.root.configure(bg="red")
            elif data.get("type") == "CANCEL":  #Cancelamento de Alerta -> Normal
                self.lbl_alert.config(text="SISTEMA NORMAL", fg=config.ALERT_COLOR_GREEN, bg=config.BG_COLOR)
                self.root.configure(bg=config.BG_COLOR)

        #Se não acontecer nenhuma das anteriores, mostra apenas dados do IMU
        elif "telemetry" in topic:
            r = data.get("roll", 0)
            p = data.get("pitch", 0)
            svm = data.get("svm", 0)
            self.lbl_telemetry.config(text=f"Roll: {r}° | Pitch: {p}° | G-Force: {svm}g")

    #Thread MQTT para receber dados
    def mqtt_thread(self):
        client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1, "FED_Windows_GUI")
        
        #Callback de receção de mensagens
        def on_message(client, userdata, msg):
            try:
                #Atualiza UI com base na mensagem recebida
                payload = json.loads(msg.payload.decode())
                self.root.after(0, self.update_ui, payload, msg.topic)
            except: pass

        #Conexão ao Broker MQTT
        try:
            client.connect(config.MQTT_BROKER_IP, config.MQTT_PORT, 60)
            client.subscribe(config.TOPIC_SUB)
            client.on_message = on_message
            client.loop_forever()
        except Exception as e:
            print(f"Erro Conexão FED: {e}")
            self.lbl_title.config(text="ERRO DE CONEXÃO ETHERNET", fg="red")

    def start_mqtt(self):
        threading.Thread(target=self.mqtt_thread, daemon=True).start()

if __name__ == "__main__":
    root = tk.Tk()  #Cria janela
    app = DashboardApp(root)    #Inicia app
    root.mainloop() #Loop principal da UI