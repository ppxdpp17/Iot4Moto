using System;
using System.Collections.Generic;
using System.Threading.Tasks;
using Simulador.Models;

namespace Simulador.Services
{
    public class SensorService
    {
        private readonly Dictionary<string, (int ArbitrationId, string AlgorithmID)> _sensorMappings;
        private readonly MqttService _mqttService;
        private readonly LoggingService _loggingService;
        private readonly Random _rand = new Random();
        private readonly CAN.CanService _canService;

        public SensorService(Dictionary<string, (int ArbitrationId, string AlgorithmID)> sensorMappings, 
                     MqttService mqttService, LoggingService loggingService, CAN.CanService canService)
        {
            _sensorMappings = sensorMappings;
            _mqttService = mqttService;
            _loggingService = loggingService;
            _canService = canService;
        }

        /// <summary>
        /// Sends sensor messages while the danger (measured distance) remains below 100m.
        /// If the distance exceeds 100m there is a 90% chance the danger ends.
        /// If a collision occurs (distance = 0), sending stops immediately.
        /// Ao final, envia uma mensagem final com status false.
        /// </summary>
        public async Task SendSensorMessagesAsync(string sensorName)
        {
            if (!_sensorMappings.ContainsKey(sensorName))
            {
                Console.WriteLine("Invalid Sensor. Available Sensors: " +
                                  string.Join(", ", _sensorMappings.Keys));
                return;
            }

            var (arbitrationIdBase, algorithmID) = _sensorMappings[sensorName];

            Console.WriteLine($"🔄 Starting automatic message sending to '{sensorName}'...\n");

            for (int i = 0; i < 10; i++)  // Simula 10 envios
            {
                int distancia = _rand.Next(30, 300); // cm
                bool status = distancia < 150;       // PERIGO se < 150cm
                int prioridade = distancia < 80 ? 1 : (distancia < 200 ? 2 : 3);
                string lado = _rand.NextDouble() < 0.5 ? "Right" : "Left";
                int ladoBit = lado == "Right" ? 1 : 0;

                // ArbitrationId with priority (optional - or keep it fixed)
                int arbitrationId = arbitrationIdBase;

                var canMessage = new CanMessage
                {
                    AlgorithmID = algorithmID,
                    CAN_Message = new CanData
                    {
                        ArbitrationId = arbitrationId,
                        Data = new int[8]
                    }
                };

                canMessage.CAN_Message.Data[0] = status ? 1 : 0;
                canMessage.CAN_Message.Data[1] = distancia & 0xFF;
                canMessage.CAN_Message.Data[2] = (distancia >> 8) & 0xFF;
                canMessage.CAN_Message.Data[3] = ladoBit;
                canMessage.CAN_Message.Data[7] = prioridade;

                string json = System.Text.Json.JsonSerializer.Serialize(canMessage);
                await _mqttService.PublishAsync("sim/canmessages", json);

                Console.WriteLine($"✅ Sent: {json}");
                Program.WriteMessage(DateTime.UtcNow.ToString("o")); // formato ISO 8601
                
                byte[] payload = new byte[8];
                payload[0] = status ? (byte)1 : (byte)0;
                payload[1] = (byte)(distancia & 0xFF);
                payload[2] = (byte)((distancia >> 8) & 0xFF);
                payload[3] = (byte)ladoBit;
                payload[7] = (byte)prioridade;
                
                _canService.EnviarDadosSimulados((uint)arbitrationIdBase, payload);

                await Task.Delay(1000); // espera 1 segundo
            }

            Console.WriteLine($"✅ Automatic sending completed for '{sensorName}'.");
        }


