using System;
using System.Threading.Tasks;
using Simulador.Services;
using Simulador.Models;
using Simulador.Helpers;

namespace Simulador.Core
{
    public class UpdateMethods
    {
        private readonly MqttService _mqttService;
        private readonly LoggingService _loggingService;

        private readonly Dashboard _dashboard;

        private readonly Auxiliares _auxiliares;

        private Routes _routes;

        private readonly CAN.CanService _canService;


        private const int MAX_BATTERY = 100;
        private const int MIN_BATTERY = 0;

        private const int MAX_SPEED = 200;
        private const int MIN_SPEED = 0;

        private const int MAX_TEMPERATURE = 100;
        private const int MIN_TEMPERATURE = -50;

        private int _previousSpeed = 0;

        private static readonly object _consoleLock = new object();

        private static int DashboardStartRow = 0;

        public UpdateMethods(MqttService mqttService, LoggingService loggingService, Dashboard dashboard, Auxiliares auxiliares, Routes routes, CAN.CanService canService)
        {
            _mqttService = mqttService;
            _loggingService = loggingService;
            _dashboard = dashboard;
            _auxiliares = auxiliares;
            _routes = routes;
            _canService = canService;
        }

        public void SetRoutes(Routes routes)
        {
            _routes = routes;
        }


        public async Task UpdateDashboardContinuouslyAsync(CancellationToken cancellationToken)
        {
            while (!cancellationToken.IsCancellationRequested)
            {
                lock (_consoleLock)
                {
                    // Save cursor position before updating
                    int cursorLeft = Console.CursorLeft;
                    int cursorTop = Console.CursorTop;

                    // Evita que o dashboard limpe o prompt ao atualizar
                    if (SimulationState.IsMotorcycleOn)
                    {
                        Console.SetCursorPosition(0, DashboardStartRow);
                        _dashboard.DisplayDashboard();
                    }

                    // Restore cursor position to where it was
                    Console.SetCursorPosition(cursorLeft, cursorTop);
                }
                await Task.Delay(500, cancellationToken);
            }

        }


        public async Task UpdateBatteryAsync(string command)
        {
            if (!CommandParser.TryParseCommand(command, out int delta))
            {
                Program.WriteMessage("Usage: battery -<value>");
                return;
            }

            int finalValue = SimulationState.Battery + delta;
            finalValue = Math.Clamp(finalValue, MIN_BATTERY, MAX_BATTERY);

            Program.WriteMessage($"Changing battery from {SimulationState.Battery}% to {finalValue}% over 5 seconds...");
            await _auxiliares.AnimateValueChangeAsync(() => SimulationState.Battery, v => SimulationState.Battery = v, finalValue,
                                          5000, "sim/battery", "battery", _mqttService, _loggingService);
        }

