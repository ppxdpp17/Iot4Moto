# /home/pi/amover_project/lib/imu_driver.py
import time
import math
from smbus import SMBus

# Registos MPU6050
ADDR = 0x68
PWR_MGMT_1 = 0x6B
ACCEL_XOUT_H = 0x3B

ACC_SENS = 16384.0 # LSB/g (Default)
G_VAL = 9.80665

def init_mpu(bus):
    try:
        bus.write_byte_data(ADDR, PWR_MGMT_1, 0x00) # Wake up
        time.sleep(0.1)
    except Exception as e:
        print(f"Erro init IMU: {e}")

def read_raw_data(bus):
    """Lê Acelerómetro e Giroscópio brutos"""
    try:
        # Lê 14 bytes (Accel + Temp + Gyro)
        data = bus.read_i2c_block_data(ADDR, ACCEL_XOUT_H, 14)
        
        def s16(h, l):
            v = (h << 8) | l
            return v - 65536 if v & 0x8000 else v

        ax = s16(data[0], data[1])
        ay = s16(data[2], data[3])
        az = s16(data[4], data[5])
        
        # Converte para 'g'
        ax_g = ax / ACC_SENS
        ay_g = ay / ACC_SENS
        az_g = az / ACC_SENS
        
        # Converte para ângulos (Roll/Pitch simplificado)
        roll = math.degrees(math.atan2(ay_g, az_g))
        pitch = math.degrees(math.atan2(-ax_g, math.sqrt(ay_g**2 + az_g**2)))
        
        # SVM (Sum Vector Magnitude) - Intensidade do impacto
        svm = math.sqrt(ax_g**2 + ay_g**2 + az_g**2) * G_VAL
        
        return {
            "ax": ax_g, "ay": ay_g, "az": az_g,
            "roll": roll, "pitch": pitch,
            "svm": svm
        }
    except:
        return None