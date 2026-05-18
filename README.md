# Connected Electric Motorcycle IoT Ecosystem

![.NET](https://img.shields.io/badge/.NET-8.0-512BD4?logo=dotnet&logoColor=white)
![C++](https://img.shields.io/badge/C++-17-00599C?logo=c%2B%2B&logoColor=white)
![MQTT](https://img.shields.io/badge/MQTT-HiveMQ-E3A628?logo=mqtt&logoColor=white)
![Platform](https://img.shields.io/badge/Platform-ARM32%20%7C%20Yocto-success)

This repository contains the core software components for an end-to-end IoT architecture designed for a connected electric motorcycle (AJP). The system bridges the gap between low-level vehicular networks (CAN bus, GPIO) and high-level cloud applications (Web & Mobile) via a 5G cellular network, utilizing advanced communication protocols for maximum performance and efficiency.

## 🏗️ System Architecture

The solution implements a robust Edge-to-Cloud-to-Client topology, divided into five main interconnected nodes:

1. **VCU (Vehicle Control Unit) Simulator:** * **Hardware:** Raspberry Pi 5 (running Raspberry Pi OS 32-bit).
   * **Software:** Built in C# (.NET 8).
   * **Role:** Simulates motorcycle dynamics (speed, battery, range) and reads real physical sensors (IMU, Front Collision Warning, Rain). It acts as the vehicle's brain, publishing raw data to the local network and injecting frames into the CAN bus.

2. **CSVU (Connected Smart Vehicle Unit):** * **Hardware:** Variscite VAR-SOM-6UL & Concerto-Board + Quectel 5G Modem.
   * **Software:** Built in C++17 (using Paho MQTT & nlohmann/json).
   * **Role:** Acts as the central IoT Gateway. It reads real-time data from the CAN bus (`can0`), translates it into optimized JSON payloads, and pushes it to the Cloud over a 5G/LTE connection.
  
3. **Cloud - Backend Server & Message Broker:** * **System Contents:** Dockerized environment containing Eclipse Mosquitto and a custom Backend Server.
   * **Software:** Built in C# (.NET 8 ASP.NET Core).
   * **Role:** The central traffic dispatcher. It ingests the MQTT JSON payloads from the motorcycle, deserializes them into RAM, and simultaneously broadcasts the telemetry via two distinct high-performance pipes: SSE (for Web) and gRPC (for Mobile).
  
4. **Client - Web Platform:** * **Hardware:** Windows Computer
   * **Software:** HTML5, JavaScript, Leaflet.js.
   * **Role:** A real-time dashboard for tracking the motorcycle's telemetry, route history, and alerts. Uses Server-Sent Events (SSE) for a lightweight, unidirectional, and auto-reconnecting data stream over HTTP/1.1.
  
5. **Client - Android App:** * **Hardware:** Android Smartphone
   * **Software:** Native Android (Kotlin, Coroutines).
   * **Role:** A companion app for the rider. Uses gRPC (HTTP/2) to maintain a highly compressed, multiplexed connection with the cloud. It features a ForegroundService for continuous alert monitoring (Crash detection, SOS SMS, Theft) with near-zero impact on battery life.

## ✨ Key Features

* **Protocol Bifurcation for Performance:** he Cloud Server intelligently routes data using SSE for the Web Platform (text-based, unidirectional) and gRPC/Protobuf for the Android App (binary-compressed, bi-directional), achieving >70% payload size reduction on mobile networks.
* **Real-time CAN-to-MQTT Translation:** Seamless bridging of standard, noise-immune automotive buses to modern IoT internet protocols.
* **Hardware-Specific Cross-Compilation:** The .NET 8 simulator is cross-compiled as a self-contained ARM32 executable to accommodate `simplyCAN` hardware library dependencies.
* **Observer & Singleton Patterns:** The Android App implements aggressive decoupling using `CopyOnWriteArrayList` and the Observer pattern (`AlertManager`), ensuring UI threads are never blocked by incoming network telemetry.
* **Robust 5G Routing:** Configured for isolated network routing, forcing cloud traffic through the Quectel cellular modem while maintaining local SSH access.
* **Environment Configuration:** No hardcoded credentials. All network and broker configurations are injected via `.env` files.
* **HTTP/2 Multiplexing:** The Android App can send commands (e.g., Remote Ignition) UP to the server while simultaneously receiving GPS streams DOWN from the server through the exact same TCP pipe.

## 🛠️ Hardware Requirements

* Raspberry Pi 5 (for VCU)
* Variscite VAR-SOM-6UL System-on-Module (for CSVU)
* simplyCAN USB/CAN adapter
* Quectel 5G/LTE Cellular Modem
* Physical Sensors (IMU for roll detection, Ultrasonic/Radar for FCW, Rain sensor)
* Cloud Virtual Machine (capable of running Docker Compose)
* Windows Computer
* Android Smartphone

## 🚀 Getting Started

### 1. VCU Simulator Setup (.NET)

Cross-compile the executable from your development machine to the Raspberry Pi:

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

### 3. Cloud Server Setup

Deploy the backend using Docker Compose:

```bash
cd CloudServer
docker-compose up --build -d
```


## 🔒 Security & Data Privacy
* **Edge Isolation:**  The VCU is air-gapped from the public internet. It communicates strictly via local CAN bus.
* **QoS Strategies:** The Gateway uses MQTT Quality of Service (QoS) 0 for fluid telemetry (e.g., speed) to preserve bandwidth, and QoS 1/2 for critical events (e.g., collision warnings or theft alarms) to guarantee delivery.
* **Cloud Auth:** AgRPC endpoints provide the foundation for secure, token-based authentication for mobile clients.

## 📖 Academic Context
This project was developed as part of a Master's Dissertation in Engineering. The architecture focuses on proving the viability, reliability, and security of integrating modern IoT ecosystems into electric mobility platforms, addressing the specific constraints of hardware processing, network latency, and mobile battery consumption.
