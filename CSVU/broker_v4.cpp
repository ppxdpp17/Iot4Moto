/***************************************************************
 * broker.cpp
 * Exemplo de "lógica de broker" em C++,
 * utilizando Paho MQTT C++ e nlohmann/json.
 *
 * Para compilar (assumindo que Paho C++ e nlohmann/json
 * estão instalados em /usr/local):
 * 
 *  Executar primeiro o broker Mosquitto:
 *      sudo systemctl start mosquitto
 *
 *      g++ -std=c++17 broker_v4.cpp -o mqtt_v4 -I/usr/local/include -L/usr/local/lib -lpaho-mqttpp3 -lpaho-mqtt3as -lpthread
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
    auto last_fcw_alarm = std::chrono::steady_clock::now() - std::chrono::seconds(10);
    
    std::cout << "[CAN] A ler dados do Raspberry em can0...\n";


    struct can_frame frame{};
    while (read(s, &frame, sizeof(frame)) > 0) {
        uint32_t id = frame.can_id & CAN_SFF_MASK;

        if (id == 0x200) { 
            uint16_t speed = frame.data[0] | (frame.data[1] << 8);
            client->publish("moto/speed", std::to_string(speed));
        }
        else if (id == 0x201) {
            client->publish("moto/battery", std::to_string((int)frame.data[0]), 0, true);
        }
        else if (id == 0x202) {
            std::string gear = (frame.data[0] == 1) ? "F" : (frame.data[0] == 2) ? "R" : (frame.data[0] == 3) ? "P" : "N";
            client->publish("moto/gear", gear);
        }
        else if (id == 0x203) {
            std::string mode = (frame.data[0] == 1) ? "ECO" : (frame.data[0] == 2) ? "SPORT" : "STD";
            client->publish("moto/drive_mode", mode);
        }
        else if (id == 0x204) {
            uint8_t mask = frame.data[0];
            client->publish("moto/lights",          (mask & 0x01) ? "true" : "false");
            client->publish("moto/maxlights",       (mask & 0x02) ? "true" : "false");
            client->publish("moto/indicator_left",  (mask & 0x04) ? "true" : "false");
            client->publish("moto/indicator_right", (mask & 0x08) ? "true" : "false");
            client->publish("moto/danger-lights",   (mask & 0x10) ? "true" : "false");
        }
        else if (id == 0x205) {
            client->publish("moto/brake", (frame.data[0] == 1) ? "true" : "false");
        }
        else if (id == 0x206) {
            client->publish("moto/abs", (frame.data[0] == 1) ? "true" : "false");
        }
        else if (id == 0x207) {
            client->publish("moto/temperature", std::to_string((int)frame.data[0]));
        }
        else if (id == 0x208) {
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
        else if (id == 0x209) {
            uint32_t km = frame.data[0] | (frame.data[1] << 8) | (frame.data[2] << 16) | (frame.data[3] << 24);
            client->publish("moto/total_kilometers", std::to_string(km), 0, true);
        }
        else if (id == 0x210) {
            uint8_t horas = frame.data[0];
            uint8_t minutos = frame.data[1];
            char timeBuffer[6];
            snprintf(timeBuffer, sizeof(timeBuffer), "%02d:%02d", horas, minutos);
            client->publish("moto/time", std::string(timeBuffer), 0, true);
        }
        else if (id == 0x301) {
            //Reconstrói a Latitude e Longitude
            int32_t lat_raw = frame.data[0] | (frame.data[1] << 8) | (frame.data[2] << 16) | (frame.data[3] << 24);
            int32_t lon_raw = frame.data[4] | (frame.data[5] << 8) | (frame.data[6] << 16) | (frame.data[7] << 24);

            double lat = lat_raw / 1000000.0;
            double lon = lon_raw / 1000000.0;

            //Criar JSON único com latitude + longitude
            json gpsPayload;
            gpsPayload["lat"] = lat;
            gpsPayload["lon"] = lon;
            
            client->publish("moto/gps", gpsPayload.dump());
        }
        else if (id == 0x302) {
            //Desempacota os bytes
            uint16_t dist_cm = frame.data[0] | (frame.data[1] << 8);
            uint8_t ttc_ds = frame.data[2];
            uint8_t type_byte = frame.data[3];

            //Reconstrói os valores reais
            double dist_m = dist_cm / 100.0;
            double ttc_s = ttc_ds / 10.0;
            std::string type_str = (type_byte == 2) ? "CRITICAL" : (type_byte == 1) ? "WARNING" : "INFO";

            //Monta o JSON para a Plataforma Web
            json fcwPayload;
            fcwPayload["dist"] = dist_m;
            fcwPayload["ttc"] = ttc_s;
            fcwPayload["type"] = type_str;

            client->publish("moto/fcw", fcwPayload.dump());

            //Se for crítico, dispara o alarme genérico para a App Android te notificar
            if (type_byte == 2) {
                auto now = std::chrono::steady_clock::now();
                
                // Só publica no MQTT se já passaram 5 segundos desde o último alarme
                if (std::chrono::duration_cast<std::chrono::seconds>(now - last_fcw_alarm).count() >= 5) {
                    last_fcw_alarm = now; //Atualiza o relógio
                    client->publish("moto/alarm", "FCW");
                }
            }
        }
        else if (id == 0x401) {
            if (frame.data[0] == 0x01) {
                //std::cout << "[CSVU] Chuva detetada via CAN. A avisar proprietário..." << std::endl;
                client->publish("moto/alarm", "RAIN");
            }
        }
    }
    return nullptr;
}

int main() {
    const std::string address = "tcp://127.0.0.1:1883";
    const std::string clientId = "CppBroker_V4";

    std::cout << "A Iniciar Motor C++ V4" << std::endl;
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
