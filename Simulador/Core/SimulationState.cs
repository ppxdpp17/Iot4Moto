// SimulationState.cs
namespace Simulador.Core
{
    public static class SimulationState
    {
        public static int Battery { get; set; } = 100;
        public static int Speed { get; set; } = 0;
        public static int Temperature { get; set; } = 25;
        public static string Gear { get; set; } = "neutral";
        public static bool Lights { get; set; } = false;
        public static bool Abs { get; set; } = false;
        public static bool IndicatorLeft { get; set; } = false;
        public static bool IndicatorRight { get; set; } = false;
        public static string DriveMode { get; set; } = "standard";
        public static bool MaxLights { get; set; } = false;
        public static bool DangerLights { get; set; } = false;
        public static double TotalKilometers { get; set; } = 0.0;
        public static double Autonomy { get; set; } = 100.0;
        public static bool IsMotorcycleOn { get; set; } = false;
        public static bool IsCharging { get; set; } = false;
        public static double Latitude { get; set; } = 41.287556;
        public static double Longitude { get; set; } = -7.739556;
        public static bool Brake { get; set; } = false;

    }
}