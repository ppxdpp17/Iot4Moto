using System;
using System.Threading.Tasks;
using Simulador.Services;
using Simulador.Models;

namespace Simulador.Core
{
    public class BackgroundUpdated
    {
        private readonly MqttService _mqttService;
        private readonly LoggingService _loggingService;
        private readonly StartAndUpdate _startAndUpdate;
        private readonly SensorService _sensorService;

        private readonly UpdateMethods _updateMethods;
        private readonly Dashboard _dashboard;

        private readonly Routes _routes;
        private CancellationTokenSource? _cts;

        private const int MIN_BATTERY = 0;

        private readonly Random rand = new Random();



        public BackgroundUpdated(MqttService mqttService, LoggingService loggingService, SensorService sensorService, CancellationTokenSource? cts, StartAndUpdate startAndUpdate, Dashboard dashboard, UpdateMethods updateMethods, Routes routes)
        {
            _mqttService = mqttService;
            _loggingService = loggingService;
            _sensorService = sensorService;
            _cts = cts;
            _startAndUpdate = startAndUpdate;
            _dashboard = dashboard;
            _updateMethods = updateMethods;
            _routes = routes;
        }

        public async Task AutoSimulateAsync(string routeName)
        {
            _loggingService.Clear();

            Program.WriteMessage($"[Auto] Starting automatic simulation for route '{routeName}'...");

            if (_cts == null || _cts.IsCancellationRequested)
            {
                _cts = new CancellationTokenSource();
            }

            _ = Task.Run(() => _updateMethods.UpdateDashboardContinuouslyAsync(_cts.Token));


            // 1. Start the motorcycle if it's off
            if (!SimulationState.IsMotorcycleOn)
            {
                await _startAndUpdate.StartMotorcycleAsync();
                _dashboard.DisplayDashboard();
                await Task.Delay(500, _cts.Token);
            }

            // 2. Start the route
            await _routes.StartRouteAsync(routeName);
            _dashboard.DisplayDashboard();
            await Task.Delay(500, _cts.Token);
            _ = Task.Run(() => _routes.UpdateRouteAsync(_sensorService));

            // 3. Engage front gear
            if (!SimulationState.Gear.Equals("front", StringComparison.OrdinalIgnoreCase))
            {
                Program.WriteMessage("[Auto] Engaging front gear...");
                await _updateMethods.UpdateGearAsync("front");
                _dashboard.DisplayDashboard();
                await Task.Delay(500, _cts.Token);

            }

            // 4. Turn on lights
            Program.WriteMessage("[Auto] Turning on lights...");
            await _updateMethods.UpdateLightsAsync("on");
            _dashboard.DisplayDashboard();

            // >>> Adjust the initial acceleration according to the route
            if (_routes._routeConfigs.TryGetValue(routeName, out var config))
            {
                int desiredSpeed;
                if (routeName.Equals("rota2", StringComparison.OrdinalIgnoreCase))
                {
                    // Para rota2 (autoestrada) queremos iniciar em 110 km/h
                    desiredSpeed = 110;
                }
                else
                {
                    // For other routes use the minimum between the configured maxSpeed and 60 km/h
                    desiredSpeed = (int)Math.Min(config.maxSpeed, 60);
                }
                Program.WriteMessage($"[Auto] Accelerating to {desiredSpeed} km/h...");
                string speedCommand = desiredSpeed > SimulationState.Speed
                    ? "+" + (desiredSpeed - SimulationState.Speed)
                    : "-" + (SimulationState.Speed - desiredSpeed);
                await _updateMethods.UpdateSpeedAsync(speedCommand);

            }
            else
            {
                // If the route is not in the dictionary, keep the default value
                Program.WriteMessage("[Auto] Route not configured, using default => 60 km/h");
                await _updateMethods.UpdateSpeedAsync("+60");
            }

            await Task.Delay(1000, _cts.Token);

            // Adjust driving mode
            // Example: if it's route2 force sport; if it's route1 force eco; etc.
            if (routeName == "rota1")
            {
                Program.WriteMessage("[Auto] Route1 => forcing 'eco' mode (urban)...");
                await _updateMethods.UpdateModeAsync("eco");
                _dashboard.DisplayDashboard();
            }
            else if (routeName == "rota2")
            {
                Program.WriteMessage("[Auto] Route2 => forcing 'sport' mode (highway)...");
                await _updateMethods.UpdateModeAsync("sport");
                _dashboard.DisplayDashboard();
            }
            else
            {
                Program.WriteMessage("[Auto] Route3 => forcing 'standard' mode (rural)...");
                await _updateMethods.UpdateModeAsync("standard");
                _dashboard.DisplayDashboard();
            }

            // Loop enquanto a rota estiver ativa
            while (_routes._isRouteActive && !_cts.IsCancellationRequested)
            {

                // Example: if it's rota2 (highway), try to maintain a high speed
                if (routeName == "rota2" && SimulationState.Speed < 80)
                {
                    int dif = 80 - SimulationState.Speed;
                    Program.WriteMessage("[Auto] Route2 => Increasing target speed to 80 km/h...");
                    await _updateMethods.UpdateSpeedAsync("+" + dif);
                }

                // if this is rota1 and speed > 50, slow down
                if (routeName == "rota1" && SimulationState.Speed > 50)
                {
                    int dif = SimulationState.Speed - 50;
                    Program.WriteMessage("[Auto] Route1 => Reducing to 50 km/h in city traffic...");
                    await _updateMethods.UpdateSpeedAsync("-" + dif);
                }

                // if this is rota3 and speed > 70, slow down
                if (routeName == "rota3" && SimulationState.Speed > 70)
                {
                    int dif = SimulationState.Speed - 70;
                    Program.WriteMessage("[Auto] Route3 => Rural segment => Reducing to 70 km/h...");
                    await _updateMethods.UpdateSpeedAsync("-" + dif);
                }




                await Task.Delay(2000, _cts.Token);
            }

            if (_routes._isRouteActive)
            {
                Program.WriteMessage("[Auto] Route still running. Simulation must not stop prematurely.");
            }
            else
            {
                Program.WriteMessage("[Auto] Route finished. Switching to standard mode and turning the motorcycle off...");
                await _updateMethods.UpdateModeAsync("standard");

                if (SimulationState.IsMotorcycleOn) // Avoid shutting down if the motorcycle is still active
                {
                    await _startAndUpdate.StopMotorcycleAsync();
                }
                await _routes.StopRouteAsync();

                // Save the last motorcycle state in the log
                _loggingService.AddPerformanceLog(_routes.CreateLogEntry());

                // Gerar o ficheiro com o nome da rota e timestamp
                string logFileName = $"Log_{_routes._activeRouteName}_{DateTime.Now:yyyyMMdd_HHmmss}.json";
                _loggingService.SaveLogsToFile(logFileName);

                Program.WriteMessage($"[LOG] Ficheiro de registo gerado: {logFileName}");

                // Reads environment variables (already loaded by Env.TraversePath().Load() in Program.cs)
                var pgHost  = Environment.GetEnvironmentVariable("PG_HOST");

                int pgPort = int.TryParse(Environment.GetEnvironmentVariable("PG_PORT"), out var portTmp)
             ? portTmp
             : 5432;   // valor-defeito

                var pgDb    = Environment.GetEnvironmentVariable("PG_DB");
                var pgUser  = Environment.GetEnvironmentVariable("PG_USER");
                var pgPass   = Environment.GetEnvironmentVariable("PG_PASSWORD");

                // Guardar logs no PostgreSQL
                var pgLogger = new PostgresLogger("172.20.0.201", 5432, "rotasimulador", "simuser", "Simulador");

                foreach (var entry in _loggingService.GetAllLogs())
                {
                    pgLogger.InsertLog(entry, routeName);
                }
                Console.Clear();
                _dashboard.DisplayDashboard();
            }

        }


