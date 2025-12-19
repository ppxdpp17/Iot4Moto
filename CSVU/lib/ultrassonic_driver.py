import RPi.GPIO as GPIO
import time

#Configuração de GPIO
def setup_gpio(trig, echo):
    GPIO.setmode(GPIO.BCM)
    GPIO.setup(trig, GPIO.OUT)
    GPIO.setup(echo, GPIO.IN)
    GPIO.output(trig, False)

#Lê a distância em metros a usar o HC-SR04
def read_distance(trig, echo):
    try:
        #Envia um pulso ultrassónico
        GPIO.output(trig, True)
        time.sleep(0.00001)
        GPIO.output(trig, False)
        
        #Mede o tempo até receber o eco
        start = time.time()
        timeout = start + 0.04
        
        #Fica no loop até o sinal de eco ser recebido
        while GPIO.input(echo) == 0:
            start = time.time()
            if start > timeout: return None
            
        #Espera pelo fim do pulso de eco
        pulse_end = time.time()
        while GPIO.input(echo) == 1:
            pulse_end = time.time()
            if pulse_end > timeout: return None
            
        duration = pulse_end - start

        #Calcula a distância (velocidade do som = 343 m/s)
        dist_m = (duration * 343.0) / 2.0
        
        #Limites do HC-SR04
        if 0.02 < dist_m < 4.0:
            return dist_m
            
    except: pass
    return None