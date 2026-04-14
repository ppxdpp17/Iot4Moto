using Grpc.Core;
using motorcycle;
using MQTTnet;
using MQTTnet.Client;
using System.Text.Json;

namespace CustomBackend.Services
{
    public class MotorcycleGrpcService : MotorcycleService.MotorcycleServiceBase
    {
        private readonly IMqttClient _mqttClient;

        public MotorcycleGrpcService()
        {
            var factory = new MqttFactory();
            _mqttClient = factory.CreateMqttClient();
        }

        private async Task EnsureMqttConnection()
        {
            if (!_mqttClient.IsConnected)
            {
                var options = new MqttClientOptionsBuilder()
                    .WithTcpServer("mosquitto", 1883) 
                    .WithClientId("CustomBackend_" + Guid.NewGuid())
                    .Build();
                await _mqttClient.ConnectAsync(options);
            }
        }

        public override async Task<CommandReply> SendCommand(CommandRequest request, ServerCallContext context)
        {
            try
            {
                await EnsureMqttConnection();

                var payload = JsonSerializer.Serialize(new { cmd = request.Action });
                var topic = $"motos/{request.Vin}/comandos";

                var msg = new MqttApplicationMessageBuilder()
                    .WithTopic(topic)
                    .WithPayload(payload)
                    .WithQualityOfServiceLevel(MQTTnet.Protocol.MqttQualityOfServiceLevel.AtLeastOnce)
                    .Build();

                await _mqttClient.PublishAsync(msg);
                Console.WriteLine($"[gRPC -> MQTT] Comando '{request.Action}' enviado para {request.Vin}.");

                return new CommandReply { Success = true, Message = "Comando OK." };
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Erro: {ex.Message}");
                return new CommandReply { Success = false, Message = "Falha interna." };
            }
        }

	public override async Task StreamTelemetria(StreamRequest request, IServerStreamWriter<TelemetriaData> responseStream, ServerCallContext context) {
    	// Enquanto o telemóvel estiver ligado à stream...
    		while (!context.CancellationToken.IsCancellationRequested) {
        		// Envia o que o MqttBridgeService guardou
        		await responseStream.WriteAsync(MqttBridgeService.UltimoEstado);
        
        		// Espera 1 segundo para não sobrecarregar a rede
        		await Task.Delay(1000); 
    		}
	}
    }
}
