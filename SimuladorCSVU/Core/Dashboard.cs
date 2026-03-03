using System;
using Simulador.Services;
using Simulador.Models;

namespace Simulador.Core
{
    public class Dashboard
    {
        private readonly MqttService _mqttService;
        private readonly LoggingService _loggingService;

        private static int DashboardStartRow = 0;
        private static int DashboardHeight = Console.WindowHeight - 7;

        private static int RestrictionsStartRow = 0;
        private static int RestrictionsHeight = 4;

        private const int MAX_BATTERY = 100;
        private const int MAX_SPEED = 200;

        private const int CommandColumnWidth = 42;
        private const int LeftColumnWidth = 60;
        private const string CommandPrefix = "- ";

        public Dashboard(MqttService mqttService, LoggingService loggingService)
        {
            _mqttService = mqttService;
            _loggingService = loggingService;
        }

        public void DisplayDashboard()
        {
            int startRow = 0;
            int maxRows = Console.WindowHeight - 7;

            for (int i = startRow; i < maxRows; i++)
            {
                Console.SetCursorPosition(0, i);
                Console.Write(new string(' ', Console.WindowWidth));
            }

            int currentRow = 0;

            void WriteLine(string text)
            {
                if (currentRow >= maxRows - 1)
                {
                    return;
                }

                Console.SetCursorPosition(0, currentRow++);
                Console.WriteLine(text);
            }

            string horizontalBorder = BuildHorizontalBorder();
            WriteLine(horizontalBorder);
            WriteLine(BuildHeaderRow("Motorcycle Simulator", "COMANDS"));
            WriteLine(horizontalBorder);

            var dashboardRows = new (string Left, string Command)[]
            {
                ($"Battery: [{GenerateBar(SimulationState.Battery, MAX_BATTERY)}] {FormatValue(SimulationState.Battery, 3)}%", "battery -<value>"),
                ($"Speed:   [{GenerateBar(SimulationState.Speed, MAX_SPEED)}] {FormatValue(SimulationState.Speed, 3)} km/h", "speed -/+ <value>"),
                ($"Gear: {FormatText(SimulationState.Gear, 9)}", "gear front / gear back / gear neutral / gear park"),
                ($"Mode: {FormatText(SimulationState.DriveMode, 9)}", "mode standard / mode eco / mode sport"),
                ($"Lights: {FormatText(SimulationState.Lights ? "On" : "Off", 10)}", "lights on / lights off"),
                ($"High Beam Light: {FormatText(SimulationState.MaxLights ? "On" : "Off", 10)}", "maxlights on / maxlights off"),
                ($"Right indicator: {FormatText(SimulationState.IndicatorRight ? "On" : "Off", 3)}", "indicator right / indicator none"),
                ($"Left indicator:  {FormatText(SimulationState.IndicatorLeft ? "On" : "Off", 3)}", "indicator left / indicator none"),
                ($"ABS: {FormatText(SimulationState.Abs ? "On" : "Off", 10)}", "abs on / abs off"),
                ($"Autonomy: {FormatDecimal(SimulationState.Autonomy, 5)} km", "start / stop"),
                ($"Total KM: {FormatDecimal(SimulationState.TotalKilometers, 5)} km", "charge start / charge stop"),
                ($"GPS: Lat {SimulationState.Latitude:F6} / Lon {SimulationState.Longitude:F6}", "route start <route> / route stop"),
                (string.Empty, "auto <route>"),
                (string.Empty, "temp -/+ <value>"),
                (string.Empty, "danger on / danger off"),
                (string.Empty, "send <sensor>"),
                (string.Empty, "sensors (manual mode)"),
                (string.Empty, "brake <value>"),
                (string.Empty, "exit"),
            };

            foreach (var row in dashboardRows)
            {
                WriteLine(BuildDashboardRow(row.Left, row.Command));
            }

            WriteLine(horizontalBorder);
            WriteLine(string.Empty);

            WriteLine("Restrictions:");
            WriteLine("- Gear 'park' can only be selected when speed is 0 km/h.");
            WriteLine("- When gear is 'front', speed can only increase.");
            WriteLine("- When gear is 'back', speed can only decrease.");
            WriteLine("- If charging (charge start), you cannot change speed or gear.");
        }

        static string FormatValue(double value, int length)
        {
            return value.ToString().PadLeft(length);
        }

        static string FormatText(string text, int length)
        {
            return text.PadRight(length);
        }

        static string FormatDecimal(double value, int length)
        {
            return value.ToString("F2").PadLeft(length);
        }

        static string GenerateBar(int value, int max)
        {
            int barLength = 20;
            int filled = (int)((value / (double)max) * barLength);
            return new string('#', filled).PadRight(barLength, '-');
        }

        private static string BuildHorizontalBorder()
        {
            return $"+-{new string('-', LeftColumnWidth)}-+-{new string('-', CommandColumnWidth)}-+";
        }

        private static string BuildHeaderRow(string leftTitle, string rightTitle)
        {
            string left = PadCenter(leftTitle, LeftColumnWidth);
            string right = PadCenter(rightTitle, CommandColumnWidth);
            return $"| {left} | {right} |";
        }

        private static string BuildDashboardRow(string leftContent, string commandText)
        {
            string leftCell = PadOrTrim(leftContent ?? string.Empty, LeftColumnWidth);
            string commandValue = string.IsNullOrWhiteSpace(commandText)
                ? string.Empty
                : $"{CommandPrefix}{commandText}";
            string commandCell = PadOrTrim(commandValue, CommandColumnWidth);
            return $"| {leftCell} | {commandCell} |";
        }

        private static string PadOrTrim(string value, int width)
        {
            if (value.Length > width)
            {
                return value.Substring(0, width);
            }

            return value.PadRight(width);
        }

        private static string PadCenter(string text, int width)
        {
            if (string.IsNullOrEmpty(text))
            {
                return new string(' ', width);
            }

            if (text.Length >= width)
            {
                return text.Substring(0, width);
            }

            int padding = (width - text.Length) / 2;
            string leftPadding = new string(' ', padding);
            string rightPadding = new string(' ', width - text.Length - padding);
            return $"{leftPadding}{text}{rightPadding}";
        }

        public void DisplayCommands()
        {
            for (int i = RestrictionsStartRow; i < RestrictionsStartRow + RestrictionsHeight; i++)
            {
                Console.SetCursorPosition(0, i);
                Console.Write(new string(' ', Console.WindowWidth));
            }

            Console.SetCursorPosition(0, RestrictionsStartRow);

            Console.WriteLine("Start motorcycle:        start");
            Console.WriteLine("Charge battery:          charge start (use when the bike is off)");
            Console.WriteLine("Exit simulator:          exit");
            Console.WriteLine();
        }
    }
}
