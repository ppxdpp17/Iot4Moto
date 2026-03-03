using System;

namespace Simulador.Helpers
{
    public static class CommandParser
    {
        public static bool TryParseCommand(string command, out int delta)
        {
            delta = 0;
            if (string.IsNullOrEmpty(command))
            {
                return false;
            }

            if (command.StartsWith("+") || command.StartsWith("-"))
            {
                if (int.TryParse(command, out delta))
                {
                    return true;
                }
            }

            return false;
        }
    }
}
