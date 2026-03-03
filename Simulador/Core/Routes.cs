using System;
using System.Threading.Tasks;
using Simulador.Services;
using Simulador.Models;
using Simulador.Helpers;

namespace Simulador.Core
{
    public class Routes
    {
        private readonly MqttService _mqttService;
        private readonly LoggingService _loggingService;

        private readonly SensorService _sensorService;

        private readonly Dashboard _dashboard;

        private UpdateMethods _updateMethods;

        private BackgroundUpdated _backgroundUpdated;

        private readonly Auxiliares _auxiliares;

        private CancellationTokenSource? _cts;

        public bool _isRouteActive = false;

        public string _activeRouteName = string.Empty;

        private List<(double Lat, double Lng)> _currentRoute = new List<(double, double)>();


        public List<RouteSegment> _currentRouteSegments = new List<RouteSegment>();

        public int _segmentIndex = 0;
        private double _distanceInSegment = 0.0; // em km

        private bool _accidentTriggered = false;
        private static Random _rand = new Random();

        private const int MAX_SPEED = 200;

        public double ToRadians(double angle)
        {
            return Math.PI * angle / 180.0;
        }


        public readonly Dictionary<string, (int maxSpeed, double accelerationRate, double extraConsumption)> _routeConfigs
            = new Dictionary<string, (int, double, double)>(StringComparer.OrdinalIgnoreCase)
        {
            { "rota1", (50, 3.0, 1.0) },

            { "rota2", (120, 8.0, 1.5) },

            { "rota3", (70, 5.0, 1.3) }
        };

        private readonly Dictionary<string, List<(double, double)>> _routes
            = new Dictionary<string, List<(double, double)>>(StringComparer.OrdinalIgnoreCase)
        {
            {
                "rota1",
                new List<(double, double)>
                {
                    (38.736946, -9.142685),
                    (38.737500, -9.140000),
                    (38.738000, -9.137000),
                    (38.738500, -9.135000),
                }
            },
            {
                "rota2",
                new List<(double, double)>
                {
                    (41.3000, -7.7400), // Ponto inicial: Vila Real
                    (41.2950, -7.7700),
                    (41.2900, -7.8000),
                    (41.2850, -7.8300)
                }
            },
            {
                "rota3",
                new List<(double, double)>
                {
                    (37.017953, -7.930834),
                    (37.018500, -7.929500),
                    (37.019000, -7.928000),
                    (37.019500, -7.927000),
                }
            }
        };

        public double CalculateBearing(double lat1, double lon1, double lat2, double lon2)
        {
            double rlat1 = ToRadians(lat1);
            double rlat2 = ToRadians(lat2);
            double dLon = ToRadians(lon2 - lon1);

            double y = Math.Sin(dLon) * Math.Cos(rlat2);
            double x = Math.Cos(rlat1) * Math.Sin(rlat2) - Math.Sin(rlat1) * Math.Cos(rlat2) * Math.Cos(dLon);
            double bearingRad = Math.Atan2(y, x);
            double bearingDeg = (bearingRad * (180.0 / Math.PI) + 360) % 360;
            return bearingDeg;
        }

        public Routes(MqttService mqttService, LoggingService loggingService, SensorService sensorService, Dashboard dashboard, UpdateMethods updateMethods, BackgroundUpdated backgroundUpdated, Auxiliares auxiliares)
        {
            _mqttService = mqttService;
            _loggingService = loggingService;
            _sensorService = sensorService;
            _dashboard = dashboard;
            _updateMethods = updateMethods;
            _backgroundUpdated = backgroundUpdated;
            _auxiliares = auxiliares;
            _cts = new CancellationTokenSource();
        }

        public void SetUpdateMethods(UpdateMethods updateMethods)
        {
            _updateMethods = updateMethods;
        }

        public void SetBackgroundUpdated(BackgroundUpdated backgroundUpdated)
        {
            _backgroundUpdated = backgroundUpdated;
        }

