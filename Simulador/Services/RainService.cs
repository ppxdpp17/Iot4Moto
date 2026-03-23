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
                //Só verifica se a mota estiver desligada
                if (!SimulationState.IsMotorcycleOn)
                {
                    //Se o sensor detetar água suficiente para passar os ~1.8V
                    if (_gpio.Read(RAIN_PIN) == PinValue.High) 
                    {
                        //Program.WriteMessage("\n[SENTINELA] Humidade detetada na mota!");
                        
                        //Envia para o barramento CAN (ID 0x401)
                        _canService.EnviarDadosSimulados(0x401, new byte[] { 0x01 });

                        //Cooldown de 20 segundos para não encher o smartphone de notificações
                        await Task.Delay(20000, token); 
                    }
                }
                await Task.Delay(500, token);
            }
        }
    }
}