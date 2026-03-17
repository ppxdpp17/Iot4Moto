import cv2
from flask import Flask, Response
from picamera2 import Picamera2

app = Flask(__name__)

#Liga a câmara diretamente pelo hardware
picam2 = Picamera2()
config = picam2.create_video_configuration(main={"format": "RGB888", "size": (640, 480)})
picam2.configure(config)
picam2.start()

def gen_frames():
    while True:
        try:
            #Captura a imagem diretamente da memória do Pi 5
            frame = picam2.capture_array()
            
            #O OpenCV lê em BGR, a câmara capta em RGB - inverte-se as cores
            frame_bgr = cv2.cvtColor(frame, cv2.COLOR_RGB2BGR)
            
            #Codifica e envia
            ret, buffer = cv2.imencode('.jpg', frame_bgr)
            frame_bytes = buffer.tobytes()
            
            yield (b'--frame\r\n'
                   b'Content-Type: image/jpeg\r\n\r\n' + frame_bytes + b'\r\n')
        except Exception as e:
            print(f"Erro a ler a frame: {e}")
            break

@app.route('/stream')
def video_feed():
    return Response(gen_frames(), mimetype='multipart/x-mixed-replace; boundary=frame')

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, threaded=True)