# fcw_server.py  (PC Windows)
# Requisitos: paho-mqtt, numpy
# Run: python3 fcw_server.py

import paho.mqtt.client as mqtt
import json
import time
from collections import deque, defaultdict
import numpy as np

# --- CONFIGURAÇÃO ---
MQTT_BROKER = "127.0.0.1"  # broker local (Windows)
MQTT_PORT = 1883
TOPIC_SUB = "fcw/radar"
TOPIC_PUB_ALERT = "fcw/alerta"

CLIENT_ID = "Servidor_FCW"
WILL_TOPIC = "system/status/server"
WILL_PAYLOAD = json.dumps({"device_id": CLIENT_ID, "status": "offline", "ts_ms": int(time.time()*1000)})

# TTC thresholds (seconds)
TTC_YELLOW = 3.5
TTC_RED = 1.0

# evaluation params
HISTORY_WINDOW = 6       # number of samples to keep per sensor for velocity estimation
MIN_SAMPLES_FOR_VEL = 2
COOLDOWN_SEC = 2.0       # suppress repeated alerts within this secs per device

# store history per device (deque of (ts_ms, dist_m))
hist = defaultdict(lambda: deque(maxlen=HISTORY_WINDOW))
last_alert_time = defaultdict(lambda: 0.0)

# MQTT client
client = mqtt.Client(client_id=CLIENT_ID, clean_session=False)
client.will_set(WILL_TOPIC, WILL_PAYLOAD, qos=1, retain=True)

def safe_connect():
    backoff = 1
    while True:
        try:
            client.connect(MQTT_BROKER, MQTT_PORT, 60)
            client.loop_start()
            print(f"[mqtt] Connected to {MQTT_BROKER}:{MQTT_PORT}")
            break
        except Exception as e:
            print(f"[mqtt] Connect error: {e}. Retrying in {backoff}s")
            time.sleep(backoff)
            backoff = min(backoff*2, 30)

def estimate_velocity_m_s(deque_hist):
    # perform linear regression (distance vs time) to estimate speed (m/s)
    if len(deque_hist) < MIN_SAMPLES_FOR_VEL:
        return None
    times = np.array([t for (t, d) in deque_hist], dtype=float) / 1000.0  # seconds
    dists = np.array([d for (t, d) in deque_hist], dtype=float)         # meters
    # fit line: dist = a * t + b -> a is speed (m/s)
    # if times are identical (rare), fallback
    if np.ptp(times) == 0:
        return None
    a, b = np.polyfit(times, dists, 1)
    return float(a)  # m/s

def compute_ttc(dist_m, vel_rel_m_s):
    # If vel_rel <= 0 -> moving away or stationary => TTC infinite
    if vel_rel_m_s <= 0:
        return float('inf')
    return dist_m / vel_rel_m_s

def make_alert_payload(device_id, level, ttc, dist_m, vel_rel):
    return {
        "device_id": device_id,
        "alert_level": level,   # "YELLOW" or "RED"
        "ts_ms": int(time.time()*1000),
        "ttc_s": None if ttc == float('inf') else round(ttc, 3),
        "distance_m": round(dist_m, 3),
        "relative_speed_m_s": None if vel_rel is None else round(vel_rel, 3),
        "msg": "POTENTIAL COLLISION" if level == "YELLOW" else "IMMINENT COLLISION - BRAKE!"
    }

def on_connect(client, userdata, flags, rc):
    print("[server] Connected, subscribing to telemetry...")
    client.subscribe(TOPIC_SUB)

def on_message(client, userdata, msg):
    try:
        payload = json.loads(msg.payload.decode())
        device_id = payload.get("device_id", "unknown")
        ts_ms = int(payload.get("ts_ms", int(time.time()*1000)))
        dist_m = float(payload.get("dist_m", payload.get("dist", 0.0)))

        # update history
        dq = hist[device_id]
        dq.append((ts_ms, dist_m))

        # estimate velocity (m/s) - positive means distance increasing (object moving away)
        # we want relative closing speed v_rel = -a (if a positive means distance growing)
        a = estimate_velocity_m_s(dq)  # a = d(dist)/dt
        if a is None:
            vel_rel = None
        else:
            vel_rel = -a  # closing speed: positive if approaching (distance decreasing)

        # compute TTC
        if vel_rel is None:
            ttc = float('inf')
        else:
            ttc = compute_ttc(dist_m, vel_rel)

        # debug log
        print(f"[{device_id}] dist={dist_m:.2f} m | vel_rel={None if vel_rel is None else vel_rel:.3f} m/s | ttc={None if ttc==float('inf') else ttc:.3f}s")

        # decision logic with cooldown
        now = time.time()
        last_t = last_alert_time[device_id]
        should_send = (now - last_t) > COOLDOWN_SEC

        if should_send:
            if ttc < TTC_RED:
                alert = make_alert_payload(device_id, "RED", ttc, dist_m, vel_rel)
                client.publish(TOPIC_PUB_ALERT, json.dumps(alert, ensure_ascii=False), qos=1)
                last_alert_time[device_id] = now
                print(f">>> RED ALERT published for {device_id}: TTC={ttc:.2f}s dist={dist_m:.2f}m")
            elif ttc < TTC_YELLOW:
                alert = make_alert_payload(device_id, "YELLOW", ttc, dist_m, vel_rel)
                client.publish(TOPIC_PUB_ALERT, json.dumps(alert, ensure_ascii=False), qos=1)
                last_alert_time[device_id] = now
                print(f">>> YELLOW ALERT published for {device_id}: TTC={ttc:.2f}s dist={dist_m:.2f}m")

    except Exception as e:
        print("Error processing message:", e)

client.on_connect = on_connect
client.on_message = on_message

if __name__ == "__main__":
    safe_connect()
    try:
        print("Servidor FCW rodando. A aguardar telemetria...")
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("Servidor interrompido.")
    finally:
        try:
            client.loop_stop()
            client.disconnect()
        except:
            pass
