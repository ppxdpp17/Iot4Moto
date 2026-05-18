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
#include <mutex>
#include "mqtt/async_client.h"
#include <nlohmann/json.hpp>
#include <thread>

using json = nlohmann::json;

//ESTRUTURA GLOBAL DO ESTADO DA MOTA ("Digital TWIN")
struct MotoState {
    double lat = 0.0;
    double lon = 0.0;
    int soc = 0;
    int range = 0;
    int speed = 0;
    bool ignition = true; //Assume true se estiver a emitir CAN
    std::string alert = "NONE";
    double roll = 0.0;
    double fcw_dist = 0.0;
    double fcw_ttc = 99.9;
};

MotoState currentState;
std::mutex stateMutex; //Impede que a thread da Cloud leia a estrutura ao mesmo tempo que a thread do CAN a está a reescrever (para evitar dados corrompidos)

//THREAD DO CAN (Atualiza o Estado e o Broker Local)
void* can_reader(void* arg) {
    auto* client = static_cast<mqtt::async_client*>(arg);

    //Configuração do Socket Raw CAN do Linux (SocketCAN)
    //Permite interagir com o barramento do veículo como se fosse uma interface de rede normal
    int s = socket(PF_CAN, SOCK_RAW, CAN_RAW);
    struct ifreq ifr{};
    strcpy(ifr.ifr_name, "can0");
    ioctl(s, SIOCGIFINDEX, &ifr);
    struct sockaddr_can addr{};
    addr.can_family = AF_CAN;
    addr.can_ifindex = ifr.ifr_ifindex;
    bind(s, (struct sockaddr*)&addr, sizeof(addr));

    auto last_fcw_alarm = std::chrono::steady_clock::now() - std::chrono::seconds(10);  //Para não mandar alertas constantemente
    
    std::cout << "[CAN] A ler dados do Raspberry em can0...\n";

    struct can_frame frame{};
    //Loop que fica à escuta de novos frames no cabo CAN
    while (read(s, &frame, sizeof(frame)) > 0) {
        uint32_t id = frame.can_id & CAN_SFF_MASK;

        //TRADUÇÃO CAN -> MQTT (LOCAL)
        //Cada ID corresponde a uma ECU simulada. O Gateway descodifica o payload hexadecimal.
        if (id == 0x200) {  //Velocidade 
            uint16_t speed = frame.data[0] | (frame.data[1] << 8);
            {
                std::lock_guard<std::mutex> lock(stateMutex);
                currentState.speed = speed;
            }
            client->publish("moto/speed", std::to_string(speed));   //Publica para o FED (local)
        }
        else if (id == 0x201) { //Bateria
            {
                std::lock_guard<std::mutex> lock(stateMutex);
                currentState.soc = (int)frame.data[0];
            }
            client->publish("moto/battery", std::to_string(currentState.soc), 0, true);
        }
        else if (id == 0x202) { //Mudança
            std::string gear = (frame.data[0] == 1) ? "F" : (frame.data[0] == 2) ? "R" : (frame.data[0] == 3) ? "P" : "N";
            client->publish("moto/gear", gear);
        }
        else if (id == 0x203) { //Modo de condução
            std::string mode = (frame.data[0] == 1) ? "ECO" : (frame.data[0] == 2) ? "SPORT" : "STD";
            client->publish("moto/drive_mode", mode);
        }   
        else if (id == 0x204) { //Luzes
            uint8_t mask = frame.data[0];
            client->publish("moto/lights",          (mask & 0x01) ? "true" : "false");
            client->publish("moto/maxlights",       (mask & 0x02) ? "true" : "false");
            client->publish("moto/indicator_left",  (mask & 0x04) ? "true" : "false");
            client->publish("moto/indicator_right", (mask & 0x08) ? "true" : "false");
            client->publish("moto/danger-lights",   (mask & 0x10) ? "true" : "false");
        }
        else if (id == 0x205) { //Travão
            client->publish("moto/brake", (frame.data[0] == 1) ? "true" : "false");
        }
        else if (id == 0x206) { //ABS
            client->publish("moto/abs", (frame.data[0] == 1) ? "true" : "false");
        }
        else if (id == 0x207) { //Temperatura
            client->publish("moto/temperature", std::to_string((int)frame.data[0]));
        }
        else if (id == 0x208) { //Autonomia
            {
                std::lock_guard<std::mutex> lock(stateMutex);
                currentState.range = frame.data[0];
            } 
            client->publish("moto/range", std::to_string(currentState.range), 0, true);
        }
        else if (id == 0x400) { //Alerta de Roubo
            {
                std::lock_guard<std::mutex> lock(stateMutex);
                currentState.alert = "THEFT";
            }
            client->publish("moto/alarm", "THEFT");
        }
        else if (id == 0x402) { //Alerta de Queda (FALL)
            {
                std::lock_guard<std::mutex> lock(stateMutex);
                currentState.alert = "FALL"; 
            }
            client->publish("moto/alarm", "FALL");
        }
        else if (id == 0x209) { //Total de Kilómetros Percorridos
            uint32_t km = frame.data[0] | (frame.data[1] << 8) | (frame.data[2] << 16) | (frame.data[3] << 24);
            client->publish("moto/total_kilometers", std::to_string(km), 0, true);
        }
        else if (id == 0x210) { //Tempo (horas minutos)
            uint8_t horas = frame.data[0];
            uint8_t minutos = frame.data[1];
            char timeBuffer[6];
            snprintf(timeBuffer, sizeof(timeBuffer), "%02d:%02d", horas, minutos);
            client->publish("moto/time", std::string(timeBuffer), 0, true);
        }
        else if (id == 0x301) { //Coordenadas GPS
            //Reconstrói a Latitude e Longitude
            int32_t lat_raw = frame.data[0] | (frame.data[1] << 8) | (frame.data[2] << 16) | (frame.data[3] << 24);
            int32_t lon_raw = frame.data[4] | (frame.data[5] << 8) | (frame.data[6] << 16) | (frame.data[7] << 24);

            //Divide por 1.000.000 (porque do outro lado foi multiplicado por 1.000.000 porque não aceita doubles, apenas int)
            double lat = lat_raw / 1000000.0;
            double lon = lon_raw / 1000000.0;

            //Criar JSON único com latitude + longitude
            json gpsPayload;
            gpsPayload["lat"] = lat;
            gpsPayload["lon"] = lon;
            
            client->publish("moto/gps", gpsPayload.dump());
            {
                std::lock_guard<std::mutex> lock(stateMutex);
                currentState.lat = lat;
                currentState.lon = lon;            
            }

        }
        else if (id == 0x302) { //Dados do Sensor Ultrassom (FCW)
            uint16_t dist_cm = frame.data[0] | (frame.data[1] << 8);
            uint8_t ttc_ds = frame.data[2];
            uint8_t type_byte = frame.data[3];

            double dist_m = dist_cm / 100.0;
            double ttc_s = ttc_ds / 10.0;
            std::string type_str = (type_byte == 2) ? "CRITICAL" : (type_byte == 1) ? "WARNING" : "INFO";

            json fcwPayload;
            fcwPayload["dist"] = dist_m;
            fcwPayload["ttc"] = ttc_s;
            fcwPayload["type"] = type_str;

            client->publish("moto/fcw", fcwPayload.dump());

            //BLOQUEIO ÚNICO DE MEMÓRIA PARA ESTE EVENTO CAN
            {
                std::lock_guard<std::mutex> lock(stateMutex);
                
                //Grava os valores contínuos de distância e time to collision
                currentState.fcw_dist = dist_m;
                currentState.fcw_ttc = ttc_s;

                //Se houver perigo crítico, grava também o alerta
                if (type_byte == 2) {
                    auto now = std::chrono::steady_clock::now();
                    if (std::chrono::duration_cast<std::chrono::seconds>(now - last_fcw_alarm).count() >= 5) {
                        last_fcw_alarm = now;
                        client->publish("moto/alarm", "FCW");
                        
                        currentState.alert = "FCW"; //Atualizado sem criar outro lock
                    }
                }
            }
        }
        else if (id == 0x303) { //IMU - Ângulo de Inclinação (Roll)
            //Lemos 2 bytes como um inteiro com sinal (int16_t) para permitir ângulos negativos
            int16_t raw_roll = frame.data[0] | (frame.data[1] << 8);

            //Divide por 100.0 para restaurar as casas decimais
            double roll_deg = raw_roll / 100.0;

            {
                std::lock_guard<std::mutex> lock(stateMutex);
                currentState.roll = roll_deg;
            }
            client->publish("moto/roll", std::to_string(roll_deg));
        }
        else if (id == 0x401) { //Alerta de Chuva
            if (frame.data[0] == 0x01) {
                client->publish("moto/alarm", "RAIN"); //Tablet local
                
                //Guarda na memória para a Cloud ler
                std::lock_guard<std::mutex> lock(stateMutex);
                currentState.alert = "RAIN";
            } 
            else if (frame.data[0] == 0x00) {
                client->publish("moto/alarm", "RAIN_CLEARED"); //Tablet local
                
                //Atualiza a memória para a Cloud avisar que parou
                std::lock_guard<std::mutex> lock(stateMutex);
                currentState.alert = "RAIN_CLEARED";
            }
        }
        else if (id == 0x106) { //Estado da Ignição
            bool motorLigado = (frame.data[0] == 0x01);
            {
                std::lock_guard<std::mutex> lock(stateMutex);
                currentState.ignition = motorLigado;
                if (!motorLigado) currentState.speed = 0;
            }
        }
    }
    return nullptr;
}

