/***************************************************************
 * broker.cpp
 * Exemplo de "lógica de broker" em C++,
 * utilizando Paho MQTT C++ e nlohmann/json.
 *
 * Para compilar (assumindo que Paho C++ e nlohmann/json
 * estão instalados em /usr/local):
 *
 *   g++ -std=c++17 main.cpp -o mqtt_v4 \
 *       -I/usr/local/include -L/usr/local/lib \
 *       -lpaho-mqttpp3 -lpaho-mqtt3as
 *
 * Executar primeiro o broker Mosquitto:
 *   sudo systemctl start mosquitto
 *
 * Depois executar:
 *   ./mqtt_v4
 *
 ***************************************************************/

#include <iostream>
#include <string>
#include <vector>
#include <chrono>
#include <iomanip>
#include <unistd.h>
#include <cstring>
#include <linux/can.h>
#include <linux/can/raw.h>
#include <sys/socket.h>
#include <net/if.h>
#include <sys/ioctl.h>
#include <pthread.h>
#include "mqtt/async_client.h"
#include <nlohmann/json.hpp>

using json = nlohmann::json;

//Thread que lê o CAN físico vindo do Raspberry
void* can_reader(void* arg) {
    auto* client = static_cast<mqtt::async_client*>(arg);
    int s = socket(PF_CAN, SOCK_RAW, CAN_RAW);
    struct ifreq ifr{};
    strcpy(ifr.ifr_name, "can0");
    ioctl(s, SIOCGIFINDEX, &ifr);
    struct sockaddr_can addr{};
    addr.can_family = AF_CAN;
    addr.can_ifindex = ifr.ifr_ifindex;
    bind(s, (struct sockaddr*)&addr, sizeof(addr));
    
    std::cout << "[CAN] A ler dados reais do Raspberry em can0...\n";


    struct can_frame frame{};
    while (read(s, &frame, sizeof(frame)) > 0) {
        uint32_t id = frame.can_id & CAN_SFF_MASK;

        if (id == 0x200) { 
            //Byte 0 e 1: Velocidade (uint16)
            uint16_t speed = frame.data[0] | (frame.data[1] << 8);
            //Byte 2: Bateria (uint8)
            uint8_t battery = frame.data[2];
            //Byte 3: Travão (bool)
            std::string brake = (frame.data[3] == 1) ? "true" : "false";

            client->publish("moto/speed", std::to_string(speed));
            client->publish("moto/battery", std::to_string((int)battery), 0, true);
            client->publish("moto/brake", brake);
        }
        else if (id == 0x202) { //GEAR
            std::string gear = (frame.data[0] == 1) ? "F" : (frame.data[0] == 2) ? "R" : (frame.data[0] == 3) ? "P" : "N";
            client->publish("moto/gear", gear);
        }
        else if (id == 0x203) { //DRIVE MODE
            std::string mode = (frame.data[0] == 1) ? "ECO" : (frame.data[0] == 2) ? "SPORT" : "STD";
            client->publish("moto/drive_mode", mode);
        }
        else if (id == 0x204) { //LUZES (Bitmask)
            uint8_t mask = frame.data[0];
            client->publish("moto/lights",          (mask & 0x01) ? "true" : "false");
            client->publish("moto/maxlights",       (mask & 0x02) ? "true" : "false");
            client->publish("moto/indicator_left",  (mask & 0x04) ? "true" : "false");
            client->publish("moto/indicator_right", (mask & 0x08) ? "true" : "false");
            client->publish("moto/danger-lights",   (mask & 0x10) ? "true" : "false");
        }
        else if (id == 0x206) { //ABS
            client->publish("moto/abs", (frame.data[0] == 1) ? "true" : "false");
        }
        else if (id == 0x207) { //TEMPERATURE
            client->publish("moto/temperature", std::to_string((int)frame.data[0]));
        }
        else if (id == 0x208) { //AUTONOMIA (Range)
            uint16_t range = frame.data[0]; 
            client->publish("moto/range", std::to_string(range), 0, true);
        }
        else if (id == 0x300) { 
            //Recuperar o int16 (multiplicado por 100 no RPi)
            int16_t roll_raw = frame.data[0] | (frame.data[1] << 8);
            float roll = roll_raw / 100.0f;

            //Publicar com precisão de 2 casas decimais para a Web
            std::stringstream ss;
            ss << std::fixed << std::setprecision(2) << roll;
            client->publish("moto/roll", ss.str());
        }
        else if (id == 0x400) {
            std::cout << "\a!!ALERTA DE MOVIMENTO NA MOTA!!" << std::endl;
            client->publish("moto/alarm", "ALERTA_MOVIMENTO");
        }
    }
    return nullptr;
}

int main() {
    const std::string address = "tcp://127.0.0.1:1884";
    const std::string clientId = "CppBroker_V4";

    std::cout << "Iniciando Motor C++ V3 (VOID Edition)" << std::endl;
    mqtt::async_client client(address, clientId);
    mqtt::connect_options connOpts;
    connOpts.set_clean_session(true);

    try {
        client.connect(connOpts)->wait();
        std::cout << "Ligado ao Broker Local.\n";

        pthread_t can_tid;
        pthread_create(&can_tid, nullptr, can_reader, &client);

        while(true) { std::this_thread::sleep_for(std::chrono::seconds(1)); }
    }
    catch(const mqtt::exception &ex) {
        std::cerr << "Erro MQTT: " << ex.what() << std::endl;
        return 1;
    }
    return 0;
}
