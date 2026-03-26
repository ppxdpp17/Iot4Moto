using System;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using MQTTnet;
using MQTTnet.Client;
using Simulador.Core;

public class CloudSyncService
{
    private IMqttClient _mqttClient;
    private MqttClientOptions _mqttOptions;
    private readonly string _topic;

    public CloudSyncService(string host, int port, string username, string password, bool useTls, string vin)
    {
        _topic = $"motos/{vin}/telemetria";
        var factory = new MqttFactory();
        _mqttClient = factory.CreateMqttClient();

        var builder = new MqttClientOptionsBuilder()
            .WithClientId($"Moto_{vin}_{Guid.NewGuid().ToString().Substring(0,5)}")
            .WithTcpServer(host, port);

        if (!string.IsNullOrEmpty(username))
            builder.WithCredentials(username, password);

        if (useTls)
            builder.WithTls();

        _mqttOptions = builder.Build();
    }

    public async Task StartAsync(CancellationToken token)
    {
        await ConnectAsync();

        //Loop infinito que envia dados a cada 5 segundos
        while (!token.IsCancellationRequested)
        {
            if (!_mqttClient.IsConnected) await ConnectAsync();

            try
            {
                var payload = new
                {
                    ts = DateTimeOffset.UtcNow.ToUnixTimeSeconds(),
                    lat = SimulationState.Latitude,
                    lon = SimulationState.Longitude,
                    soc = SimulationState.Battery,
                    rng = SimulationState.Autonomy,
                    spd = SimulationState.Speed,
                    ign = SimulationState.IsMotorcycleOn,
                    alr = SimulationState.FallDetected ? "FALL" : (SimulationState.TheftAlarmActive ? "THEFT" : "NONE")
                };

                string jsonPayload = JsonSerializer.Serialize(payload);

                var message = new MqttApplicationMessageBuilder()
                    .WithTopic(_topic)
                    .WithPayload(jsonPayload)
                    .WithQualityOfServiceLevel(MQTTnet.Protocol.MqttQualityOfServiceLevel.AtLeastOnce)
                    .Build();

                await _mqttClient.PublishAsync(message, token);
                Console.WriteLine($"[CLOUD] Enviado: {jsonPayload}");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[CLOUD] Erro ao enviar: {ex.Message}");
            }

            await Task.Delay(5000, token); //Envia a cada 5 segundos
        }
    }

    private async Task ConnectAsync()
    {
        try
        {
            await _mqttClient.ConnectAsync(_mqttOptions, CancellationToken.None);
        }
        catch (Exception)
        {}
    }
}