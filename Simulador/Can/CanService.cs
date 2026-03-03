using System;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using IXXAT;
using Simulador.Services;
using static IXXAT.SimplyCAN;

namespace CAN
{
    public class CanService
    {
        private static char[] mserial_port = "/dev/ttyACM0\0".ToCharArray();
        private static ushort mbitrate = 500;
        private readonly MqttService _mqttService;

        public CanService(MqttService mqttService)
        {
            _mqttService = mqttService;
        }

        private void ErrorExit()
        {
            Console.WriteLine("Erro: " + simply_get_last_error());
            simply_close();
        }

        private async Task InterpretarMensagem(can_msg_t msg)
        {
            if (msg.dlc < 4) return;

            byte status = msg.payload[0];
            int distancia = msg.payload[1] + (msg.payload[2] << 8);
            string lado = msg.payload[3] == 1 ? "Direita" : "Esquerda";

            uint tipoSensor = msg.ident & 0xFF;  // Mask to obtain only the sensor type
            int prioridade = (int)((msg.ident & 0xF00) >> 8);
            string algoritmo = tipoSensor switch
            {
                0x00 => "FrontalCollision",
                0x10 => "RearCollision",
                0x20 => "BlindSpotDetection",
                0x05 => "PedestrianDetection",
                _ => $"Desconhecido_{msg.ident:X}"
            };

            string perigo = status == 1 ? "PERIGO" : "Seguro";

            Console.WriteLine($"[CAN] {algoritmo} | Priority: {prioridade} | Status: {perigo} | Distance: {distancia} cm | Side: {lado}");

            var payload = new
            {
                status = perigo,
                distancia,
                lado,
                prioridade
            };

            string topic = $"sim/can/{algoritmo}";
            string json = JsonSerializer.Serialize(payload);

            await _mqttService.PublishAsync(topic, json);
        }

        public async Task StartAsync(CancellationToken token)
        {
            if (!simply_open(mserial_port) || !simply_initialize_can(mbitrate) || !simply_start_can())
            {
                ErrorExit();
                return;
            }

            Console.WriteLine("[CAN] CAN connection started.");

            await Task.Run(async () =>
            {
                can_msg_t rx = new can_msg_t();
                while (!token.IsCancellationRequested)
                {
                    sbyte res = simply_receive(ref rx);
                    if (res == 1)
                        await InterpretarMensagem(rx);
                    else if (res == -1)
                        ErrorExit();

                    Thread.Sleep(1);
                }
            }, token);

            simply_stop_can();
            simply_close();
            Console.WriteLine("[CAN] CAN connection stopped.");
        }
        
        //Método de envio de dados
        public bool EnviarDadosSimulados(uint id, byte[] data)
        {
            if (data.Length > 8) throw new ArgumentException("CAN payload max 8 bytes");

            can_msg_t txMsg = new can_msg_t
            {
                ident = id,
                dlc = (byte)data.Length,
                payload = new byte[8]
            };
            Array.Copy(data, txMsg.payload, data.Length);

            bool success = simply_send(ref txMsg);
            
            if (!success)
            {
                Console.WriteLine($"[CAN] Erro ao enviar ID 0x{id:X}: {simply_get_last_error()}");
            }
            return success;
        }
        
        public void EnviarVelocidade(int speed) => 
            EnviarDadosSimulados(0x200, new byte[] { (byte)(speed & 0xFF), (byte)((speed >> 8) & 0xFF) });

        public void EnviarBateria(int battery) => 
            EnviarDadosSimulados(0x201, new byte[] { (byte)battery });

        public void EnviarGear(string gear) => 
            EnviarDadosSimulados(0x202, new byte[] { (byte)(gear.ToLower() switch { "neutral"=>0, "front"=>1, "back"=>2, "park"=>3, _=>0 }) });

        public void EnviarModo(string mode) => 
            EnviarDadosSimulados(0x203, new byte[] { (byte)(mode.ToLower() switch { "standard"=>0, "eco"=>1, "sport"=>2, _=>0 }) });

        public void EnviarLuzes() {
            byte mask = 0;
            if (SimulationState.Lights) mask |= 0x01;
            if (SimulationState.MaxLights) mask |= 0x02;
            if (SimulationState.IndicatorLeft) mask |= 0x04;
            if (SimulationState.IndicatorRight) mask |= 0x08;
            if (SimulationState.DangerLights) mask |= 0x10;
            EnviarDadosSimulados(0x204, new byte[] { mask });
        }

        public void EnviarBrake(bool brake) => 
            EnviarDadosSimulados(0x205, new byte[] { (byte)(brake ? 1 : 0) });
    }
}
