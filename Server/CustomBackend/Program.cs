using Microsoft.AspNetCore.Server.Kestrel.Core;
using MQTTnet;
using MQTTnet.Client;
using System.Text.Json;
using motorcycle;
using CustomBackend.Services; 

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

// Mapeia o serviço gRPC (A classe MotorcycleGrpcService deve estar noutro ficheiro do teu projeto)
app.MapGrpcService<MotorcycleGrpcService>();

// 3. ENDPOINT SSE (Web) - AGORA LÊ O DICIONÁRIO FLEXÍVEL
app.MapGet("/api/stream", async (HttpContext context) => {
    context.Response.Headers["Content-Type"] = "text/event-stream";
    context.Response.Headers["Cache-Control"] = "no-cache";
    context.Response.Headers["Connection"] = "keep-alive";

    var channel = System.Threading.Channels.Channel.CreateUnbounded<string>();

    void AoReceberDadosWeb(Dictionary<string, object> estado) {
        var jsonRico = JsonSerializer.Serialize(estado);
        channel.Writer.TryWrite(jsonRico);
    }

    MqttBridgeService.OnNovoEstadoWeb += AoReceberDadosWeb;

    try {
        await foreach (var json in channel.Reader.ReadAllAsync(context.RequestAborted)) {
            await context.Response.WriteAsync($"data: {json}\n\n");
            await context.Response.Body.FlushAsync();
        }
    } 
    finally {
        MqttBridgeService.OnNovoEstadoWeb -= AoReceberDadosWeb;
    }
});

// Força a inicialização do Singleton do MQTT no arranque
app.Services.GetRequiredService<MqttBridgeService>();

app.MapGet("/", () => "Backend C# Ativo: gRPC (50051) + SSE (5000)");

app.Run();


// ==========================================
// PONTE MQTT - GESTÃO DE ESTADO DUAL
// ==========================================
public class MqttBridgeService {
    // ESTADO 1: Estrito para a App Android (gRPC - Não toca no .proto)
    public static TelemetriaData UltimoEstado = new TelemetriaData { Lat = 0, Lon = 0, Alert = "NONE" };
    public static event Action<TelemetriaData>? OnNovoEstado;

    // ESTADO 2: Flexível para a Plataforma Web (SSE)
    public static Dictionary<string, object> WebEstado = new() {
        {"lat", 0.0}, {"lon", 0.0}, {"alert", "NONE"}, {"ign", false}, 
        {"r", 0.0}, {"spd", 0}, {"soc", 0}
    };
    public static event Action<Dictionary<string, object>>? OnNovoEstadoWeb;

    private IMqttClient? _mqttClient;

    public MqttBridgeService() { Task.Run(ConnectMqtt); }

    private async Task ConnectMqtt() {
        var mqttFactory = new MqttFactory();
        _mqttClient = mqttFactory.CreateMqttClient();
        
        // Assegura-te que "mosquitto" é o hostname correto no teu Docker Compose. Se não for, usa o IP "127.0.0.1" ou da rede.
        var options = new MqttClientOptionsBuilder().WithTcpServer("mosquitto", 1883).Build();

        _mqttClient.ApplicationMessageReceivedAsync += e => {
            var payload = System.Text.Encoding.UTF8.GetString(e.ApplicationMessage.PayloadSegment);
            
            try {
                var data = JsonDocument.Parse(payload).RootElement;
                
                // 1. Atualiza a memória rigorosa para o gRPC
                lock(UltimoEstado) {
                    if (data.TryGetProperty("lat", out var lat)) UltimoEstado.Lat = lat.GetDouble();
                    if (data.TryGetProperty("lon", out var lon)) UltimoEstado.Lon = lon.GetDouble();
                    if (data.TryGetProperty("alr", out var alr)) UltimoEstado.Alert = alr.GetString() ?? "NONE";
                    if (data.TryGetProperty("ts", out var ts)) UltimoEstado.Ts = ts.GetInt64();
                    if (data.TryGetProperty("ign", out var ign)) UltimoEstado.Ign = ign.GetBoolean();
                }
                OnNovoEstado?.Invoke(UltimoEstado);

                // 2. Atualiza a memória flexível para a Web (Incluindo Velocidade, Bateria e Inclinação)
                lock(WebEstado) {
                    if (data.TryGetProperty("lat", out var p1)) WebEstado["lat"] = p1.GetDouble();
                    if (data.TryGetProperty("lon", out var p2)) WebEstado["lon"] = p2.GetDouble();
                    if (data.TryGetProperty("alr", out var p3)) WebEstado["alert"] = p3.GetString() ?? "NONE";
                    if (data.TryGetProperty("ign", out var p4)) WebEstado["ign"] = p4.GetBoolean();
                    if (data.TryGetProperty("r", out var p5)) WebEstado["r"] = p5.GetDouble();
                    if (data.TryGetProperty("spd", out var p6)) WebEstado["spd"] = p6.GetDouble();
                    if (data.TryGetProperty("soc", out var p7)) WebEstado["soc"] = p7.GetInt32();
                    
                    // Extra: Injetar FCW se existir no JSON original
                    if (data.TryGetProperty("fcw", out var fcw)) {
                        WebEstado["fcw"] = JsonSerializer.Deserialize<object>(fcw.GetRawText())!;
                    } else if (WebEstado.ContainsKey("fcw")) {
                        WebEstado.Remove("fcw"); // Limpa o FCW se não vier no frame atual
                    }
                }
                OnNovoEstadoWeb?.Invoke(WebEstado);

            } catch (Exception ex) { 
                Console.WriteLine($"[MQTT ERRO] JSON inválido: {ex.Message}"); 
            }
            return Task.CompletedTask;
        };

        await _mqttClient.ConnectAsync(options);
        await _mqttClient.SubscribeAsync("motos/AJP1RB5SXLB123456/telemetria");
    }

    // O gRPC vai chamar isto para enviar o comando de volta para a CSVU
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