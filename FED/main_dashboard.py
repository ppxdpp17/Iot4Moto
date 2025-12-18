# C:\Users\Pedro\Projetos\AMOVER_FED\main_dashboard.py
import tkinter as tk
from tkinter import font
import paho.mqtt.client as mqtt
import json
import threading
import config # Importa o ficheiro acima

class DashboardApp:
    def __init__(self, root):
        self.root = root
        self.root.title("A-MOVER FED Interface")
        self.root.geometry(config.WINDOW_SIZE)
        self.root.configure(bg=config.BG_COLOR)
        
        # --- UI LAYOUT ---
        # Cabeçalho
        self.lbl_title = tk.Label(root, text="AMOVER SYSTEM READY", font=("Arial", 14), 
                                  bg=config.BG_COLOR, fg="#888888")
        self.lbl_title.pack(pady=10)

        # Área Central (Velocidade e Alertas)
        self.frame_center = tk.Frame(root, bg=config.BG_COLOR)
        self.frame_center.pack(expand=True)

        self.lbl_speed_val = tk.Label(self.frame_center, text="0", font=("Helvetica", 80, "bold"), 
                                      bg=config.BG_COLOR, fg=config.TEXT_COLOR)
        self.lbl_speed_val.pack()
        
        self.lbl_speed_unit = tk.Label(self.frame_center, text="km/h", font=("Arial", 16), 
                                       bg=config.BG_COLOR, fg="#AAAAAA")
        self.lbl_speed_unit.pack()

        # Área de Alertas (Inicialmente vazia)
        self.lbl_alert = tk.Label(root, text="", font=("Arial", 24, "bold"), 
                                  bg=config.BG_COLOR, fg=config.ALERT_COLOR_GREEN)
        self.lbl_alert.pack(pady=20, fill='x')

        # Rodapé (Telemetria Técnica)
        self.frame_footer = tk.Frame(root, bg="#1E1E1E", height=40)
        self.frame_footer.pack(fill='x', side='bottom')
        
        self.lbl_telemetry = tk.Label(self.frame_footer, text="Roll: 0.0° | Pitch: 0.0° | SVM: 1.0g", 
                                      font=("Consolas", 10), bg="#1E1E1E", fg="#00FF00")
        self.lbl_telemetry.pack(pady=10)

        # --- MQTT START ---
        self.start_mqtt()

    def update_ui(self, data, topic):
        # Atualiza a interface baseado no tópico
        if "fcw" in topic:
            # Forward Collision Warning
            dist = data.get("dist", 0)
            ttc = data.get("ttc", 99)
            tipo = data.get("type", "INFO")
            
            if tipo == "ACCIDENT": # Colisão Iminente
                self.root.configure(bg=config.ALERT_COLOR_RED)
                self.lbl_alert.config(text=f"!!!TRAVAR AGORA!!!\n{dist}m", bg=config.ALERT_COLOR_RED, fg="white")
            elif tipo == "WARNING":
                self.lbl_alert.config(text=f"->APROXIMAÇÃO ({dist}m!!!)", fg=config.ALERT_COLOR_YELLOW, bg=config.BG_COLOR)
                self.root.configure(bg=config.BG_COLOR)
            else:
                self.lbl_alert.config(text="", bg=config.BG_COLOR)
                self.root.configure(bg=config.BG_COLOR)

        elif "accident" in topic:
            # Deteção de Queda (Accident Warning)
            if data.get("type") == "ACCIDENT":
                self.lbl_alert.config(text="QUEDA DETETADA \nSOS ENVIADO", fg="white", bg="red")
                self.root.configure(bg="red")
            elif data.get("type") == "CANCEL":
                self.lbl_alert.config(text="SISTEMA NORMAL", fg=config.ALERT_COLOR_GREEN, bg=config.BG_COLOR)
                self.root.configure(bg=config.BG_COLOR)

        elif "telemetry" in topic:
            # Dados do IMU
            r = data.get("roll", 0)
            p = data.get("pitch", 0)
            svm = data.get("svm", 0) # Assumindo que vem SVM
            self.lbl_telemetry.config(text=f"Roll: {r}° | Pitch: {p}° | G-Force: {svm}g")

    def mqtt_thread(self):
        client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1, "FED_Windows_GUI")
        
        def on_message(client, userdata, msg):
            try:
                payload = json.loads(msg.payload.decode())
                # Usa .after para thread safety no Tkinter
                self.root.after(0, self.update_ui, payload, msg.topic)
            except: pass

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
    root = tk.Tk()
    app = DashboardApp(root)
    root.mainloop()