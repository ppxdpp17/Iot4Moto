using System;
using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;
using Simulador.Models;
using Simulador.Services;
using Simulador.Helpers;
using Simulador.Core;
using CAN;
using DotNetEnv;

namespace Simulador
{
    public class RouteSegment
    {
        public double LatStart { get; set; }
        public double LngStart { get; set; }
        public double LatEnd { get; set; }
        public double LngEnd { get; set; }
        public double Distance { get; set; } // em km
        public double Heading { get; set; }
        public double Slope { get; set; } // already exists
    }

    static class Program
    {
        // Mapeamento dos sensores para ArbitrationId e AlgorithmID
        private static readonly Dictionary<string, (int ArbitrationId, string AlgorithmID)> sensorMappings
            = new Dictionary<string, (int, string)>(StringComparer.OrdinalIgnoreCase)
            {
                { "blindspot",        (0x100, "BlindSpotDetection") },
                { "pedestrian",       (0x101, "PedestrianDetection") },
                { "frontalcollision", (0x102, "FrontalCollisionDetection") },
                { "rearcollision",    (0x103, "RearCollisionDetection") },
                // Add other sensors as needed
            };


        // Cancellation token for asynchronous tasks
        private static CancellationTokenSource? _cancellationTokenSource;
        private static int PromptRow;
        private static int DashboardMaxHeight;
        private static int MessageStartRow;
        private static int MaxMessageLines = 5;
        private static int messageLineCounter = 0;

        


        // Object for synchronizing access to the console
        private static readonly object _consoleLock = new object();

        private static int MessageRow = Console.WindowHeight - 6;

        public static void WriteMessage(string msg)
        {
            lock (_consoleLock)
            {
                int row = MessageStartRow + messageLineCounter;

                // Ensure we do not write on the prompt line
                if (row >= PromptRow)
                {
                    messageLineCounter = 0;
                    row = MessageStartRow;
                }

                Console.SetCursorPosition(0, row);
                Console.Write(new string(' ', Console.WindowWidth));
                Console.SetCursorPosition(0, row);
                Console.WriteLine(msg);

                messageLineCounter = (messageLineCounter + 1) % MaxMessageLines;
            }
        }


