using System;
using System.IO;
using System.Threading;
using System.Threading.Tasks;
using Simulador.Services;

namespace Simulador.Core
{
    public class CameraService
    {
        private readonly MqttService _mqttService;
        private readonly string _imagePath = "camera_frame.jpg"; //Static Image

        public CameraService(MqttService mqttService)
        {
            _mqttService = mqttService;
        }

        public async Task StartStreamingAsync(CancellationToken token)
        {
            if (!File.Exists(_imagePath))
            {
                Console.WriteLine($"[CÂMARA] ERRO: Ficheiro {_imagePath} não encontrado.");
                return;
            }

            Console.WriteLine("[CÂMARA] Streaming falso iniciado...");

            while (!token.IsCancellationRequested)
            {
                try
                {
                    // Lê a imagem e converte para Base64
                    byte[] imageArray = await File.ReadAllBytesAsync(_imagePath, token);
                    string base64Image = Convert.ToBase64String(imageArray);

                    // Publica no tópico
                    await _mqttService.PublishAsync("moto/camera", base64Image);

                    // 1 FPS para não estrangular o broker local
                    await Task.Delay(1000, token);
                }
                catch (TaskCanceledException)
                {
                    break; // Sai limpo se mandares parar
                }
                catch (Exception ex)
                {
                    Console.WriteLine($"[CÂMARA] Erro no streaming: {ex.Message}");
                }
            }
        }
    }
}