        public async Task UpdateTimeAsync(CancellationToken cancellationToken)
        {
            while (!cancellationToken.IsCancellationRequested)
            {
                var currentTime = DateTime.Now.ToString("hh:mm tt");
                await _mqttService.PublishAsync("sim/time", currentTime);

                _loggingService.AddPerformanceLog(CreateLogEntry());

                try
                {
                    await Task.Delay(60000, cancellationToken); // Atualizar a cada minuto
                }
                catch (TaskCanceledException)
                {
                    break;
                }
            }
        }

        public async Task UpdateTotalKilometersAsync(CancellationToken cancellationToken)
        {
            while (!cancellationToken.IsCancellationRequested)
            {
                if (SimulationState.IsMotorcycleOn)
                {
                    // convert km/h to km per minute
                    SimulationState.TotalKilometers += SimulationState.Speed / 60.0;
                    await _mqttService.PublishAsync("sim/total_kilometers", SimulationState.TotalKilometers.ToString("F2"));

                    _loggingService.AddPerformanceLog(CreateLogEntry());
                }

                try
                {
                    await Task.Delay(60000, cancellationToken); // Atualizar a cada minuto
                }
                catch (TaskCanceledException)
                {
                    break;
                }
            }
        }

        public async Task UpdateAutonomyAsync(CancellationToken cancellationToken)
        {
            while (!cancellationToken.IsCancellationRequested)
            {
                if (SimulationState.IsMotorcycleOn)
                {
                    if (SimulationState.Speed > 0)
                    {
                        SimulationState.Autonomy = SimulationState.Battery * 10.0 / SimulationState.Speed;
                        await _mqttService.PublishAsync("sim/autonomy", SimulationState.Autonomy.ToString("F2"));
                    }
                    else
                    {
                        // Parado => "infinita"
                        await _mqttService.PublishAsync("sim/autonomy", "∞");
                    }

                    // Publicar novamente para garantir
                    await _mqttService.PublishAsync("sim/autonomy", SimulationState.Autonomy.ToString("F2"));

                    _loggingService.AddPerformanceLog(CreateLogEntry());
                }

                try
                {
                    await Task.Delay(60000, cancellationToken); // Atualizar a cada minuto
                }
                catch (TaskCanceledException)
                {
                    break;
                }
            }
        }

