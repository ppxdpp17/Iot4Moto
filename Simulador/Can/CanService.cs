using System;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using IXXAT;
using Simulador.Services;
using Simulador.Core;
using static IXXAT.SimplyCAN;

namespace CAN
{
    public class CanService
    {
        private static char[] mserial_port = "/dev/ttyACM0\0".ToCharArray();
        private static ushort mbitrate = 500;
        private readonly MqttService _mqttService;
        private readonly object _canLock = new object();

        public CanService(MqttService mqttService)
        {
            _mqttService = mqttService;
        }

        private void ErrorExit()
        {
            Console.WriteLine("[CAN] Erro Crítico: " + simply_get_last_error());
            simply_close();
        }

        // --- RECEÇÃO DE DADOS ---
        private async Task InterpretarMensagem(can_msg_t msg)
        {
            if (msg.dlc < 4) return;

            byte status = msg.payload[0];
            int distancia = msg.payload[1] + (msg.payload[2] << 8);
            string lado = msg.payload[3] == 1 ? "Direita" : "Esquerda";

            uint tipoSensor = msg.ident & 0xFF; 
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
            var payload = new { status = perigo, distancia, lado, prioridade };

            await _mqttService.PublishAsync($"sim/can/{algoritmo}", JsonSerializer.Serialize(payload));
        }

        public async Task StartAsync(CancellationToken token)
        {
            if (!simply_open(mserial_port) || !simply_initialize_can(mbitrate) || !simply_start_can())
            {
                ErrorExit();
                return;
            }

            Console.WriteLine("[CAN] Ligação iniciada em /dev/ttyACM0.");

            // Inicia o Heartbeat em background
            _ = StartHeartbeatAsync(token);

            await Task.Run(async () =>
            {
                can_msg_t rx = new can_msg_t();
                while (!token.IsCancellationRequested)
                {
                    sbyte res;
                    lock (_canLock) //Bloqueia a API enquanto lê para não chocar com o envio
                    {
                        res = simply_receive(ref rx);
                    }
                    
                    if (res == 1) await InterpretarMensagem(rx);
                    else if (res == -1) ErrorExit();
                    
                    Thread.Sleep(1);
                }
            }, token);

            simply_stop_can();
            simply_close();
        }

        // --- ENVIO DE DADOS ---
        public bool EnviarDadosSimulados(uint id, byte[] data)
        {
            if (data.Length > 8) return false;

            lock (_canLock) // Evita colisões no envio
            {
                can_msg_t txMsg = new can_msg_t
                {
                    ident = id,
                    dlc = (byte)data.Length,
                    payload = new byte[8]
                };
                Array.Copy(data, txMsg.payload, data.Length);

                int tentativas = 3;
                bool success = false;

                while (tentativas > 0 && !success)
                {
                    success = simply_send(ref txMsg);
                    if (!success)
                    {
                        // O buffer de hardware está cheio. Dá 2ms para ele despejar dados no cabo.
                        Thread.Sleep(2);
                        tentativas--;
                    }
                }

                if (!success) 
                {
                    // Lê o erro real para o limpar da memória do dispositivo
                    short erro = simply_get_last_error(); 
                    Console.WriteLine($"[CAN] Falha ID 0x{id:X}. Erro interno: {erro}");
                }
                return success;
            }
        }

        // Métodos de atalho para envio
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

        public void EnviarAbs(bool state) => 
            EnviarDadosSimulados(0x206, new byte[] { (byte)(state ? 1 : 0) });

        public void EnviarTemperatura(int temp) => 
            EnviarDadosSimulados(0x207, new byte[] { (byte)temp });

        public void EnviarAutonomia(double autonomy) {
            int range = (int)Math.Round(autonomy);
            EnviarDadosSimulados(0x208, new byte[] { (byte)(range & 0xFF), (byte)((range >> 8) & 0xFF) });
        }

        public void EnviarTotalKilometers(int km) {
            byte[] data = BitConverter.GetBytes(km); 
            EnviarDadosSimulados(0x209, data);
        }

