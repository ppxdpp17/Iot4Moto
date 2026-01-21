import requests
from flask import Flask, Response, request

# Garante que este é o URL que a UnitV2 usa para o stream MJPEG
UNITV2_STREAM_URL = "http://10.254.239.1/video_feed"
SECRET_KEY = "!ppxdpp123!"

app = Flask(__name__)

@app.route('/video')
def video_feed():
    if request.args.get('key') != SECRET_KEY:
        return "Acesso Negado", 403

    def generate():
        try:
            r = requests.get(UNITV2_STREAM_URL, stream=True, timeout=5)
            for chunk in r.iter_content(chunk_size=1024):
                yield chunk
        except Exception as e:
            print(f"Erro no stream: {e}")

    return Response(generate(), 
                    content_type='multipart/x-mixed-replace; boundary=frame')

if __name__ == '__main__':
    # host 0.0.0.0 é obrigatório para ser visto pelo telemóvel
    app.run(host='0.0.0.0', port=5000, threaded=True)
