using System;
using System.Device.Gpio;
using System.Threading;
using System.Threading.Tasks;
using CAN;
using Simulador.Core;

namespace Simulador.Services
{
    public class RainService
    {
        private const int RAIN_PIN = 17; 
        private readonly CanService _canService;
        private GpioController _gpio;
        private bool _isRaining = false;
        private bool _arranque = true; // Garante o silêncio no primeiro arranque

        public RainService(CanService canService)
        {
            _canService = canService;
            _gpio = new GpioController(); 
            _gpio.OpenPin(RAIN_PIN, PinMode.Input);
        }

        public async Task StartAsync(CancellationToken token)
        {
            while (!token.IsCancellationRequested)
            {
                // A mota só avalia a chuva se estiver estacionada (desligada)
                if (!SimulationState.IsMotorcycleOn)
                {
                    var isWet = _gpio.Read(RAIN_PIN) == PinValue.High;

                    // 1. Fase de Calibração Inicial: Absorve o estado atual sem enviar CAN
                    if (_arranque)
                    {
                        _isRaining = isWet;
                        _arranque = false;
                        await Task.Delay(2000, token);
                        continue; // Salta para a próxima iteração
                    }

                    // 2. Deteção de Mudança de Estado (Edge Detection)
                    if (isWet && !_isRaining) 
                    {
                        _isRaining = true;
                        _canService.EnviarDadosSimulados(0x401, new byte[] { 0x01 });
                    }
                    else if (!isWet && _isRaining)
                    {
                        _isRaining = false;
                        _canService.EnviarDadosSimulados(0x401, new byte[] { 0x00 }); 
                    }
                }
                
                // Intervalo de 2 segundos de amostragem
                await Task.Delay(2000, token); 
            }
        }
    }
}