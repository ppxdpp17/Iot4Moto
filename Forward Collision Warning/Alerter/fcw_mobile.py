# fcw_mobile.py  (Termux / Android)
# Requisitos: pip install paho-mqtt
# Also: pkg install termux-api
# Run: python3 fcw_mobile.py

import paho.mqtt.client as mqtt
import subprocess
import sys
import time
import json
import threading

MQTT_BROKER = "10.10.9.216"   # IP do PC broker
MQTT_PORT = 1883
TOPIC_ALERT = "fcw/alerta"
TOPIC_CLEAR = "fcw/alert_clear"   # if server publishes clear messages

CLIENT_ID = "Mobile_Display"
VIBRATE_THREAD = None
VIBRATE_STOP = threading.Event()

def termux_notify(title, content, vibr_ms=1000):
    # send simple notification
    try:
        subprocess.run([
            "termux-notification",
            "--title", title,
            "--content", content,
            "--id", "999",
            "--vibrate", str(vibr_ms)
        ], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    except Exception as e:
        print("Notification error:", e)

def vibrate_loop():
    # vibrate repeatedly until VIBRATE_STOP set
    while not VIBRATE_STOP.is_set():
        try:
            subprocess.run(["termux-vibrate", "--duration", "800"], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        except Exception:
            pass
        # pause briefly
        VIBRATE_STOP.wait(timeout=0.5)

def start_continuous_vibration():
    global VIBRATE_THREAD, VIBRATE_STOP
    if VIBRATE_THREAD and VIBRATE_THREAD.is_alive():
        return
    VIBRATE_STOP.clear()
    VIBRATE_THREAD = threading.Thread(target=vibrate_loop, daemon=True)
    VIBRATE_THREAD.start()

def stop_continuous_vibration():
    global VIBRATE_THREAD, VIBRATE_STOP
    VIBRATE_STOP.set()
    if VIBRATE_THREAD:
        VIBRATE_THREAD.join(timeout=1.0)
    VIBRATE_THREAD = None

def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print("Display Conectado. A aguardar alertas.")
        client.subscribe(TOPIC_ALERT)
        client.subscribe(TOPIC_CLEAR)
    else:
        print("Erro de conexão MQTT:", rc)

def on_message(client, userdata, msg):
    topic = msg.topic
    payload_raw = msg.payload.decode(errors="ignore")
    if topic == TOPIC_CLEAR:
        # stop vibration / clear
        stop_continuous_vibration()
        print("[ALERTA] clear received.")
        return

    # expect JSON alert
    try:
        alert = json.loads(payload_raw)
    except json.JSONDecodeError:
        alert = {"msg": payload_raw}

    level = alert.get("alert_level", "YELLOW")
    msg_text = alert.get("msg", "") + f" TTC={alert.get('ttc_s')}s dist={alert.get('distance_m')}m"
    if level == "RED":
        # red: continuous vibration + prominent notification
        termux_notify("🛑 FCW - TRAVA!", msg_text, vibr_ms=1000)
        start_continuous_vibration()
    else:
        # yellow: single vib + attention
        termux_notify("⚠️ FCW - ATENÇÃO", msg_text, vibr_ms=800)
        # no continuous vibration; it vibrates once with notification

    # print summary
    print("\n" + "="*30)
    print(f"ALERTA [{level}] -> {msg_text}")
    print("="*30 + "\n")

def main():
    client = mqtt.Client(client_id=CLIENT_ID, clean_session=False)
    client.on_connect = on_connect
    client.on_message = on_message

    backoff = 1
    while True:
        try:
            print(f"Conectando ao broker {MQTT_BROKER} ...")
            client.connect(MQTT_BROKER, MQTT_PORT, 60)
            client.loop_forever()
        except KeyboardInterrupt:
            print("Termux display interrompido.")
            stop_continuous_vibration()
            break
        except Exception as e:
            print(f"MQTT error: {e}. Reconnect in {backoff}s")
            time.sleep(backoff)
            backoff = min(backoff*2, 30)

if __name__ == "__main__":
    main()
