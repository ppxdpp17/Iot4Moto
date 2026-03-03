using System;
using System.Collections.Generic;
using System.IO;
using System.Text.Json;
using Simulador.Models;

namespace Simulador.Services
{
    public class LoggingService
    {
        private readonly List<LogEntry> _logEntries = new List<LogEntry>();
        private readonly object _logLock = new object();

        private LogEntry? _lastLogEntry = null;

        public void AddPerformanceLog(LogEntry entry)
        {
            entry.MessageType = "Performance";
            AddLog(entry);
        }

        public void AddSensorLog(LogEntry entry)
        {
            entry.MessageType = "Sensor";
            AddLog(entry);
        }

        private void AddLog(LogEntry entry)
        {
            lock (_logLock)
            {
                if (_lastLogEntry != null)
                {
                    if (entry.MessageType == "Performance" && _lastLogEntry.MessageType == "Performance")
                    {
                        if (
                            entry.Battery == _lastLogEntry.Battery &&
                            entry.Speed == _lastLogEntry.Speed &&
                            entry.Temperature == _lastLogEntry.Temperature &&
                            entry.Gear == _lastLogEntry.Gear &&
                            entry.Lights == _lastLogEntry.Lights &&
                            entry.Abs == _lastLogEntry.Abs &&
                            entry.IndicatorLeft == _lastLogEntry.IndicatorLeft &&
                            entry.IndicatorRight == _lastLogEntry.IndicatorRight &&
                            entry.DriveMode == _lastLogEntry.DriveMode &&
                            entry.MaxLights == _lastLogEntry.MaxLights &&
                            entry.DangerLights == _lastLogEntry.DangerLights &&
                            Math.Abs(entry.TotalKilometers - _lastLogEntry.TotalKilometers) < 0.0001 &&
                            Math.Abs(entry.Autonomy - _lastLogEntry.Autonomy) < 0.0001
                        )
                        {
                            // No changes detected, skip adding the log
                            return;
                        }
                    }
                }

                _logEntries.Add(entry);
                _lastLogEntry = entry;
            }
        }

        public void SaveLogsToFile(string fileName)
        {
            lock (_logLock)
            {
                if (_logEntries.Count == 0)
                {
                    Console.WriteLine("Nenhum registro para salvar.");
                    return;
                }

                try
                {
                    var options = new JsonSerializerOptions { WriteIndented = true };
                    string jsonString = JsonSerializer.Serialize(_logEntries, options);
                    File.WriteAllText(fileName, jsonString);
                    Console.WriteLine($"Registros salvos em {fileName}");
                }
                catch (Exception ex)
                {
                    Console.WriteLine($"Erro ao salvar os registros: {ex.Message}");
                }
            }
        }

        public void Clear()
        {
            lock (_logLock)
            {
                _logEntries.Clear();
                _lastLogEntry = null;
            }
        }

        public List<LogEntry> GetAllLogs()
        {
            lock (_logLock)
            {
                return new List<LogEntry>(_logEntries);
            }
        }
    }
}
