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
                if (!SimulationState.IsRainEnabled) { 
                    await Task.Delay(1000, token); 
                    continue; 
                }

                if (!SimulationState.IsMotorcycleOn)
                {
                    var isWet = _gpio.Read(RAIN_PIN) == PinValue.High;

                    if (isWet && !_isRaining) 
                    {
                        _isRaining = true;
                        _canService.EnviarDadosSimulados(0x401, new byte[] { 0x01 });
                    }
                    else if (!isWet && _isRaining)
                    {
                        _isRaining = false;
                        //Envia 0x00 para sinalizar que o perigo passou
                        _canService.EnviarDadosSimulados(0x401, new byte[] { 0x00 }); 
                    }
                }
                
                //Intervalo de 1 minuto
                await Task.Delay(60000, token); 
            }
    }
}