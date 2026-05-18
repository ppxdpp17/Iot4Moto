using Grpc.Core;
using motorcycle;
using System.Text.Json;

namespace CustomBackend.Services
{
    public class MotorcycleGrpcService : MotorcycleService.MotorcycleServiceBase
    {
        private readonly MqttBridgeService _mqttBridge;

        //Injeção de dependência: O ASP.NET entrega o Singleton aqui
        public MotorcycleGrpcService(MqttBridgeService mqttBridge)
        {
            _mqttBridge = mqttBridge;
        }

        public override async Task<CommandReply> SendCommand(CommandRequest request, ServerCallContext context)
        {
            try
            {
                var payload = JsonSerializer.Serialize(new { cmd = request.Action });
                var topic = $"motos/{request.Vin}/comandos";

                //Pede ao Singleton para enviar
                await _mqttBridge.PublicarComandoAsync(topic, payload);
                Console.WriteLine($"[gRPC -> MQTT] Comando '{request.Action}' enviado para {request.Vin}.");

                return new CommandReply { Success = true, Message = "Comando OK." };
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Erro: {ex.Message}");
                return new CommandReply { Success = false, Message = "Falha interna." };
            }
        }

        public override async Task StreamTelemetria(StreamRequest request, IServerStreamWriter<TelemetriaData> responseStream, ServerCallContext context) 
        {
            //Usa um Channel
            var channel = System.Threading.Channels.Channel.CreateUnbounded<TelemetriaData>();

            void AoReceberEstado(TelemetriaData novoEstado) {
                channel.Writer.TryWrite(novoEstado);
            }

            //Subscreve a "campainha" da Telemetria
            MqttBridgeService.OnNovoEstado += AoReceberEstado;

            try 
            {
                await foreach (var estado in channel.Reader.ReadAllAsync(context.CancellationToken)) {
                    await responseStream.WriteAsync(estado);
                }
            }
            finally 
            {
                //Limpeza crítica quando a App se desliga
                MqttBridgeService.OnNovoEstado -= AoReceberEstado;
            }
        }
    }
}