        public void EnviarTempo(int horas, int minutos) {
            EnviarDadosSimulados(0x210, new byte[] { (byte)horas, (byte)minutos });
        }


        //Envio de dados do HC-SR04 (FCW)
        public void EnviarFcw(double distanceMeters, double ttcSeconds, string type)
        {
            //Converter a distância para centímetros (para caber num inteiro de 16 bits)
            ushort distCm = (ushort)Math.Round(distanceMeters * 100);
            
            //Converte o TTC para décimas de segundo (para caber num byte)
            byte ttcDs = (byte)Math.Min(255, Math.Max(0, Math.Round(ttcSeconds * 10)));
            
            //Converte o Tipo de Alerta para um código numérico (0=INFO, 1=WARNING, 2=CRITICAL)
            byte typeByte = (byte)(type == "CRITICAL" ? 2 : type == "WARNING" ? 1 : 0);

            byte[] data = new byte[4];
            data[0] = (byte)(distCm & 0xFF);          //Distância (Byte baixo)
            data[1] = (byte)((distCm >> 8) & 0xFF);   //Distância (Byte alto)
            data[2] = ttcDs;                          //TTC
            data[3] = typeByte;                       //Nível de Alerta

            EnviarDadosSimulados(0x302, data);
        }

        //SIMULADOR A GERAR DADOS DE GPS (ESTÁTICOS) - MAIS TARDE RETIRAR ISTO, JÁ QUE OS DADOS GPS
        //SERÃO "GERADOS" DIRETAMENTE NA CSVU ATRAVÉS DO MODEM
        public void EnviarGPS(double latitude, double longitude)
        {
            int latInt = (int)Math.Round(latitude * 1000000);
            int lonInt = (int)Math.Round(longitude * 1000000);

            byte[] data = new byte[8];
            // Latitude (4 bytes, Little Endian)
            data[0] = (byte)(latInt & 0xFF);
            data[1] = (byte)((latInt >> 8) & 0xFF);
            data[2] = (byte)((latInt >> 16) & 0xFF);
            data[3] = (byte)((latInt >> 24) & 0xFF);
            
            // Longitude (4 bytes, Little Endian)
            data[4] = (byte)(lonInt & 0xFF);
            data[5] = (byte)((lonInt >> 8) & 0xFF);
            data[6] = (byte)((lonInt >> 16) & 0xFF);
            data[7] = (byte)((lonInt >> 24) & 0xFF);

            EnviarDadosSimulados(0x301, data);
        }




        // --- O MOTOR DO SIMULADOR (HEARTBEAT) ---
        public async Task StartHeartbeatAsync(CancellationToken ct)
        {
            using PeriodicTimer timer = new PeriodicTimer(TimeSpan.FromMilliseconds(100)); // Base de 100ms
            int tickCount = 0;

            while (await timer.WaitForNextTickAsync(ct))
            {
                // --- 100ms (Sempre) ---
                EnviarVelocidade(SimulationState.Speed);

                // --- 200ms (A cada 2 ticks) ---
                if (tickCount % 200 == 0)
                {
                    EnviarGear(SimulationState.Gear);
                    EnviarModo(SimulationState.DriveMode);
                    EnviarLuzes(); // Já inclui Piscas e DangerLights
                    EnviarAbs(SimulationState.Abs);
                    EnviarBrake(SimulationState.Brake);
                }

                // --- 500ms (A cada 5 ticks) ---
                if (tickCount % 500 == 0)
                {
                    EnviarBateria(SimulationState.Battery);
                    EnviarTemperatura(SimulationState.Temperature);
                    EnviarAutonomia(SimulationState.Autonomy);
                }

                //Intervalo de 1s porque não precisa de atualizar com grande velocidade
                if (tickCount % 1000 == 0)
                {
                    EnviarGPS(SimulationState.Latitude, SimulationState.Longitude);
                }

                // Incrementa e reinicia a cada segundo para evitar overflow (embora irrelevante aqui)
                tickCount = (tickCount + 100) % 1000;
            }
        }
    }
}