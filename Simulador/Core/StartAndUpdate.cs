// StartAndUpdate.cs
using System;
using System.Threading.Tasks;
using Simulador.Services;
using Simulador.Models;

namespace Simulador.Core
{
    public class StartAndUpdate
    {
        private readonly MqttService _mqttService;
        private readonly LoggingService _loggingService;

        private Auxiliares _auxiliares;

        public StartAndUpdate(MqttService mqttService, LoggingService loggingService, Auxiliares auxiliares)
        {
            _mqttService = mqttService;
            _loggingService = loggingService;
            _auxiliares = auxiliares;
        }

        public void SetAux(Auxiliares auxiliares)
        {
            _auxiliares = auxiliares;
        }

        public async Task StartMotorcycleAsync()
        {
            if (SimulationState.IsMotorcycleOn)
            {
                Console.WriteLine("The motorcycle is already on.");
                return;
            }

            SimulationState.IsMotorcycleOn = true;
            Console.WriteLine("Motorcycle started.");

            await _mqttService.PublishAsync("sim/motorcycle", "on");
            await _mqttService.PublishAsync("sim/collision", "false");
            SimulationState.Lights = true;
            Console.WriteLine("Lights turned on automatically.");
            await _mqttService.PublishAsync("sim/lights", "true");
            await _mqttService.PublishAsync("sim/battery", SimulationState.Battery.ToString());
            await _mqttService.PublishAsync("sim/total_kilometers", SimulationState.TotalKilometers.ToString("F2"));
            await _mqttService.PublishAsync("sim/gps/latitude", SimulationState.Latitude.ToString("F6"));
            await _mqttService.PublishAsync("sim/gps/longitude", SimulationState.Longitude.ToString("F6"));
            _loggingService.AddPerformanceLog(CreateLogEntry());

            await _auxiliares.PublishAllStatesAsync();
        }

        public async Task StopMotorcycleAsync()
        {
            Console.WriteLine("[DEBUG] StopMotorcycleAsync invoked!");
            await Task.Delay(10000);
            if (!SimulationState.IsMotorcycleOn)
            {
                Console.WriteLine("The motorcycle is already off.");
                return;
            }

            SimulationState.IsMotorcycleOn = false;
            Console.WriteLine("Motorcycle turned off.");

            await _mqttService.PublishAsync("sim/motorcycle", "off");
            SimulationState.Lights = false;
            Console.WriteLine("Lights turned off automatically.");
            await _mqttService.PublishAsync("sim/lights", "false");
            SimulationState.Speed = 0;
            Console.WriteLine("Speed set to 0 km/h.");
            await _mqttService.PublishAsync("sim/speed", "0");
            _loggingService.AddPerformanceLog(CreateLogEntry());
        }

        private LogEntry CreateLogEntry()
        {
            return new LogEntry
            {
                Timestamp = DateTime.Now,
                MessageType = "Performance",
                Battery = SimulationState.Battery,
                Speed = SimulationState.Speed,
                Temperature = SimulationState.Temperature,
                Gear = SimulationState.Gear,
                Lights = SimulationState.Lights,
                Abs = SimulationState.Abs,
                IndicatorLeft = SimulationState.IndicatorLeft,
                IndicatorRight = SimulationState.IndicatorRight,
                DriveMode = SimulationState.DriveMode,
                MaxLights = SimulationState.MaxLights,
                DangerLights = SimulationState.DangerLights,
                TotalKilometers = SimulationState.TotalKilometers,
                Autonomy = SimulationState.Autonomy
            };
        }
    }
}
