# /home/pi/amover_project/services/svc_video.py
from flask import Flask, Response
import requests
import sys
import os

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import config

app = Flask(__name__)

def generate_frames():
    try:
        r = requests.get(config.UNITV2_STREAM, stream=True, timeout=5)
        for chunk in r.iter_content(chunk_size=1024):
            if chunk: yield chunk
    except: pass

@app.route('/video')
def video_feed():
    return Response(generate_frames(), mimetype='multipart/x-mixed-replace; boundary=frame')

if __name__ == '__main__':
    print("📹 Servidor de Vídeo a correr na porta 5000")
    app.run(host='0.0.0.0', port=5000, threaded=True)