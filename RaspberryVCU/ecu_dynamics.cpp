#include <iostream>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <linux/i2c-dev.h>
#include <linux/can.h>
#include <linux/can/raw.h>
#include <net/if.h>
#include <cmath>
#include <cstring>

#define MPU6050_ADDR 0x68
#define ALPHA 0.98f
#define DT 0.01f //10ms loop (100Hz)

class MPU6050 {
    int file;
public:
    MPU6050() {
        file = open("/dev/i2c-1", O_RDWR);
        ioctl(file, I2C_SLAVE, MPU6050_ADDR);
        //Acorda o sensor MPU6050
        unsigned char buf[2] = {0x6B, 0x00};
        write(file, buf, 2);
    }

    void read_raw(short &ax, short &ay, short &az, short &gx, short &gy, short &gz) {
        unsigned char reg = 0x3B;
        unsigned char data[14];
        write(file, &reg, 1);
        read(file, data, 14);

        ax = (data[0] << 8) | data[1];
        ay = (data[2] << 8) | data[3];
        az = (data[4] << 8) | data[5];
        //data[6,7] é a temperatura (ignora-se)
        gx = (data[8] << 8) | data[9];
        gy = (data[10] << 8) | data[11];
        gz = (data[12] << 8) | data[13];
    }
};

int main() {
    //Setup CAN
    int s = socket(PF_CAN, SOCK_RAW, CAN_RAW);
    struct ifreq ifr;
    strcpy(ifr.ifr_name, "can0"); //simplyCAN está na interface can0
    ioctl(s, SIOCGIFINDEX, &ifr);
    struct sockaddr_can addr = {AF_CAN, ifr.ifr_ifindex};
    bind(s, (struct sockaddr *)&addr, sizeof(addr));

    MPU6050 imu;
    float roll = 0, pitch = 0;
    short ax, ay, az, gx, gy, gz;

    //std::cout << "[ECU_DYNAMICS] Raspberry Pi 5 a processar IMU..." << std::endl;

    while (true) {
        imu.read_raw(ax, ay, az, gx, gy, gz);

        //Converter para unidades físicas (considerando default de +-2g e +-250deg/s)
        float acc_roll = atan2(ay, az) * 180 / M_PI;
        float gyro_roll_rate = gx / 131.0f;

        //Filtro Complementar
        roll = ALPHA * (roll + gyro_roll_rate * DT) + (1.0f - ALPHA) * acc_roll;

        //Preparar Frame CAN (ID 0x300)
        struct can_frame frame;
        frame.can_id = 0x300;
        frame.can_dlc = 4;
        
        //Converter float para int16 (multiplicado por 100 para manter 2 casas decimais)
        int16_t roll_int = (int16_t)(roll * 100);
        
        frame.data[0] = roll_int & 0xFF;
        frame.data[1] = (roll_int >> 8) & 0xFF;
        
        //Enviar para o barramento
        write(s, &frame, sizeof(struct can_frame));

        usleep(10000); //10ms
    }
    return 0;
}