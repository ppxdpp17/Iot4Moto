# /home/pi/amover_project/lib/ultrasonic_driver.py
import RPi.GPIO as GPIO
import time

def setup_gpio(trig, echo):
    GPIO.setmode(GPIO.BCM)
    GPIO.setup(trig, GPIO.OUT)
    GPIO.setup(echo, GPIO.IN)
    GPIO.output(trig, False)

#Lê a distância em metros a usar o HC-SR04
def read_distance(trig, echo):
    """Lê distância em Metros"""
    try:
        GPIO.output(trig, True)
        time.sleep(0.00001)
        GPIO.output(trig, False)
        
        start = time.time()
        timeout = start + 0.04
        
        while GPIO.input(echo) == 0:
            start = time.time()
            if start > timeout: return None
            
        pulse_end = time.time()
        while GPIO.input(echo) == 1:
            pulse_end = time.time()
            if pulse_end > timeout: return None
            
        duration = pulse_end - start
        dist_m = (duration * 343.0) / 2.0
        
        if 0.02 < dist_m < 4.0:
            return dist_m
            
    except: pass
    return None