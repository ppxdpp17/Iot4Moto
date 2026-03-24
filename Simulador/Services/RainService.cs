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
                //A mota só avalia a chuva se estiver estacionada
                if (!SimulationState.IsMotorcycleOn)
                {
                    var isWet = _gpio.Read(RAIN_PIN) == PinValue.High;

                    if (isWet && !_isRaining) 
                    {
                        _isRaining = true;
                        //Program.WriteMessage("\n[SENTINELA] Chuva detetada!");
                        _canService.EnviarDadosSimulados(0x401, new byte[] { 0x01 });
                    }
                    else if (!isWet && _isRaining)
                    {
                        _isRaining = false;
                        //Program.WriteMessage("\n[SENTINELA] Sensor seco. Alerta desativado.");
                        //Envia 0x00 para sinalizar que o perigo passou
                        _canService.EnviarDadosSimulados(0x401, new byte[] { 0x00 }); 
                    }
                }
                
                //Intervalo rápido de 2 segundos. 
                //A máquina de estados (variável _isRaining) garante que não há spam na rede.
                await Task.Delay(2000, token); 
            }
        }
    }
}