        /// <summary>
        /// Starts a route, converts the point list into a segment list, and resets control state.
        /// </summary>
        public async Task StartRouteAsync(string routeName)
        {
            if (!_routeConfigs.ContainsKey(routeName))
            {
                Console.WriteLine($"Route '{routeName}' does not exist. Available routes: rota1, rota2, rota3.");
                return;
            }

            if (_isRouteActive)
            {
                Console.WriteLine("A route is already running. Stop the current route first (route stop).");
                return;
            }

            if (!_routes.ContainsKey(routeName))
            {
                Console.WriteLine($"Route '{routeName}' does not exist. Available routes: rota1, rota2, rota3.");
                return;
            }

            // Store the current name
            _activeRouteName = routeName.ToLower();
            // Load the route (list of points) and convert to segments
            _currentRoute = _routes[routeName];
            _currentRouteSegments = BuildRouteSegments(_currentRoute, routeName);
            _isRouteActive = true;
            // Reset the segmentation controls
            _segmentIndex = 0;
            _distanceInSegment = 0.0;

            _isRouteActive = true;

            _accidentTriggered = false;


            Console.WriteLine($"Route '{routeName}' started with {_currentRoute.Count} points.");
            _ = Task.Run(() => UpdateRouteAsync(_sensorService)); // Ensure it runs in the background
            await _mqttService.PublishAsync("sim/route/status", $"started:{routeName}");

            
            
            _dashboard.DisplayDashboard();
        }

        public async Task StopRouteAsync()
        {
            if (!_isRouteActive)
            {
                Console.WriteLine("There is no active route.");
                return;
            }

            _isRouteActive = false;
            Console.WriteLine("Route stopped.");
            await _mqttService.PublishAsync("sim/route/status", "stopped");

            
        }

        /// <summary>
        /// Converts a (Lat, Lng) list into segment list with start/end and distance.
        /// </summary>
        public List<RouteSegment> BuildRouteSegments(List<(double Lat, double Lng)> routePoints, string routeName)
        {
            var segments = new List<RouteSegment>();

            for (int i = 0; i < routePoints.Count - 1; i++)
            {
                var (lat1, lng1) = routePoints[i];
                var (lat2, lng2) = routePoints[i + 1];

                double dist = CalculateDistance(lat1, lng1, lat2, lng2);
                double heading = CalculateBearing(lat1, lng1, lat2, lng2);

                // Definir slope: somente para rota3 (montanhosa)
                double slopeValue = 0.0;
                if (routeName.Equals("rota3", StringComparison.OrdinalIgnoreCase))
                {
                    slopeValue = GenerateRandomSlope(-0.05, 0.10); // entre -5% e +10%
                }
                else
                {
                    slopeValue = 0.0;
                }

                segments.Add(new RouteSegment
                {
                    LatStart = lat1,
                    LngStart = lng1,
                    LatEnd = lat2,
                    LngEnd = lng2,
                    Distance = dist,
                    Heading = heading,
                    Slope = slopeValue
                });
            }
            return segments;
        }


        private double GenerateRandomSlope(double min, double max)
        {
            var rand = new Random();
            return rand.NextDouble() * (max - min) + min;
        }


        /// <summary>
        /// Calculates approximate distance (km) between two coordinates using the Haversine formula.
        /// </summary>
        public double CalculateDistance(double lat1, double lon1, double lat2, double lon2)
        {
            const double R = 6371.0; // Raio da Terra em km
            double dLat = ToRadians(lat2 - lat1);
            double dLon = ToRadians(lon2 - lon1);

            double a = Math.Sin(dLat / 2) * Math.Sin(dLat / 2) +
                       Math.Cos(ToRadians(lat1)) * Math.Cos(ToRadians(lat2)) *
                       Math.Sin(dLon / 2) * Math.Sin(dLon / 2);
            double c = 2 * Math.Atan2(Math.Sqrt(a), Math.Sqrt(1 - a));

            return R * c;
        }


