using System;

namespace Simulador.Models
{
    public class LogEntry
    {
        public DateTime Timestamp { get; set; }
        public string Topic { get; set; } = string.Empty;
        public string Payload { get; set; } = string.Empty;
        public string MessageType { get; set; } = "Performance"; // "Performance" ou "Sensor"

        // Campos para atividades de performance
        public int Battery { get; set; }
        public int Speed { get; set; }
        public int Temperature { get; set; }
        public string Gear { get; set; } = "neutral"; // Default initialization
        public bool Lights { get; set; }
        public bool Abs { get; set; }
        public bool IndicatorLeft { get; set; }
        public bool IndicatorRight { get; set; }
        public string DriveMode { get; set; } = "standard"; // Default initialization
        public bool MaxLights { get; set; }
        public bool DangerLights { get; set; }
        public double TotalKilometers { get; set; }
        public double Autonomy { get; set; }

        // Campos adicionais para mensagens de sensor
        public string? SensorName { get; set; }
        public int? ArbitrationId { get; set; }
        public int[]? Data { get; set; }
    }
}