        public async Task UpdateSpeedAsync(string command)
        {
            // Check whether the current gear allows changing speed
            if (SimulationState.Gear.Equals("neutral", StringComparison.OrdinalIgnoreCase) ||
                SimulationState.Gear.Equals("park", StringComparison.OrdinalIgnoreCase))
            {
                Program.WriteMessage("Cannot change speed while the gear is 'neutral' or 'park'.");
                return;
            }

            if (SimulationState.Gear.Equals("neutral", StringComparison.OrdinalIgnoreCase) && SimulationState.Speed > 20)
            {
                Program.WriteMessage("[AUTO-GEAR] Speed above 20 km/h while in neutral. Switching to front.");
                await UpdateGearAsync("front");
            }


            if (!CommandParser.TryParseCommand(command, out int delta))
            {
                Program.WriteMessage("Usage: speed +<value> or speed -<value>");
                return;
            }
            else if (SimulationState.Gear.Equals("back", StringComparison.OrdinalIgnoreCase) && delta > 0)
            {
                Program.WriteMessage("Cannot increase speed while the gear is 'back'.");
                return;
            }

            int targetSpeed = SimulationState.Speed + delta;
            targetSpeed = Math.Clamp(targetSpeed, MIN_SPEED, MAX_SPEED);

            Program.WriteMessage($"About to {(delta > 0 ? "accelerate" : "decelerate")} from {SimulationState.Speed} km/h to {targetSpeed} km/h...");

            await _auxiliares.ApplyAccelerationAsync(targetSpeed);

            // After acceleration, if ABS is off and speed dropped sharply, display an alert
            if (!SimulationState.Abs) // ABS is off
            {
                int speedDrop = _previousSpeed - SimulationState.Speed;
                if (speedDrop >= 30)
                {
                    Program.WriteMessage("[ABS ALERT] Possible skid detected due to heavy braking!");
                    // Trigger a sensor or extra log if needed
                }
            }
            _previousSpeed = SimulationState.Speed;

            if (_routes._routeConfigs.TryGetValue(_routes._activeRouteName, out var config))
            {
                int routeMaxSpeed = config.maxSpeed;
                if (SimulationState.Speed > routeMaxSpeed)
                {
                    Program.WriteMessage($"[LIMIT] Exceeded maxSpeed for {_routes._activeRouteName} (limit={routeMaxSpeed}). Forcing {SimulationState.Speed} => {routeMaxSpeed}");
                    Program.WriteMessage($"[LIMIT] Surpassed {routeMaxSpeed} km/h on {_routes._activeRouteName}. Applying automatic braking...");
                    await _auxiliares.ApplyBrakingAsync(routeMaxSpeed);
                    Program.WriteMessage($"[LIMIT] Speed corrected to {SimulationState.Speed} km/h.");

                    await _mqttService.PublishAsync("sim/speed", SimulationState.Speed.ToString());
                    


                    var logEntry = new LogEntry
                    {
                        Timestamp = DateTime.Now,
                        Topic = "sim/speed",
                        Payload = SimulationState.Speed.ToString(),
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

                    _loggingService.AddPerformanceLog(logEntry);
                }

        // If rota2 speed exceeds 120 km/h show an alert
        if (_routes._activeRouteName == "rota2" && SimulationState.Speed > 120)
        {
            Program.WriteMessage("[ALERT] Exceeded limit (120 km/h) on rota2!");
        }
            }
        }

        public async Task UpdateTemperatureAsync(string command)
        {
            if (!CommandParser.TryParseCommand(command, out int delta))
            {
                Program.WriteMessage("Usage: temp +<value> or temp -<value>");
                return;
            }

            int finalValue = SimulationState.Temperature + delta;
            finalValue = Math.Clamp(finalValue, MIN_TEMPERATURE, MAX_TEMPERATURE);

            Program.WriteMessage($"Changing temperature from {SimulationState.Temperature} C to {finalValue} C over 5 seconds...");
            await _auxiliares.AnimateValueChangeAsync(() => SimulationState.Temperature, v => SimulationState.Temperature = v, finalValue,
                                          5000, "sim/temperature", "temperature", _mqttService, _loggingService);
        }

        public async Task UpdateGearAsync(string position)
        {
            position = position.ToLower();
            if (position == "front" || position == "back" || position == "neutral")
            {
                await _auxiliares.ApplyStateChangeAsync(() => SimulationState.Gear = position,
                                            "Gear", "sim/gear", position,
                                            _mqttService, _loggingService);
                _canService.EnviarGear(position);
            }
            else if (position == "park")
            {
                if (SimulationState.Speed == 0)
                {
                    await _auxiliares.ApplyStateChangeAsync(() => SimulationState.Gear = position,
                                                "Gear", "sim/gear", position,
                                                _mqttService, _loggingService);
                    _canService.EnviarGear(position);
                }
                else
                {
                    Program.WriteMessage("Cannot switch to 'park' while speed is not 0 km/h.");
                }
            }
            else
            {
                Program.WriteMessage("Usage: gear front | gear back | gear neutral | gear park");
            }
        }



        public async Task UpdateIndicatorAsync(string direction)
        {
            direction = direction.ToLower();
            bool left = SimulationState.IndicatorLeft;
            bool right = SimulationState.IndicatorRight;

            if (direction == "left")
            {
                left = true;
                right = false;
            }
            else if (direction == "right")
            {
                left = false;
                right = true;
            }
            else if (direction == "none")
            {
                left = false;
                right = false;
            }
            else
            {
                Program.WriteMessage("Usage: indicator left | indicator right | indicator none");
                return;
            }

            // Atualizar indicadores imediatamente
            await _auxiliares.ApplyStateChangeAsync(() =>
            {
                SimulationState.IndicatorLeft = left;
                SimulationState.IndicatorRight = right;
            },
            "Pisca Esquerdo", "sim/indicator_left", left.ToString(),
            _mqttService, _loggingService);

            await _auxiliares.ApplyStateChangeAsync(() =>
            {
                SimulationState.IndicatorLeft = left;
                SimulationState.IndicatorRight = right;
            },
            "Pisca Direito", "sim/indicator_right", right.ToString(),
            _mqttService, _loggingService);
        }

        public async Task UpdateLightsAsync(string state)
        {
            state = state.ToLower();
            bool newLights;
            if (state == "on")
            {
                newLights = true;
            }
            else if (state == "off")
            {
                newLights = false;
            }
            else
            {
                Program.WriteMessage("Usage: lights on | lights off");
                return;
            }

            await _auxiliares.ApplyStateChangeAsync(() => SimulationState.Lights = newLights,
                                        "Low beam lights", "sim/lights", newLights.ToString(),
                                        _mqttService, _loggingService);
            _canService.EnviarLuzes();
        }

        public async Task UpdateAbsAsync(string state)
        {
            state = state.ToLower();
            bool newAbs;
            if (state == "on")
            {
                newAbs = true;
            }
            else if (state == "off")
            {
                newAbs = false;
            }
            else
            {
                Program.WriteMessage("Usage: abs on | abs off");
                return;
            }

            await _auxiliares.ApplyStateChangeAsync(() => SimulationState.Abs = newAbs,
                                        "ABS", "sim/abs", newAbs.ToString(),
                                        _mqttService, _loggingService);
        }

        public async Task UpdateModeAsync(string mode)
        {
            mode = mode.ToLower();
            if (mode == "standard" || mode == "eco" || mode == "sport")
            {
                await _auxiliares.ApplyStateChangeAsync(() => SimulationState.DriveMode = mode,
                                            "Drive mode", "sim/drive_mode", mode,
                                            _mqttService, _loggingService);
                _canService.EnviarModo(mode);
            }
            else
            {
                Program.WriteMessage("Usage: mode standard | mode eco | mode sport");
            }
        }

        public async Task UpdateMaxLightsAsync(string state)
        {
            state = state.ToLower();
            bool newMax;
            if (state == "on")
            {
                newMax = true;
            }
            else if (state == "off")
            {
                newMax = false;
            }
            else
            {
                Program.WriteMessage("Usage: maxlights on | maxlights off");
                return;
            }

            await _auxiliares.ApplyStateChangeAsync(() => SimulationState.MaxLights = newMax,
                                        "High beam lights", "sim/maxlights", newMax.ToString(),
                                        _mqttService, _loggingService);
        }

        public async Task UpdateDangerAsync(string state)
        {
            state = state.ToLower();
            bool newDanger;
            if (state == "on")
            {
                newDanger = true;
            }
            else if (state == "off")
            {
                newDanger = false;
            }
            else
            {
                Program.WriteMessage("Usage: danger on | danger off");
                return;
            }

            await _auxiliares.ApplyStateChangeAsync(() => SimulationState.DangerLights = newDanger,
                                        "Hazard lights", "sim/danger-lights", newDanger.ToString(),
                                        _mqttService, _loggingService);
        }

        public async Task AutoActivateIndicatorAsync(string direction, int durationMs)
        {
            // Turn the indicator on (left or right)
            await UpdateIndicatorAsync(direction);
            // Wait for the requested duration (e.g., 3000 ms)
            await Task.Delay(durationMs);
            // Turn the indicator off
            await UpdateIndicatorAsync("none");
        }

        public async Task ApplyBrakeCommandAsync(string command)
        {
            if (!int.TryParse(command, out int value) || value <= 0)
            {
                Program.WriteMessage("Usage: brake <positive value> (e.g., brake 20)");
                return;
            }

            int targetSpeed = Math.Max(SimulationState.Speed - value, 0);
            Program.WriteMessage($"Braking from {SimulationState.Speed} km/h to {targetSpeed} km/h...");
            await _auxiliares.ApplyBrakingAsync(targetSpeed);
        }




    }
}