        /// <summary>
        /// Updates the position along the route on each cycle based on speed and segment distance.
        /// </summary>
        public async Task UpdateRouteAsync(SensorService sensorService)
        {


            while (!_cts.IsCancellationRequested)
            {
                try
                {
                    if (SimulationState.IsMotorcycleOn && _isRouteActive &&
                        _currentRouteSegments.Count > 0 &&
                        _segmentIndex < _currentRouteSegments.Count)
                    {

                        if (SimulationState.Battery <= 0)
                        {
                            Console.WriteLine("[WARN] Battery depleted. The route will end.");
                            _isRouteActive = false;
                            await _mqttService.PublishAsync("sim/route/status", "battery_exhausted");
                            await _mqttService.PublishAsync("sim/speed", "0");
                            return;
                        }

                        // Get the current segment
                        var segment = _currentRouteSegments[_segmentIndex];

                        // Calculate the distance traveled in this cycle
                        double speedKmS = Math.Max(SimulationState.Speed, 5) / 3600.0;
                        double distanceThisLoop = speedKmS * 1.0; // 1 segundo por loop
                        _distanceInSegment += distanceThisLoop;

                        // If the traveled distance exceeds the segment length, move to the next one
                        if (_distanceInSegment >= segment.Distance)
                        {
                            if (SimulationState.Speed > 0)
                            {
                                _distanceInSegment -= segment.Distance;
                                _segmentIndex++;
                                if (_segmentIndex >= _currentRouteSegments.Count && _isRouteActive)
                                {
                                    Console.WriteLine("[DEBUG] _isRouteActive definido como false. Confirma se a rota terminou realmente.");
                                    _isRouteActive = false;
                                    Console.WriteLine("Route completed (you reached the final point).");
                                    await _mqttService.PublishAsync("sim/route/status", "completed");
                                }
                            }
                            else
                            {
                                // If stopped (e.g., at a traffic light) do not advance on the route
                                Console.WriteLine("[INFO] Motorcycle stopped. Segment will not progress until movement resumes.");
                            }
                        }


                        if (_isRouteActive && _segmentIndex < _currentRouteSegments.Count)
                        {
                            // Re-obtem o segmento atual (caso tenha mudado)
                            segment = _currentRouteSegments[_segmentIndex];

                            // Calcula o progresso (fraction) do segmento atual
                            double fraction = _distanceInSegment / segment.Distance;
                            fraction = Math.Clamp(fraction, 0.0, 1.0);

                            double lat = segment.LatStart + (segment.LatEnd - segment.LatStart) * fraction;
                            double lng = segment.LngStart + (segment.LngEnd - segment.LngStart) * fraction;

                            // Publish the position
                            await _mqttService.PublishAsync("sim/gps/latitude", lat.ToString("F6"));
                            await _mqttService.PublishAsync("sim/gps/longitude", lng.ToString("F6"));

                            // -- Traffic light block for rota1 --
                            // Block to simulate stops on rota1 (urban)
                            if (_activeRouteName == "rota1")
                            {
                                // If segment progress is near the end (>= 80%) and speed > 0
                                if (fraction >= 0.8 && SimulationState.Speed > 0)
                                {
                                    double randomVal = _rand.NextDouble();
                                    // 20% chance to trigger the red light event
                                    if (randomVal < 0.2)
                                    {
                                        Console.WriteLine("[DEBUG] Red light event on rota1.");
                                        Console.WriteLine("[URBAN] Red light detected! Slowing down to stop...");
                                        // Decelerate smoothly to 0 km/h
                                        await AdjustSpeedAsync(0);

                                        // Choose a random stop duration between 3000 and 7000 ms
                                        int stopDuration = _rand.Next(3000, 7000);
                                        Console.WriteLine($"[URBAN] Stopped for {stopDuration} ms at the red light.");
                                        await Task.Delay(stopDuration);

                                        Console.WriteLine("[URBAN] Green light! Accelerating...");
                                        // Accelerate smoothly to 20 km/h (minimum speed to resume the route)
                                        await _auxiliares.ApplyAccelerationAsync(20);
                                    }
                                }
                            }


                            // -- Automatic indicator block (curves and lane changes) --
                            if (_activeRouteName != "rota2" && _segmentIndex < _currentRouteSegments.Count - 1)
                            {
                                double currentHeading = segment.Heading;
                                double nextHeading = _currentRouteSegments[_segmentIndex + 1].Heading;
                                double angleDiff = nextHeading - currentHeading;
                                if (angleDiff > 180) { angleDiff -= 360; }
                                if (angleDiff < -180) { angleDiff += 360; }

                                if (Math.Abs(angleDiff) >= 20 && fraction >= 0.8)
                                {
                                    string indicatorDirection = angleDiff > 0 ? "left" : "right";
                                    Console.WriteLine($"[AUTO-INDICATOR] Curve detected ({indicatorDirection}, diff={angleDiff:0.0} deg). Activating indicator for 3000 ms...");
                                    // Trigger the indicator automatically for a short window
                                    _ = _updateMethods.AutoActivateIndicatorAsync(indicatorDirection, 6000);
                                }
                            }

                            // Only add automatic overtaking logic for rota2
                            if (_activeRouteName == "rota2" && SimulationState.Speed >= 80)
                            {
                                double chance = _rand.NextDouble();
                                if (chance < 0.05)
                                {
                                    Console.WriteLine("[AUTO] Left-side overtake started on rota2!");
                                    await _updateMethods.UpdateIndicatorAsync("left");

                                    int originalSpeed = SimulationState.Speed;
                                    int overtakeSpeed = Math.Min(SimulationState.Speed + 20, MAX_SPEED);

                                    if (_routeConfigs.TryGetValue(_activeRouteName, out var config))
                                    {
                                        int maxAllowed = config.maxSpeed;
                                        if (overtakeSpeed > maxAllowed)
                                        {
                                            Console.WriteLine($"[OVERTAKE] Requested speed ({overtakeSpeed}) exceeds the route limit ({maxAllowed}). Clamping to {maxAllowed} km/h.");
                                            overtakeSpeed = maxAllowed;
                                        }
                                    }

                                    await _auxiliares.ApplyAccelerationAsync(overtakeSpeed);
                                    await Task.Delay(3000);

                                    await _updateMethods.UpdateIndicatorAsync("right");
                                    await Task.Delay(2000);

                                    await _updateMethods.UpdateIndicatorAsync("none");
                                    await _auxiliares.ApplyAccelerationAsync(originalSpeed);
                                }
                            }

                            // -- Accident logic block for rota2 (existing) --
                            if (_activeRouteName == "rota2" && SimulationState.Speed > 0)
                            {
                                double dangerChance = _rand.NextDouble();
                                if (dangerChance < 0.05)
                                {
                                    string[] sensors = { "blindspot", "pedestrian", "frontalcollision", "rearcollision" };
                                    string sensorToActivate = sensors[_rand.Next(sensors.Length)];
                                    Console.WriteLine($"Triggering sensor {sensorToActivate}...");
                                    await sensorService.SendSensorMessagesAsync(sensorToActivate);
                                    await Task.Delay(5000);
                                }
                            }


                            // Specific block for rota3 (rural or mountainous road)
                            if (_activeRouteName == "rota3")
                            {
                                if (segment.Slope > 0.08 && SimulationState.Speed > 30)
                                {
                                    Console.WriteLine("[ROUTE3] Steep climb detected. Reducing speed for safety.");
                                    await AdjustSpeedAsync(SimulationState.Speed - 10);

                                }
                                else if (segment.Slope < -0.02 && SimulationState.Speed > 70)
                                {
                                    Console.WriteLine("[ROUTE3] Steep descent detected. Adjusting speed for safety.");
                                    await _auxiliares.ApplyAccelerationAsync(70);
                                }
                                if (_rand.NextDouble() < 0.1 && SimulationState.Speed > 20)
                                {
                                    Console.WriteLine("[ROUTE3] Rough or narrow section detected. Temporarily reducing speed.");
                                    await AdjustSpeedAsync(SimulationState.Speed - 10);

                                    await Task.Delay(2000);
                                    Console.WriteLine("[ROUTE3] Normal conditions restored. Accelerating again.");
                                    await _auxiliares.ApplyAccelerationAsync(30);
                                }
                            }

                        }

                    }
                    // Wait 1 second before repeating the cycle
                    await Task.Delay(1000, _cts.Token);

                }
                catch (TaskCanceledException)
                {
                    break;
                }
            }
        }

        private async Task AdjustSpeedAsync(int targetSpeed)
        {
            int currentSpeed = SimulationState.Speed;

            if (targetSpeed < currentSpeed)
            {
                Console.WriteLine($"[TRAVAGEM] Reduzindo de {currentSpeed} km/h para {targetSpeed} km/h...");
                await _auxiliares.ApplyBrakingAsync(targetSpeed);
                Console.WriteLine($"[BRAKE] Speed stabilized at {SimulationState.Speed} km/h.");
            }
            else if (targetSpeed > currentSpeed)
            {
                Console.WriteLine($"[ACCELERATION] Increasing from {currentSpeed} km/h to {targetSpeed} km/h...");
                await _auxiliares.ApplyAccelerationAsync(targetSpeed);
                Console.WriteLine($"[ACCELERATION] Stabilized at {SimulationState.Speed} km/h.");
            }
            else
            {
                Console.WriteLine($"[SPEED] Already at {currentSpeed} km/h. No change necessary.");
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









