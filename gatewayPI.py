#!/usr/bin/env python3
import sensor_handler  # Mantemos o import do sensor_handler
from smbus import SMBus
import time

def main():
    # 1. Inicialização e Calibração
    bus = SMBus(1)
    sensor_handler.init_mpu(bus, dlpf=1, rate_hz=500)
    acc_bias, gyr_bias = sensor_handler.calibrate(bus)
    
    # 2. Configuração do Loop
    target_hz = 10  # Reduzimos a frequência para 10Hz para que a consola consiga acompanhar
    period = 1.0/target_hz
    next_t = time.perf_counter()
    print("-" * 50)
    print(f"A RECOLHER E IMPRIMIR DADOS AHRS (a {target_hz} Hz)")
    print("-" * 50)

    try:
        while True:
            # 3. Ler e Calcular os Dados
            # A função get_sensor_data retorna uma lista de tuplos:
            # [('ACEL', ...), ('GYRO', ...), ('AHRS', ts, pitch, roll, yaw)]
            rows = sensor_handler.get_sensor_data(bus, acc_bias, gyr_bias)
            
            # 4. Processar e Imprimir
            for sensor, ts, x, y, z in rows:
                if sensor == "AHRS":
                    # Imprimir apenas os dados AHRS (Pitch, Roll, Yaw)
                    print(f"AHRS (ms: {ts}): Pitch={x:.2f}º | Roll={y:.2f}º | Yaw={z:.2f}º")
                # Se quisesse imprimir o acelerómetro:
                # elif sensor == "ACEL":
                #     print(f"ACEL: X={x:.2f} m/s² | Y={y:.2f} m/s² | Z={z:.2f} m/s²")

            # 5. Agendamento
            next_t += period
            delay = next_t - time.perf_counter()
            if delay > 0:
                time.sleep(delay)
            else:
                next_t = time.perf_counter() # Re-sincroniza se perder o deadline
                
    except KeyboardInterrupt:
        print("\nRecolha de dados encerrada pelo utilizador.")
    finally:
        bus.close()

if __name__ == "__main__":
    main()
