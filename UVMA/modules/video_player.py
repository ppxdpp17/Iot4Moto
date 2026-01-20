import subprocess
import sys
import os
import requests  # <--- Instala isto no Termux: pip install requests
import config

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

def get_smart_video_url():
    return config.VIDEO_URL_LOCAL

# Visualização de stream em tempo real 
def open_feed():
    print(f"\n >>> A abrir stream de vídeo...")
    
    try:
        # 1. Escolhe o melhor link para este momento
        final_url = get_smart_video_url()
        
        print(f" >>> A carregar: {final_url}")

        # 2. Abre o URL escolhido
        subprocess.run(["termux-open-url", final_url], check=False)
        
    except Exception as e:
        print(f"Erro ao abrir vídeo: {e}")