        static async Task Main(string[] args)
        {
            Env.TraversePath().Load();

            Console.WriteLine("Starting MQTT client in the console...");

            var host = Environment.GetEnvironmentVariable("MQTT_HOST") ?? "localhost";
            var portStr = Environment.GetEnvironmentVariable("MQTT_PORT");
            var port    = int.TryParse(portStr, out var p) ? p : 1884;

            var mqttService = new MqttService(host, port, "Simulator"); // board broker

            // Initialize services
            var canService = new CAN.CanService(mqttService);

            var loggingService = new LoggingService();
            //var mqttService = new MqttService("172.20.0.201", 1884, "Simulator");
            var dashboard = new Dashboard(mqttService, loggingService);
            var auxiliar = new Auxiliares(mqttService, loggingService, dashboard, canService); 
            var sensorService = new SensorService(sensorMappings, mqttService, loggingService, canService);

            
            var startAndUpdate = new StartAndUpdate(mqttService, loggingService, null);
            var chargingMethods = new ChargingMethods(mqttService, loggingService, dashboard);
            var updateMethods = new UpdateMethods(mqttService, loggingService, dashboard, auxiliar, null, canService);
            var routes = new Routes(mqttService, loggingService, sensorService, dashboard, null, null, auxiliar);
            var backgroundUpdated = new BackgroundUpdated(mqttService, loggingService, sensorService, _cancellationTokenSource, startAndUpdate, dashboard, updateMethods, routes);


            startAndUpdate.SetAux(auxiliar);
            updateMethods.SetRoutes(routes);
            routes.SetUpdateMethods(updateMethods);
            routes.SetBackgroundUpdated(backgroundUpdated);
            try
            {
                await mqttService.ConnectAsync();

                // Choose the VIN before starting the simulator
                Console.Clear();

                Console.WriteLine("Choose the VIN of the motorcycle to simulate:");
                Console.WriteLine("1 - AJP1RB5SXLB123456");
                Console.WriteLine("2 - AJP7XZ8MWQ2Y1R3K");

                string? selectedVin = null;
                while (selectedVin == null)
                {
                    Console.Write("Enter 1 or 2: ");
                    var input = Console.ReadLine();
                    switch (input)
                    {
                        case "1":
                            selectedVin = "AJP1RB5SXLB123456";
                            break;
                        case "2":
                            selectedVin = "AJP7XZ8MWQ2Y1R3K";
                            break;
                        default:
                            Console.WriteLine("Invalid option. Try again.");
                            break;
                    }
                }

                // Publish the VIN to the moto/vin topic
                await mqttService.PublishAsync("moto/vin", selectedVin);
                Console.WriteLine($"\nVIN {selectedVin} selected and published.\n");
                Thread.Sleep(1000); // small delay before continuing

            }
            catch
            {
                Console.WriteLine("Failed to connect to the MQTT broker. Closing program.");
                return;
            }

           PromptRow = Console.WindowHeight - 1;
            DashboardMaxHeight = PromptRow - MaxMessageLines - 1; // reserva linhas
            MessageStartRow = PromptRow - MaxMessageLines;



            int windowHeight = Console.WindowHeight;

            // Keep the restrictions fixed on the last 5 lines (4 restriction lines + 1 prompt line)
            PromptRow = Console.WindowHeight - 1; // last line for the prompt

            // Initialize the cancellation token
            _cancellationTokenSource = new CancellationTokenSource();

            // Show the initial commands
            dashboard.DisplayCommands();

            // Start asynchronous tasks to update time, distance, autonomy
            _ = backgroundUpdated.UpdateTimeAsync(_cancellationTokenSource.Token);
            _ = backgroundUpdated.UpdateTotalKilometersAsync(_cancellationTokenSource.Token);
            _ = backgroundUpdated.UpdateAutonomyAsync(_cancellationTokenSource.Token);

            // Update battery if the bike is charging
            _ = chargingMethods.UpdateChargingAsync(_cancellationTokenSource.Token);

            // Battery consumption while riding
            _ = backgroundUpdated.UpdateBatteryConsumptionAsync(_cancellationTokenSource.Token);

            _ = canService.StartAsync(_cancellationTokenSource.Token);


            // >>> New task: SIMULATE ROUTE (more realistic logic)
            _ = routes.UpdateRouteAsync(sensorService);

            // Start the task to refresh the dashboard and menu
            _ = Task.Run(() => updateMethods.UpdateDashboardContinuouslyAsync(_cancellationTokenSource.Token));

            // After starting the other tasks, also run:
            _ = backgroundUpdated.FluctuateSpeedAsync(_cancellationTokenSource.Token);

            bool running = true;
            while (running)
            {
                lock (_consoleLock)
                {
                    Console.SetCursorPosition(0, PromptRow);
                    Console.Write(new string(' ', Console.WindowWidth));
                    Console.SetCursorPosition(0, PromptRow);
                    Console.Write("> ");
                }
                var line = Console.ReadLine();
                if (line == null) { continue; }

                lock (_consoleLock)
                {
                    // Reposition the cursor after user input
                    Console.SetCursorPosition(0, PromptRow);
                    Console.Write(new string(' ', Console.WindowWidth));
                    Console.SetCursorPosition(0, PromptRow);
                    Console.Write("> ");
                }

                var parts = line.Split(' ', StringSplitOptions.RemoveEmptyEntries);
                if (parts.Length == 0) { continue; }

                switch (parts[0].ToLower())
                {
                    case "start":
                        await startAndUpdate.StartMotorcycleAsync();
                        SimulationState.IsMotorcycleOn = true;
                // Refresh available commands
                        dashboard.DisplayDashboard();
                        break;

                    case "stop":
                        await startAndUpdate.StopMotorcycleAsync();
                        SimulationState.IsMotorcycleOn = false;
                        Console.Clear();
                        break;

                    // Control charging
                    case "charge":
                        if (parts.Length == 2)
                        {
                            string chargeAction = parts[1].ToLower();
                            if (chargeAction == "start")
                            {
                                await chargingMethods.StartChargingAsync();
                                dashboard.DisplayDashboard();
                            }
                            else if (chargeAction == "stop")
                            {
                                await chargingMethods.StopChargingAsync();
                                dashboard.DisplayDashboard();
                            }
                            else
                            {
                                Program.WriteMessage("Usage: charge start | charge stop");
                            }
                        }
                        else
                        {
                            Program.WriteMessage("Usage: charge start | charge stop");
                        }
                        break;

                    case "exit":
                        running = false;
                        break;

                    // Route command
                    case "route":
                        if (!SimulationState.IsMotorcycleOn)
                        {
                            Program.WriteMessage("You must turn on the motorcycle before using this command.");
                            break;
                        }
                        if (parts.Length >= 2)
                        {
                            string action = parts[1].ToLower();
                            if (action == "start")
                            {
                                // route start <nome>
                                if (parts.Length == 3)
                                {
                                    string routeName = parts[2].ToLower();
                                    await routes.StartRouteAsync(routeName);
                                }
                                else
                                {
                                    Program.WriteMessage("Usage: route start <route1|route2|route3>");
                                }
                            }
                            else if (action == "stop")
                            {
                                await routes.StopRouteAsync();
                            }
                            else
                            {
                                Program.WriteMessage("Usage: route start <name> | route stop");
                            }
                        }
                        else
                        {
                            Program.WriteMessage("Usage: route start <name> | route stop");
                        }
                        break;

                    case "auto":
                        if (parts.Length == 2)
                        {
                            string routeName = parts[1].ToLower();
                            // Launch the automatic scenario
                            _ = Task.Run(() => backgroundUpdated.AutoSimulateAsync(routeName));

                        }
                        else
                        {
                            Program.WriteMessage("Usage: auto <route1|route2|route3>");
                        }
                        break;


                    // Check if the motorcycle is on before processing other commands
                    case "battery":
                    case "speed":
                    case "temp":
                    case "gear":
                    case "indicator":
                    case "lights":
                    case "abs":
                    case "mode":
                    case "maxlights":
                    case "danger":
                    case "send":
                    case "sensors":
                    case "brake":
                        if (!SimulationState.IsMotorcycleOn)
                        {
                            Program.WriteMessage("You must turn on the motorcycle before using this command. Use 'start' to turn it on.");
                            break;
                        }

                        // Block speed/gear changes while charging
                        if (SimulationState.IsCharging && (parts[0].ToLower() == "speed" || parts[0].ToLower() == "gear"))
                        {
                            Program.WriteMessage("The motorcycle is charging. Stop charging before changing speed or gears.");
                            break;
                        }

                        // Processar outros comandos
                        switch (parts[0].ToLower())
                        {
                            case "battery":
                                if (parts.Length == 2)
                                {
                                    if (CommandParser.TryParseCommand(parts[1], out int delta))
                                    {
                                        // Do not allow positive values
                                        if (delta > 0)
                                        {
                                            Program.WriteMessage("You cannot manually increase the battery. Use 'charge start' to charge.");
                                        }
                                        else
                                        {
                                            // Se for negativo, podemos chamar o UpdateBatteryAsync normalmente
                                             Program.WriteMessage(DateTime.UtcNow.ToString("o")); // formato ISO 8601
                                            await updateMethods.UpdateBatteryAsync(parts[1]);
                                           
                                        }
                                    }
                                    else
                                    {
                                        Program.WriteMessage("Usage: battery -<value>");
                                    }
                                }
                                else
                                {
                                    Program.WriteMessage("Usage: battery -<value>");
                                }
                                break;

                            case "speed":
                                if (parts.Length == 2)
                                {
                                    Program.WriteMessage(DateTime.UtcNow.ToString("o")); // ISO 8601 format
                                    await updateMethods.UpdateSpeedAsync(parts[1]);
                                    
                                }
                                else
                                {
                                    Program.WriteMessage("Usage: speed +<value> or speed -<value>");
                                }
                                break;

                            case "brake":
                                if (!SimulationState.IsMotorcycleOn)
                                {
                                    Program.WriteMessage("You must turn on the motorcycle before using this command.");
                                    break;
                                }
                                if (parts.Length == 2)
                                {
                                    await updateMethods.ApplyBrakeCommandAsync(parts[1]);
                                }
                                else
                                {
                                    Program.WriteMessage("Usage: brake <value>");
                                }
                                break;
                            case "gps":
                                Program.WriteMessage($"Current location: {SimulationState.Latitude:F6}, {SimulationState.Longitude:F6}");
                                break;


                            case "temp":
                                if (parts.Length == 2)
                                {
                                    await updateMethods.UpdateTemperatureAsync(parts[1]);
                                }
                                else
                                {
                                    Program.WriteMessage("Usage: temp +<value> or temp -<value>");
                                }
                                break;

                            case "gear":
                                if (parts.Length == 2)
                                {
                                    Program.WriteMessage(DateTime.UtcNow.ToString("o")); // ISO 8601 format
                                    await updateMethods.UpdateGearAsync(parts[1]);
                                }
                                else
                                {
                                    Program.WriteMessage("Usage: gear front | gear back | gear neutral | gear park");
                                }
                                break;

                            case "indicator":
                                if (parts.Length == 2)
                                {
                                    await updateMethods.UpdateIndicatorAsync(parts[1]);
                                }
                                else
                                {
                                    Program.WriteMessage("Usage: indicator left | indicator right | indicator none");
                                }
                                break;

                            case "lights":
                                if (parts.Length == 2)
                                {
                                    await updateMethods.UpdateLightsAsync(parts[1]);
                                }
                                else
                                {
                                    Program.WriteMessage("Usage: lights on | lights off");
                                }
                                break;

                            case "abs":
                                if (parts.Length == 2)
                                {
                                    await updateMethods.UpdateAbsAsync(parts[1]);
                                    Program.WriteMessage(DateTime.UtcNow.ToString("o")); // formato ISO 8601
                                }
                                else
                                {
                                    Program.WriteMessage("Usage: abs on | abs off");
                                }
                                break;

                            case "mode":
                                if (parts.Length == 2)
                                {
                                    await updateMethods.UpdateModeAsync(parts[1]);
                                    Program.WriteMessage(DateTime.UtcNow.ToString("o")); // formato ISO 8601
                                }
                                else
                                {
                                    Program.WriteMessage("Usage: mode standard | mode eco | mode sport");
                                }
                                break;

                            case "maxlights":
                                if (parts.Length == 2)
                                {
                                    await updateMethods.UpdateMaxLightsAsync(parts[1]);
                                    Program.WriteMessage(DateTime.UtcNow.ToString("o")); // formato ISO 8601
                                }
                                else
                                {
                                    Program.WriteMessage("Usage: maxlights on | maxlights off");
                                }
                                break;

                            case "danger":
                                if (parts.Length == 2)
                                {
                                    await updateMethods.UpdateDangerAsync(parts[1]);
                                }
                                else
                                {
                                    Program.WriteMessage("Usage: danger on | danger off");
                                }
                                break;

                            case "send":
                                if (parts.Length == 2)
                                {
                                    await sensorService.SendSensorMessagesAsync(parts[1]);
                                }
                                else
                                {
                                    Program.WriteMessage("Usage: send <sensor>");
                                }
                                break;

                            case "sensors":
                                await sensorService.StartManualInputAsync();
                                break;

                            default:
                                Program.WriteMessage("Command not recognized.");
                                break;
                        }
                        break;

                    default:
                        Program.WriteMessage("Command not recognized.");
                        break;
                }
            }

            // Cancel all asynchronous tasks
            _cancellationTokenSource?.Cancel();

            // Salvar os registros antes de desconectar
            //string logFileName = $"MotorcyclePerformanceLog_{DateTime.Now:yyyyMMdd_HHmmss}.json";
            //loggingService.SaveLogsToFile(logFileName);

            await mqttService.DisconnectAsync();
            Console.WriteLine("Desconectado do broker. Encerrando programa.");
        }

    }
}