//THREAD DA CLOUD (Envio via 5G)
void* cloud_worker(void* arg) {
    //const char* env_ip = std::getenv("SERVER_IP");
    //const std::string serverURI = env_ip ? (std::string("tcp://") + env_ip + ":1883") : "tcp://85.234.145.56:1883";
    const std::string serverURI = "tcp://85.234.145.56:1883";
    const std::string clientId = "CSVU_AJP_" + std::to_string(time(nullptr));
    const std::string topic_telemetry = "motos/AJP1RB5SXLB123456/telemetria";
    const std::string topic_commands = "motos/AJP1RB5SXLB123456/comandos";
    
    mqtt::async_client cloudClient(serverURI, clientId);
    mqtt::connect_options connOpts;
    connOpts.set_clean_session(true);

    //ABRE SOCKET CAN EXCLUSIVO PARA ESCRITA
    int s_write = socket(PF_CAN, SOCK_RAW, CAN_RAW);
    struct ifreq ifr{};
    strcpy(ifr.ifr_name, "can0");
    ioctl(s_write, SIOCGIFINDEX, &ifr);
    struct sockaddr_can addr{};
    addr.can_family = AF_CAN;
    addr.can_ifindex = ifr.ifr_ifindex;
    bind(s_write, (struct sockaddr*)&addr, sizeof(addr));
    
    
    //CALLBACK DE RECEÇÃO DE COMANDOS DO SERVIDOR
    cloudClient.set_message_callback([s_write](mqtt::const_message_ptr msg) {
        std::string payload = msg->get_payload_str();
        std::cout << "[DOWNLINK] Comando recebido do Servidor: " << payload << "\n";

        try {
            json cmdJson = json::parse(payload);
            if (cmdJson.contains("cmd")) {
                std::string action = cmdJson["cmd"];
                
                //Constrói frame CAN para a VCU arrancar/desligar
                struct can_frame tx_frame{};
                tx_frame.can_id = 0x105; 
                tx_frame.can_dlc = 1;

                if (action == "ign_on") {
                    tx_frame.data[0] = 0x01;
                    //Atualiza o estado interno com bloqueio de memória
                    std::lock_guard<std::mutex> lock(stateMutex);
                    currentState.ignition = true;
                }
                else if (action == "ign_off") {
                    tx_frame.data[0] = 0x00;
                    //Atualiza o estado interno com bloqueio de memória
                    std::lock_guard<std::mutex> lock(stateMutex);
                    currentState.ignition = false;
                    currentState.speed = 0;
                }

                write(s_write, &tx_frame, sizeof(struct can_frame));
                std::cout << "[CAN TX] Ordem de ignição (" << action << ") injetada no barramento.\n";
            }
        } catch (...) {
            std::cout << "[ERRO] Falha ao ler JSON do comando.\n";
        }
    });
    
    int tick = 0;

    while (true) {
        try {
            if (!cloudClient.is_connected()) {
                std::cout << "[CLOUD] A ligar ao Servidor (" << serverURI << ")...\n";
                cloudClient.connect(connOpts)->wait();
                std::cout << "[CLOUD] Ligado! A subscrever comandos...\n";
                cloudClient.subscribe(topic_commands, 1)->wait();
            }

            MotoState snapshot;
            {
                std::lock_guard<std::mutex> lock(stateMutex);
                snapshot = currentState;
                if (currentState.alert == "THEFT" || currentState.alert == "FALL" || 
                currentState.alert == "FCW" || currentState.alert == "RAIN" || 
                currentState.alert == "RAIN_CLEARED") {
                
                currentState.alert = "NONE";
                }          
            }

            json payload;

            //DADOS RÁPIDOS: Enviados sempre (5x por segundo / 200ms)
            payload["ts"] = std::time(nullptr);
            payload["spd"] = snapshot.speed;
            payload["r"] = snapshot.roll;
            payload["ign"] = snapshot.ignition;
            payload["fcw"]["dist"] = snapshot.fcw_dist;
            payload["fcw"]["ttc"] = snapshot.fcw_ttc;
            payload["alr"] = snapshot.alert;
            payload["lat"] = snapshot.lat;
            payload["lon"] = snapshot.lon;
            
            
            //Força o envio do alerta imediatamente se houver um
            if (snapshot.alert != "NONE") {
                payload["alr"] = snapshot.alert;
            }

            //DADOS LENTOS: Enviados apenas a cada 5 ticks (1 segundo)
            if (tick % 5 == 0) {
                payload["soc"] = snapshot.soc;
                payload["rng"] = snapshot.range;
            }

            std::string msg = payload.dump();
            cloudClient.publish(topic_telemetry, msg, 0, false)->wait();
        } catch (const mqtt::exception& exc) {
            std::cerr << "[CLOUD] Erro de ligação: " << exc.what() << "\n";
        }

        tick++;
        //Espera apenas 200ms em vez de 5 segundos
        std::this_thread::sleep_for(std::chrono::milliseconds(200)); 
    }
    
    close(s_write);
    return nullptr;
}

//Arranque
int main() {
    mqtt::async_client localClient("tcp://127.0.0.1:1883", "CppBroker_V4");
    mqtt::connect_options localConnOpts;
    localConnOpts.set_clean_session(true);

    try {
        localClient.connect(localConnOpts)->wait();
        std::cout << "[LOCAL] Ligado ao Broker Local da Mota.\n";

        std::thread t_can(can_reader, &localClient);
        std::thread t_cloud(cloud_worker, nullptr);

        t_can.join();
        t_cloud.join();
    }
    catch(const mqtt::exception &ex) {
        std::cerr << "Erro Fatal: " << ex.what() << std::endl;
        return 1;
    }
    return 0;
}