        // Method to simulate natural speed fluctuations
        public async Task FluctuateSpeedAsync(CancellationToken cancellationToken)
        {

            while (!cancellationToken.IsCancellationRequested)
            {
                // If the bike is on, not charging, and has speed > 0:
                if (SimulationState.IsMotorcycleOn && !SimulationState.IsCharging && SimulationState.Speed > 0)
                {
                    // Generate a random delta between -2 and +2 km/h (adjust if needed)
                    int delta = rand.Next(-2, 3); // -2, -1, 0, 1 ou 2
                    // Apply the variation, ensuring the speed does not go negative
                    int newSpeed = Math.Max(SimulationState.Speed + delta, 0);
                    // Update the speed (you can also clamp it to a maximum value if required)
                    SimulationState.Speed = newSpeed;
                    _dashboard.DisplayDashboard();
                            // Publish the new speed
                    await _mqttService.PublishAsync("sim/speed", SimulationState.Speed.ToString());
                }
                // Aguarda 1 segundo antes de repetir
                await Task.Delay(1000, cancellationToken);
            }
        }


        public async Task UpdateBatteryConsumptionAsync(CancellationToken cancellationToken)
        {
            while (!cancellationToken.IsCancellationRequested)
            {
                try
                {
                    if (SimulationState.IsMotorcycleOn && !SimulationState.IsCharging)
                    {
                        // 1. Base consumption depending on the drive mode
                        double baseConsumptionRate;
                        switch (SimulationState.DriveMode.ToLower())
                        {
                            case "eco":
                                baseConsumptionRate = 0.2;
                                break;
                            case "sport":
                                baseConsumptionRate = 1.0;
                                break;
                            default:
                                baseConsumptionRate = 0.5;
                                break;
                        }

                        // 2. Fator extra definido pela rota
                        double extraFactor = 1.0;
                        if (_routes._routeConfigs.TryGetValue(_routes._activeRouteName, out var config))
                        {
                            extraFactor = config.extraConsumption;
                        }

                        // 3. Calculate base consumption (per-minute consumption proportional to speed)
                        double consumptionThisMinute = baseConsumptionRate * (SimulationState.Speed / 60.0) * extraFactor;

                        // 4. If we are on route3, incorporate the slope effect
                        double slopeFactor = 1.0;
                        if (_routes._activeRouteName.Equals("rota3", StringComparison.OrdinalIgnoreCase))
                        {
                            if (_routes._segmentIndex < _routes._currentRouteSegments.Count)
                            {
                                double slope = _routes._currentRouteSegments[_routes._segmentIndex].Slope;
                                slopeFactor = 1.0 + slope;
                                if (slopeFactor < 0.5)
                                {
                                    slopeFactor = 0.5;
                                }
                            }
                        }
                        consumptionThisMinute *= slopeFactor;

                        // 5. Calcular o consumo para o ciclo (a cada 5 segundos)
                        int batteryConsumed = (int)Math.Round(consumptionThisMinute);
                        int newBattery = Math.Max(SimulationState.Battery - batteryConsumed, MIN_BATTERY);
                        if (newBattery != SimulationState.Battery)
                        {
                            SimulationState.Battery = newBattery;
                            await _mqttService.PublishAsync("sim/battery", SimulationState.Battery.ToString());
                            Program.WriteMessage($"[Consumo] Bateria => {SimulationState.Battery}% (speed={SimulationState.Speed} km/h, mode={SimulationState.DriveMode}, route={_routes._activeRouteName}, slopeFactor={slopeFactor:0.00}).");
                            _loggingService.AddPerformanceLog(CreateLogEntry());
                        }

                        // 6. Check if the battery is depleted
                        if (SimulationState.Battery <= MIN_BATTERY)
                        {
                            Program.WriteMessage("[ALERT] Low battery! Motorcycle in safe mode.");

                            if (_routes._isRouteActive)
                            {
                                Program.WriteMessage("[ALERT] The motorcycle is on an active route, reducing speed to save battery.");
                                SimulationState.Speed = Math.Max(SimulationState.Speed - 10, 0);
                            }
                            else
                            {
                                Program.WriteMessage("[ALERT] Battery depleted! Shutting down motorcycle...");
                                SimulationState.Speed = 0;
                                await _mqttService.PublishAsync("sim/speed", "0");
                                SimulationState.IsMotorcycleOn = false;  // Only shut down if not in an active route
                            }
                        }

                    }

                    // Wait 5 seconds before the next cycle
                    await Task.Delay(5000, cancellationToken);
                }
                catch (TaskCanceledException)
                {
                    break;
                }
            }
        }


        public LogEntry CreateLogEntry()
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
