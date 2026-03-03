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
#include <vector>

#define MPU6050_ADDR 0x68
#define THRESHOLD_ACCEL 2500  //Sensibilidade ao movimento
#define THRESHOLD_GYRO 1500   //Sensibilidade à rotação
#define SAMPLES 5

class Sentinel {
    int i2c_file;
    int can_sock;
public:
    Sentinel() {
        //Setup I2C
        i2c_file = open("/dev/i2c-1", O_RDWR);
        ioctl(i2c_file, I2C_SLAVE, MPU6050_ADDR);
        unsigned char wake[] = {0x6B, 0x00};
        write(i2c_file, wake, 2);

        //Setup CAN
        can_sock = socket(PF_CAN, SOCK_RAW, CAN_RAW);
        struct ifreq ifr;
        strcpy(ifr.ifr_name, "can0");
        ioctl(can_sock, SIOCGIFINDEX, &ifr);
        struct sockaddr_can addr = {AF_CAN, ifr.ifr_ifindex};
        bind(can_sock, (struct sockaddr *)&addr, sizeof(addr));
    }

    void send_alert() {
        struct can_frame frame;
        frame.can_id = 0x400; //ID de Alerta definido no Broker
        frame.can_dlc = 1;
        frame.data[0] = 0xFF; //Código de alarme
        write(can_sock, &frame, sizeof(struct can_frame));
        std::cout << "[SENTINEL] !!! MOVIMENTO DETETADO - ALERTA ENVIADO !!!" << std::endl;
    }

    void monitor() {
        short ax, ay, az, gx, gy, gz;
        unsigned char reg = 0x3B;
        unsigned char data[14];
        
        //Valores de referência
        short last_ax = 0, last_ay = 0, last_az = 0;

        //std::cout << "[SENTINEL] Modo de Segurança Ativo. A monitorizar..." << std::endl;

        while (true) {
            write(i2c_file, &reg, 1);
            read(i2c_file, data, 14);

            ax = (data[0] << 8) | data[1];
            ay = (data[2] << 8) | data[3];
            az = (data[4] << 8) | data[5];
            gx = (data[8] << 8) | data[9];
            gy = (data[10] << 8) | data[11];
            gz = (data[12] << 8) | data[13];

            //Lógica de deteção: variação brusca entre leituras
            if (last_ax != 0) {
                if (abs(ax - last_ax) > THRESHOLD_ACCEL || 
                    abs(ay - last_ay) > THRESHOLD_ACCEL ||
                    abs(gx) > THRESHOLD_GYRO) {
                    send_alert();
                    sleep(5); //Cooldown para evitar spam
                }
            }

            last_ax = ax; last_ay = ay; last_az = az;
            usleep(200000); //5Hz é suficiente para poupar energia e detetar furtos
        }
    }
};

int main() {
    Sentinel sentinel;
    sentinel.monitor();
    return 0;
}