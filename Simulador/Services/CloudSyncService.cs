using System;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using MQTTnet;
using MQTTnet.Client;

public class CloudSyncService
{
    private IMqttClient _mqttClient;
    private MqttClientOptions _mqttOptions;

    // Dados do HiveMQ Cloud (Substitui pelos teus)
    private readonly string _hiveMqServer = "43694c8e9d834a5ab42e7714b016ae38.s1.eu.hivemq.cloud"; 
    private readonly string _username = "ppxdpp";
    private readonly string _password = "Ppxdpp17";
    private readonly string _topic = "mota/AJP/telemetria";

    public async Task InitializeAsync()
    {
        var factory = new MqttFactory();
        _mqttClient = factory.CreateMqttClient();

        _mqttOptions = new MqttClientOptionsBuilder()
            .WithClientId($"Mota_{Guid.NewGuid()}")
            .WithTcpServer(_hiveMqServer, 8883) //8883 é obrigatório para TLS
            .WithCredentials(_username, _password)
            .WithTls() //TLS é obrigatório no HiveMQ Cloud
            .WithCleanSession()
            .Build();

        await ConnectAsync();
    }

    private async Task ConnectAsync()
    {
        try
        {
            Console.WriteLine("A ligar ao HiveMQ Cloud...");
            await _mqttClient.ConnectAsync(_mqttOptions, CancellationToken.None);
            Console.WriteLine("Ligado com sucesso!");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Erro na ligação: {ex.Message}");
        }
    }

    public async Task SendTelemetryAsync(double lat, double lon, int soc, int range, double speed, bool ignition, string alert)
    {
        if (!_mqttClient.IsConnected)
        {
            await ConnectAsync();
        }

        // Construir o payload compacto
        var payload = new
        {
            ts = DateTimeOffset.UtcNow.ToUnixTimeSeconds(),
            lat = lat,
            lon = lon,
            soc = soc,
            rng = range,
            spd = speed,
            ign = ignition,
            alr = alert
        };

        string jsonPayload = JsonSerializer.Serialize(payload);

        var message = new MqttApplicationMessageBuilder()
            .WithTopic(_topic)
            .WithPayload(jsonPayload)
            .WithQualityOfServiceLevel(MQTTnet.Protocol.MqttQualityOfServiceLevel.AtLeastOnce) // QoS 1 garante entrega
            .Build();

        await _mqttClient.PublishAsync(message, CancellationToken.None);
        Console.WriteLine($"Dados enviados: {jsonPayload}");
    }
}