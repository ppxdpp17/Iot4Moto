import subprocess
import sys
import os
import config

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

#Visualizaçaõ de stream em tempo real 
def open_feed():
    print(f"\n >>> A abrir stream de vídeo...")
    print(f" >>> URL: {config.VIDEO_STREAM_URL}")
    try:
        #Comando Termux para abrir URL na app padrão do Android
        subprocess.run(["termux-open-url", config.VIDEO_STREAM_URL], check=False)
    except Exception as e:
        print(f"Erro ao abrir vídeo: {e}")