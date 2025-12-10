#Código que vai analisar qualquer movimento no sensor
import time, math
from smbus import SMBus

#Endereço I2C
ADDR = 0x68

#Registos
PWR_MGMT_1   = 0x6B #Clock
WHO_AM_I     = 0x75 #Identificação do chip
ACCEL_XOUT_H = 0x3B #Inicio do bloco do acelerómetro
GYRO_XOUT_H  = 0x43 #Inicio do bloco do acelerómetro

#Sensibilidades
ACC_SENS = 16384.0  #LSB/g
GYR_SENS = 131.0    #LSB/(°/s)
G = 9.80665

#Estado inicial do AHRS
pitch = 0.0
roll = 0.0
yaw = 0.0
last_time = None

#Função para ler dois bytes consecutivos (alto e baixo), combinar em 16 bits
#e converter para número com sinal
def read_word(bus, addr, reg):
    hi = bus.read_byte_data(addr, reg)
    lo = bus.read_byte_data(addr, reg + 1)
    val = (hi << 8) | lo
    #Converte para signed 16-bit
    return val - 65536 if val & 0x8000 else val

#Inicia o IMU
def init_mpu(bus, dlpf=1, rate_hz=500):
    who = bus.read_byte_data(ADDR, WHO_AM_I)
    if who not in (0x68, 0x69):
        raise RuntimeError(f"MPU6050 não encontrado (WHO_AM_I=0x{who:02X})")
    bus.write_byte_data(ADDR, PWR_MGMT_1, 0x00)
    time.sleep(0.05)

    #DLPF: 0=260Hz/8kHz gyro; 1..6=184..5Hz/1kHz gyro
    dlpf = int(dlpf) & 0x07
    bus.write_byte_data(ADDR, 0x1A, dlpf)

    #Gyro ODR base
    gyro_base = 8000 if dlpf in (0, 7) else 1000
    div = max(0, int(gyro_base // rate_hz) - 1)
    bus.write_byte_data(ADDR, 0x19, div)

    time.sleep(0.05)

#Lê todos os canais em formato raw
def read_all(bus):

    #Lê 14 bytes seguidos a partir do ACCEL_XOUT_H
    data = bus.read_i2c_block_data(ADDR, ACCEL_XOUT_H, 14)

    def s16(h, l):
        v = (h << 8) | l
        return v - 65536 if v & 0x8000 else v

    #Acelerómetro
    ax = s16(data[0], data[1])
    ay = s16(data[2], data[3])
    az = s16(data[4], data[5])

    #Giroscópio
    gx = s16(data[8], data[9])
    gy = s16(data[10], data[11])
    gz = s16(data[12], data[13])

    return (ax, ay, az, gx, gy, gz)

#Converte os sinais obtidos
def convert(ax, ay, az, gx, gy, gz):
    #Acelerómetro: converte de LSB para g e depois para m/s²
    ax_g, ay_g, az_g = ax/ACC_SENS, ay/ACC_SENS, az/ACC_SENS
    ax_ms2, ay_ms2, az_ms2 = ax_g*G, ay_g*G, az_g*G

    #Giroscópio_ converte de LSB para º/s
    gx_dps, gy_dps, gz_dps = gx/GYR_SENS, gy/GYR_SENS, gz/GYR_SENS
    return (ax_ms2, ay_ms2, az_ms2, gx_dps, gy_dps, gz_dps)

#Função para estimar e remover "bias" (offset elétrico) enquanto o sensor está parado (e na horizontal)
#de modo simples. soma 500 amostras (2ms entre cada) de ACC e Gyr e faz a média
def calibrate(bus, samples=500, dt=0.002):
    print("A calibrar...")
    acc_bias = [0.0, 0.0, 0.0]
    gyr_bias = [0.0, 0.0, 0.0]
    for _ in range(samples):
        ax, ay, az, gx, gy, gz = read_all(bus)
        acc_bias[0] += ax; acc_bias[1] += ay; acc_bias[2] += az
        gyr_bias[0] += gx; gyr_bias[1] += gy; gyr_bias[2] += gz
        time.sleep(dt)
    acc_bias = [v/samples for v in acc_bias]
    gyr_bias = [v/samples for v in gyr_bias]

    #Em repouso, az ≈ +1g → remover só o offset elétrico (deixa +1g físico)
    acc_bias[2] -= ACC_SENS #Mantém leitura ~0g na vertical após correção
    #print("Bias ACC (LSB):", acc_bias, "Bias GYR (LSB):", gyr_bias)
    return acc_bias, gyr_bias

def get_sensor_data(bus, acc_bias, gyr_bias):
    global pitch, roll, yaw, last_time
    ts_ms = int(time.time() * 1000)

    if last_time is None:
        last_time = time.time()

    dt = time.time() - last_time
    last_time = time.time()

    ax, ay, az, gx, gy, gz = read_all(bus)
    ax -= acc_bias[0]; ay -= acc_bias[1]; az -= acc_bias[2]
    gx -= gyr_bias[0]; gy -= gyr_bias[1]; gz -= gyr_bias[2]
    ax_ms2, ay_ms2, az_ms2, gx_dps, gy_dps, gz_dps = convert(ax, ay, az, gx, gy, gz)

    #Cálculo do pitch/roll a partir do accel
    pitch_acc = math.degrees(math.atan2(ay_ms2, math.sqrt(ax_ms2**2 + az_ms2**2)))
    roll_acc  = math.degrees(math.atan2(-ax_ms2, az_ms2))

    #Filtro complementar
    alpha = 0.98
    pitch = alpha*(pitch + gx_dps*dt) + (1-alpha)*pitch_acc
    roll  = alpha*(roll  + gy_dps*dt) + (1-alpha)*roll_acc
    yaw   = yaw + gz_dps*dt  #yaw relativo

    return [
        ("ACEL", ts_ms, ax_ms2, ay_ms2, az_ms2),
        ("GYRO", ts_ms, gx_dps, gy_dps, gz_dps),
        ("AHRS", ts_ms, pitch, roll, yaw)
    ]
