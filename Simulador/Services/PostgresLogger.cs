using Npgsql;
using NpgsqlTypes;
using Simulador.Models;

namespace Simulador.Services
{
    public class PostgresLogger
    {
        private readonly string _connectionString;

        public PostgresLogger(string host, int port, string database, string user, string password)
        {
            _connectionString = $"Host={host};Port={port};Database={database};Username={user};Password={password}";
        }

        public void InsertLog(LogEntry entry, string routeName)
        {
            using var conn = new NpgsqlConnection(_connectionString);
            conn.Open();

            using var transaction = conn.BeginTransaction();

            try
            {
                using var cmd = new NpgsqlCommand(@"
                    INSERT INTO logsrotas (
                        timestamp, route_name, message_type, battery, speed, temperature,
                        gear, lights, abs, indicator_left, indicator_right, drive_mode,
                        max_lights, danger_lights, total_kilometers, autonomy
                    ) VALUES (
                        @timestamp, @route_name, @message_type, @battery, @speed, @temperature,
                        @gear, @lights, @abs, @indicator_left, @indicator_right, @drive_mode,
                        @max_lights, @danger_lights, @total_kilometers, @autonomy
                    );", conn, transaction);

                cmd.Parameters.Add("timestamp", NpgsqlDbType.Timestamp).Value = entry.Timestamp;
                cmd.Parameters.AddWithValue("route_name", routeName);
                cmd.Parameters.AddWithValue("message_type", entry.MessageType ?? (object)DBNull.Value);
                cmd.Parameters.AddWithValue("battery", entry.Battery);
                cmd.Parameters.AddWithValue("speed", entry.Speed);
                cmd.Parameters.AddWithValue("temperature", entry.Temperature);
                cmd.Parameters.AddWithValue("gear", entry.Gear ?? (object)DBNull.Value);
                cmd.Parameters.AddWithValue("lights", entry.Lights);
                cmd.Parameters.AddWithValue("abs", entry.Abs);
                cmd.Parameters.AddWithValue("indicator_left", entry.IndicatorLeft);
                cmd.Parameters.AddWithValue("indicator_right", entry.IndicatorRight);
                cmd.Parameters.AddWithValue("drive_mode", entry.DriveMode ?? (object)DBNull.Value);
                cmd.Parameters.AddWithValue("max_lights", entry.MaxLights);
                cmd.Parameters.AddWithValue("danger_lights", entry.DangerLights);
                cmd.Parameters.AddWithValue("total_kilometers", entry.TotalKilometers);
                cmd.Parameters.AddWithValue("autonomy", entry.Autonomy);

                Console.WriteLine($"[DB] Inserting log for route '{routeName}' at {entry.Timestamp}...");
                cmd.ExecuteNonQuery();
                transaction.Commit();
                Console.WriteLine($"[BD] ✅ Log inserido com sucesso.");
            }
            catch (Exception ex)
            {
                transaction.Rollback();
                Console.WriteLine($"[DB ERROR] Failed to insert log: {ex.Message}");
            }
        }
    }
}
