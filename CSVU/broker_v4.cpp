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

using json = nlohmann::json;

// 1. ESTRUTURA GLOBAL DO ESTADO DA MOTA
struct MotoState {
    double lat = 0.0;
    double lon = 0.0;
    int soc = 0;
    int range = 0;
    int speed = 0;
    bool ignition = true; // Assume true se estiver a emitir CAN
    std::string alert = "NONE";
};

MotoState currentState;
std::mutex stateMutex; // Protege a estrutura contra concorrência

// 2. THREAD DO CAN (Atualiza o Estado e o Broker Local)
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
        std::lock_guard<std::mutex> lock(stateMutex); // Bloqueia para escrever

        if (id == 0x200) { 
            uint16_t speed = frame.data[0] | (frame.data[1] << 8);
            currentState.speed = speed;
            client->publish("moto/speed", std::to_string(speed));
        }
        else if (id == 0x201) {
            currentState.soc = (int)frame.data[0];
            client->publish("moto/battery", std::to_string(currentState.soc), 0, true);
        }
        else if (id == 0x208) {
            currentState.range = frame.data[0]; 
            client->publish("moto/range", std::to_string(currentState.range), 0, true);
        }
        else if (id == 0x301) {
            int32_t lat_raw = frame.data[0] | (frame.data[1] << 8) | (frame.data[2] << 16) | (frame.data[3] << 24);
            int32_t lon_raw = frame.data[4] | (frame.data[5] << 8) | (frame.data[6] << 16) | (frame.data[7] << 24);
            currentState.lat = lat_raw / 1000000.0;
            currentState.lon = lon_raw / 1000000.0;
            
            json gpsPayload = {{"lat", currentState.lat}, {"lon", currentState.lon}};
            client->publish("moto/gps", gpsPayload.dump());
        }
        else if (id == 0x400) {
            currentState.alert = "ALERTA_MOVIMENTO";
            client->publish("moto/alarm", currentState.alert);
        }
        // ... (MANTÉM O RESTO DOS TEUS IFs ORIGINAIS AQUI: luzes, piscas, etc para o broker local) ...
    }
    return nullptr;
}

// 3. THREAD DA CLOUD (Envia via 5G a cada 5 segs)
void* cloud_worker(void* arg) {
    const std::string serverURI = "ssl://43694c8e9d834a5ab42e7714b016ae38.s1.eu.hivemq.cloud:8883";
    const std::string clientId = "Mota_CloudSync_" + std::to_string(time(nullptr));
    const std::string topic = "motos/AJP1RB5SXLB123456/telemetria";

    mqtt::async_client cloudClient(serverURI, clientId);
    
    mqtt::connect_options connOpts;
    connOpts.set_user_name("ppxdpp");
    connOpts.set_password("Ppxdpp17");
    connOpts.set_clean_session(true);

    // TLS Options (Obrigatório para o HiveMQ)
    mqtt::ssl_options sslOpts;
    sslOpts.set_enable_server_cert_auth(true);
    sslOpts.set_trust_store("/etc/ssl/certs/ca-certificates.crt"); // Caminho standard Linux
    connOpts.set_ssl(sslOpts);

    while (true) {
        try {
            if (!cloudClient.is_connected()) {
                std::cout << "[CLOUD] A ligar ao HiveMQ...\n";
                cloudClient.connect(connOpts)->wait();
                std::cout << "[CLOUD] Ligado com sucesso!\n";
            }

            // Copia rápida do estado para não bloquear o CAN_Reader
            MotoState snapshot;
            {
                std::lock_guard<std::mutex> lock(stateMutex);
                snapshot = currentState;
                currentState.alert = "NONE"; // Limpa o alerta depois de o capturar
            }

            // Monta o JSON compacto
            json payload;
            payload["ts"] = std::time(nullptr);
            payload["lat"] = snapshot.lat;
            payload["lon"] = snapshot.lon;
            payload["soc"] = snapshot.soc;
            payload["rng"] = snapshot.range;
            payload["spd"] = snapshot.speed;
            payload["ign"] = snapshot.ignition;
            payload["alr"] = snapshot.alert;

            std::string msg = payload.dump();
            cloudClient.publish(topic, msg, 1, false)->wait();
            std::cout << "[CLOUD] Enviado: " << msg << "\n";

        } catch (const mqtt::exception& exc) {
            std::cerr << "[CLOUD] Erro: " << exc.what() << "\n";
        }

        std::this_thread::sleep_for(std::chrono::seconds(5));
    }
    return nullptr;
}

// 4. ARRANQUE
int main() {
    // Liga ao broker LOCAL (Mosquitto na CSVU)
    mqtt::async_client localClient("tcp://127.0.0.1:1883", "CppBroker_V4");
    mqtt::connect_options localConnOpts;
    localConnOpts.set_clean_session(true);

    try {
        localClient.connect(localConnOpts)->wait();
        std::cout << "[LOCAL] Ligado ao Broker Local.\n";

        pthread_t can_tid, cloud_tid;
        pthread_create(&can_tid, nullptr, can_reader, &localClient);
        pthread_create(&cloud_tid, nullptr, cloud_worker, nullptr);

        while(true) { std::this_thread::sleep_for(std::chrono::hours(1)); }
    }
    catch(const mqtt::exception &ex) {
        std::cerr << "Erro Fatal: " << ex.what() << std::endl;
        return 1;
    }
    return 0;
}