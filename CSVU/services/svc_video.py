import sys
import os
import requests
from flask import Flask, Response, stream_with_context, request

#Configurações
UNITV2_URL = "http://10.254.239.1/video_feed"
SECRET_KEY = "!ppxdpp123!" #Security Key

app = Flask(__name__)

def get_stream():
    try:
        req = requests.get(UNITV2_URL, stream=True, timeout=3)
        return Response(stream_with_context(req.iter_content(chunk_size=1024)), 
                        content_type=req.headers['content-type'])
    except Exception as e:
        print(f"ERRO CAMARA: {e}")
        return f"Erro: {e}", 503

@app.route('/')
@app.route('/video')
@app.route('/video_feed')
def video_feed():
    #---BLOCO DE SEGURANÇA---
    # Verifica se o link tem key
    token_recebido = request.args.get('key')

    if token_recebido != SECRET_KEY:
        print(f"!!! ACESSO NEGADO: Alguém tentou entrar sem chave !!!")
        return "ACESSO NEGADO (Chave Invalida)", 403
    # --------------------------

    print(f"-> Acesso autorizado. A ligar à câmara...")
    return get_stream()

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, threaded=True)