        public async Task StartManualInputAsync()
        {
            Console.WriteLine("== MANUAL MODE OF SENSOR DATA INSERTION ==\n");

            while (true)
            {
                Console.Write("Write the sensor number (1 - FrontalCollision, 2 - RearCollision, 3 - BlindSpotDetection, 4 - PedestrianDetection): ");
                string inputSensorName = Console.ReadLine();
                string sensorName = "";

                switch (inputSensorName)
                {
                    case "1":
                        sensorName = "frontalcollision";
                        break;
                    case "2":
                        sensorName = "rearcollision";
                        break;
                    case "3":
                        sensorName = "blindspot";
                        break;
                    case "4":
                        sensorName = "pedestrian";
                        break;
                }

                if (string.IsNullOrEmpty(sensorName))
                {
                    Console.WriteLine("Exiting manual mode.");
                    break;
                }

                if (!_sensorMappings.ContainsKey(sensorName))
                {
                    Console.WriteLine("Invalid sensor. Try again.\n");
                    continue;
                }

                var (arbitrationId, algorithmID) = _sensorMappings[sensorName];

                Console.Write("Distance (meters): ");
                if (!int.TryParse(Console.ReadLine(), out int distancia))
                {
                    Console.WriteLine("Invalid distance.\n");
                    continue;
                }

                Console.Write("Status (1 - Safe, 2 - DANGER): ");
                string inputStatus = Console.ReadLine();
                string statusStr = "";

                switch (inputStatus)
                {
                    case "1":
                        statusStr = "Safe";
                        break;
                    case "2":
                        statusStr = "DANGER";
                        break;
                }

                bool status = statusStr?.ToUpper() == "DANGER";

                Console.Write("Priority (1 = High, 2 = Medium, 3 = Low): ");
                if (!int.TryParse(Console.ReadLine(), out int prioridade))
                {
                    Console.WriteLine("Invalid Priority.\n");
                    continue;
                }

                string lado = null;
                int ladoBit = 0;


                Console.Write("Side (Right or Left): ");
                lado = Console.ReadLine();
                ladoBit = lado?.ToLower() == "right" ? 1 : 0;


                // ⚙️ Construir ArbitrationId com prioridade
                int prioridadeOffset = prioridade switch
                {
                    1 => 0x100,
                    2 => 0x200,
                    3 => 0x300,
                    _ => 0x200
                };

                int arbitrationIdComPrioridade = arbitrationId;


                // 🧱 Construir estrutura CAN
                var canMessage = new CanMessage
                {
                    AlgorithmID = algorithmID,
                    CAN_Message = new CanData
                    {
                        ArbitrationId = arbitrationIdComPrioridade,
                        Data = new int[8]
                    }
                };

                switch (algorithmID)
                {
                    case "FrontalCollisionDetection":
                        canMessage.CAN_Message.Data[0] = status ? 1 : 0;
                        canMessage.CAN_Message.Data[1] = distancia & 0xFF;
                        canMessage.CAN_Message.Data[2] = (distancia >> 8) & 0xFF;
                        canMessage.CAN_Message.Data[3] = ladoBit;
                        canMessage.CAN_Message.Data[7] = prioridade;
                        break;
                    case "RearCollisionDetection":
                        canMessage.CAN_Message.Data[0] = status ? 1 : 0;
                        canMessage.CAN_Message.Data[1] = distancia & 0xFF;
                        canMessage.CAN_Message.Data[2] = (distancia >> 8) & 0xFF;
                        canMessage.CAN_Message.Data[3] = ladoBit;
                        canMessage.CAN_Message.Data[7] = prioridade;
                        break;
                    case "BlindSpotDetection":
                        canMessage.CAN_Message.Data[0] = status ? 1 : 0;
                        canMessage.CAN_Message.Data[1] = distancia & 0xFF;
                        canMessage.CAN_Message.Data[2] = (distancia >> 8) & 0xFF;
                        canMessage.CAN_Message.Data[3] = ladoBit;
                        canMessage.CAN_Message.Data[7] = prioridade;
                        break;
                    case "PedestrianDetection":
                        canMessage.CAN_Message.Data[0] = status ? 1 : 0;
                        canMessage.CAN_Message.Data[1] = distancia & 0xFF;
                        canMessage.CAN_Message.Data[2] = (distancia >> 8) & 0xFF;
                        canMessage.CAN_Message.Data[3] = ladoBit;
                        canMessage.CAN_Message.Data[7] = prioridade;
                        break;
                }
                
                

                // Publish raw JSON on the CAN simulation topic
                string rawJson = System.Text.Json.JsonSerializer.Serialize(canMessage);
                await _mqttService.PublishAsync("sim/canmessages", rawJson);
                byte[] manualPayload = new byte[8];
                for(int i=0; i<8; i++) manualPayload[i] = (byte)canMessage.CAN_Message.Data[i];
                _canService.EnviarDadosSimulados((uint)arbitrationIdComPrioridade, manualPayload);
                Console.WriteLine($"\n✅ Published on topic: sim/canmessages: {rawJson}");
                Program.WriteMessage(DateTime.UtcNow.ToString("o")); // formato ISO 8601
            }
        }
    }
}
