using CustomBackend.Services;
using Microsoft.AspNetCore.Server.Kestrel.Core;
using MQTTnet;
using MQTTnet.Client;
using System.Text.Json;
using motorcycle; 

var builder = WebApplication.CreateBuilder(args);

// 1. ABRIR DUAS PORTAS: 50051 (gRPC) e 5000 (SSE Web)
builder.WebHost.ConfigureKestrel(options => {
    options.ListenAnyIP(50051, o => o.Protocols = HttpProtocols.Http2);
    options.ListenAnyIP(5000, o => o.Protocols = HttpProtocols.Http1AndHttp2);
});

builder.Services.AddGrpc();
builder.Services.AddSingleton<MqttBridgeService>();

// 2. PERMISSÕES DE REDE (CORS)
builder.Services.AddCors(options => {
    options.AddPolicy("AllowWeb", policy => policy.AllowAnyOrigin().AllowAnyHeader().AllowAnyMethod());
});

var app = builder.Build();

app.UseCors("AllowWeb");
app.MapGrpcService<MotorcycleGrpcService>();

// 3. ENDPOINT SSE (Web)
app.MapGet("/api/stream", async (HttpContext context) => {
    // Correção dos warnings ASP0019 (usando os indexers em vez de Add)
    context.Response.Headers["Content-Type"] = "text/event-stream";
    context.Response.Headers["Cache-Control"] = "no-cache";
    context.Response.Headers["Connection"] = "keep-alive";

    //Cria o tubo (Channel)
    var channel = System.Threading.Channels.Channel.CreateUnbounded<string>();

    //Define a função que empurra os dados para o tubo
    void AoReceberDados(string novoJson) {
        channel.Writer.TryWrite(novoJson);
    }

    //Subscreve ao evento do Singleton
    MqttBridgeService.OnNovoJson += AoReceberDados;

    try {
        //O loop fica a dormir. O CPU fica a 0%. 
        //Quando entra um JSON no tubo, ele acorda, escreve na Web, e volta a dormir.
        await foreach (var json in channel.Reader.ReadAllAsync(context.RequestAborted)) {
            await context.Response.WriteAsync($"data: {json}\n\n");
            await context.Response.Body.FlushAsync();
        }
    } 
    finally {
        //IMPORTANTE: Se o utilizador fechar a página Web, remove a subscrição para não esgotar a RAM
        MqttBridgeService.OnNovoJson -= AoReceberDados;
    }
});

app.Services.GetRequiredService<MqttBridgeService>();
app.MapGet("/", () => "Backend C# Ativo: gRPC (50051) + SSE (5000)");

app.Run();

// --- PONTE MQTT ---
public class MqttBridgeService {
    public static TelemetriaData UltimoEstado = new TelemetriaData { Lat = 0, Lon = 0, Alert = "NONE" };
    
    // O Evento (a campainha) que vai avisar o SSE e a App que há dados novos
    public static event Action<string> OnNovoJson; 
    public static event Action<TelemetriaData> OnNovoEstado;

    private IMqttClient _mqttClient; // Guardamos o cliente aqui!

    public MqttBridgeService() { Task.Run(ConnectMqtt); }

    private async Task ConnectMqtt() {
        var mqttFactory = new MqttFactory();
        _mqttClient = mqttFactory.CreateMqttClient();
        var options = new MqttClientOptionsBuilder().WithTcpServer("mosquitto", 1883).Build();

        _mqttClient.ApplicationMessageReceivedAsync += e => {
            var payload = System.Text.Encoding.UTF8.GetString(e.ApplicationMessage.PayloadSegment);
            
            // Dispara o evento em vez de gravar numa variável morta
            OnNovoJson?.Invoke(payload); 
            
            try {
                var data = JsonDocument.Parse(payload).RootElement;
                
                // Bloqueia a memória para evitar conflitos de leitura do gRPC
                lock(UltimoEstado) {
                    if (data.TryGetProperty("lat", out var latProp)) UltimoEstado.Lat = latProp.GetDouble();
                    if (data.TryGetProperty("lon", out var lonProp)) UltimoEstado.Lon = lonProp.GetDouble();
                    if (data.TryGetProperty("alr", out var alrProp)) UltimoEstado.Alert = alrProp.GetString() ?? "NONE";
                    if (data.TryGetProperty("ts", out var tsProp)) UltimoEstado.Ts = tsProp.GetInt64();
                    
                    // Extração correta do estado da ignição
                    if (data.TryGetProperty("ign", out var ignProp)) {
                        UltimoEstado.Ign = ignProp.GetBoolean();
                    }
                }
                OnNovoEstado?.Invoke(UltimoEstado);
            } catch (Exception ex) { 
                Console.WriteLine($"[MQTT ERRO] JSON inválido: {ex.Message}"); 
            }
            return Task.CompletedTask;
        };

        await mqttClient.ConnectAsync(options);
        await mqttClient.SubscribeAsync("motos/AJP1RB5SXLB123456/telemetria");
    }

    //O gRPC vai chamar isto para enviar o comando pela ligação partilhada
    public async Task PublicarComandoAsync(string topic, string payload) {
        if (_mqttClient != null && _mqttClient.IsConnected) {
            var msg = new MqttApplicationMessageBuilder()
                .WithTopic(topic)
                .WithPayload(payload)
                .WithQualityOfServiceLevel(MQTTnet.Protocol.MqttQualityOfServiceLevel.AtLeastOnce)
                .Build();
            await _mqttClient.PublishAsync(msg);
        }
    }
}