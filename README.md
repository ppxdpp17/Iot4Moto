# Connected Electric Motorcycle IoT Ecosystem

![.NET](https://img.shields.io/badge/.NET-8.0-512BD4?logo=dotnet&logoColor=white)
![C++](https://img.shields.io/badge/C++-17-00599C?logo=c%2B%2B&logoColor=white)
![MQTT](https://img.shields.io/badge/MQTT-HiveMQ-E3A628?logo=mqtt&logoColor=white)
![Platform](https://img.shields.io/badge/Platform-ARM32%20%7C%20Yocto-success)

This repository contains the core software components for an end-to-end IoT architecture designed for a connected electric motorcycle (AJP). The system bridges the gap between low-level vehicular networks (CAN bus) and high-level cloud applications (Web/Mobile) via a 5G cellular network.

## 🏗️ System Architecture

The solution is divided into two main processing nodes, replicating a real-world automotive topology:

1. **VCU (Vehicle Control Unit) Simulator:** * **Hardware:** Raspberry Pi 5 (running Raspberry Pi OS 32-bit).
   * **Software:** Built in C# (.NET 8).
   * **Role:** Simulates motorcycle dynamics (speed, battery, range) and reads real physical sensors (IMU, Front Collision Warning, Rain). It acts as the vehicle's brain, publishing raw data to the local network and injecting frames into the CAN bus.

2. **CSVU (Connected Smart Vehicle Unit):** * **Hardware:** Variscite VAR-SOM-6UL & Concerto-Board + Quectel 5G Modem.
   * **Software:** Built in C++17 (using Paho MQTT & nlohmann/json).
   * **Role:** Acts as the central IoT Gateway. It reads real-time data from the CAN bus (`can0`), translates it into optimized JSON payloads, and pushes it to a Cloud MQTT Broker (HiveMQ) over a 5G/LTE connection using TLS encryption.

## ✨ Key Features

* **Real-time CAN-to-MQTT Translation:** Seamless bridging of standard automotive buses to modern IoT protocols.
* **Multithreaded Sensor Fusion:** The VCU processes concurrent routines for simulated vehicle physics and real physical sensor polling without blocking.
* **Hardware-Specific Cross-Compilation:** The .NET 8 simulator is cross-compiled as a self-contained ARM32 executable to accommodate `simplyCAN` hardware library dependencies.
* **Secure Cloud Connectivity:** The C++ Gateway uses TLS/SSL certificates to authenticate and encrypt telemetries sent to the HiveMQ Cloud.
* **Robust 5G Routing:** Configured for isolated network routing, forcing cloud traffic through the Quectel cellular modem while maintaining local SSH access.
* **Environment Configuration:** No hardcoded credentials. All network and broker configurations are injected via `.env` files.

## 🛠️ Hardware Requirements

* Raspberry Pi 5 (for VCU)
* Variscite VAR-SOM-6UL System-on-Module (for CSVU)
* simplyCAN USB/CAN adapter
* Quectel 5G/LTE Cellular Modem
* Physical Sensors (IMU for roll detection, Ultrasonic/Radar for FCW, Rain sensor)

## 🚀 Getting Started

### 1. VCU Simulator Setup (.NET)

The simulator requires cross-compilation from a development machine to the Raspberry Pi.

```bash
# Clone the repository
git clone [https://github.com/yourusername/IoT4Moto.git](https://github.com/yourusername/IoT4Moto.git)
cd IoT4Moto/Simulador

# Build the self-contained executable for ARM32 (required by simplyCAN drivers)
dotnet publish MqttConsoleClient.csproj -c Release -r linux-arm --self-contained true -p:PublishSingleFile=true

# Transfer the output file to the Raspberry Pi
scp bin/Release/net8.0/linux-arm/publish/MqttConsoleClient user@<RPI_IP>:/home/user/
``` 

#### Runnign the VCU:
Create an .env file in the same directory as the executable on the Raspberry Pi:
```bash
BROKER_HOST=127.0.0.1
BROKER_PORT=1883
CLIENT_ID=VCU_Simulator
```

Execute the simulator:
```bash
chmod +x MqttConsoleClient
./MqttConsoleClient
```

### 2. CSVU Gateway Setup (C++)

The Gateway runs on the Variscite board. Ensure the Mosquitto broker is running locally to bridge the VCU and CSVU.

```bash
# Navigate to the Gateway source code
cd ../CSVU

# Compile the C++ Broker/Gateway
g++ -std=c++17 broker_v4.cpp -o mqtt_v4 -I/usr/local/include -L/usr/local/lib -lpaho-mqttpp3 -lpaho-mqtt3as -lpthread

# Ensure the 5G modem interface is up and route the traffic
ip link set enxf255284d1129 up
dhclient enxf255284d1129
ip route replace default via 192.168.224.1 dev enxf255284d1129
```

#### Runnign the CSVU:
```bash
./mqtt_v4
```

## 🔒 Security & Data Privacy
* **Local Isolation:**  The VCU is air-gapped from the internet. It communicates strictly via local CAN bus and local MQTT.
* **QoS Strategies:** The Gateway uses MQTT Quality of Service (QoS) 0 for fluid telemetry (e.g., speed) to preserve bandwidth, and QoS 1/2 for critical events (e.g., collision warnings or theft alarms) to guarantee delivery.
* **TLS Encryption:** All external traffic from the CSVU to the HiveMQ platform is secured using standard `/etc/ssl/certs/ca-certificates.crt`

## 📖 Academic Context
This project was developed as part of a Master's Dissertation in Engineering. The architecture focuses on proving the viability, reliability, and security of integrating modern IoT ecosystems into electric mobility platforms.
