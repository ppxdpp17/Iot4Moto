using MQTTnet;
using MQTTnet.Client;
using System;
using System.Threading.Tasks;

namespace Simulador.Services
{
    public class MqttService
    {
        private readonly IMqttClient _mqttClient;
        private readonly string _brokerAddress;
        private readonly int _brokerPort;
        private readonly string _clientId;

        // EVENTO PARA AVISAR O PROGRAM.CS QUE CHEGOU UMA MENSAGEM
        public event Action<string, string>? OnMessageReceived;

        public MqttService(string brokerAddress, int brokerPort, string clientId)
        {
            _brokerAddress = brokerAddress;
            _brokerPort = brokerPort;
            _clientId = clientId;

            var factory = new MqttFactory();
            _mqttClient = factory.CreateMqttClient();

            // QUANDO RECEBE MENSAGEM DO BROKER, DISPARA O EVENTO
            _mqttClient.ApplicationMessageReceivedAsync += e =>
            {
                var topic = e.ApplicationMessage.Topic;
                var payload = System.Text.Encoding.UTF8.GetString(e.ApplicationMessage.PayloadSegment);
                OnMessageReceived?.Invoke(topic, payload);
                return Task.CompletedTask;
            };
        }

        public async Task ConnectAsync()
        {
            var options = new MqttClientOptionsBuilder()
                .WithTcpServer(_brokerAddress, _brokerPort)
                .WithClientId(_clientId)
                .Build();

            try
            {
                await _mqttClient.ConnectAsync(options);
                Console.WriteLine("Conectado ao broker MQTT!");
            }
            catch (Exception ex)
            {
                Console.WriteLine("Erro ao conectar: " + ex.Message);
                throw;
            }
        }

        public async Task PublishAsync(string topic, string payload, bool retain = false)
        {
            if (_mqttClient.IsConnected)
            {
                var message = new MqttApplicationMessageBuilder()
                    .WithTopic(topic)
                    .WithPayload(payload)
                    .WithQualityOfServiceLevel(MQTTnet.Protocol.MqttQualityOfServiceLevel.AtLeastOnce)
                    .WithRetainFlag(retain)
                    .Build();

                await _mqttClient.PublishAsync(message);
            }
            else
            {
                Console.WriteLine("MQTT client is not connected.");
            }
        }

        // FUNÇÃO PARA SUBSCREVER UM TÓPICO
        public async Task SubscribeAsync(string topic)
        {
            if (_mqttClient.IsConnected)
            {
                await _mqttClient.SubscribeAsync(topic);
            }
        }

        public async Task DisconnectAsync()
        {
            if (_mqttClient.IsConnected)
            {
                await _mqttClient.DisconnectAsync();
                Console.WriteLine("Desconectado do broker MQTT.");
            }
        }
    }
}