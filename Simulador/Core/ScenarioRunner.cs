using System;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using CAN;

namespace Simulador.Core
{
    public class ScenarioRunner
    {
        private readonly CanService _canService;
        private CancellationTokenSource _cts;
        public bool IsRunning { get; private set; }

        public ScenarioRunner(CanService canService)
        {
            _canService = canService;
            _cts = new CancellationTokenSource();
        }

        public void Stop()
        {
            if (IsRunning)
            {
                _cts.Cancel();
                IsRunning = false;
                Program.WriteMessage("[CENÁRIO] Simulação abortada.");
            }
        }

        public async Task RunAsync(string scenarioName)
        {
            if (IsRunning)
            {
                Program.WriteMessage("[ERRO] Já existe um cenário a correr. Pára-o primeiro.");
                return;
            }

            // Mapeia os teus comandos para ficheiros reais
            string fileName = scenarioName.ToLower() switch
            {
                "routepedro1" => "rota_hibrida_fall.csv",
                "routepedro2" => "rota_hibrida_fcw.csv",
                "routepedro3" => "rota_real.csv",
                _ => null
            };

            if (fileName == null || !File.Exists(fileName))
            {
                Program.WriteMessage($"[ERRO] Cenário '{scenarioName}' não reconhecido ou ficheiro '{fileName}' não encontrado na raiz.");
                return;
            }

            IsRunning = true;
            SimulationState.HasCrashed = false;
            _cts = new CancellationTokenSource();
            Program.WriteMessage($"[CENÁRIO] A iniciar {fileName}...");

            try
            {
                // Lê o ficheiro e ignora a primeira linha se for cabeçalho
                var lines = File.ReadAllLines(fileName).Where(l => !l.StartsWith("TempoEspera") && !string.IsNullOrWhiteSpace(l));

                foreach (var line in lines)
                {
                    if (_cts.IsCancellationRequested) break;

                    var parts = line.Split(',');
                    if (parts.Length < 7) continue;

                    // 1. Extração
                    int waitMs = int.Parse(parts[0]);
                    double lat = double.Parse(parts[1], CultureInfo.InvariantCulture);
                    double lon = double.Parse(parts[2], CultureInfo.InvariantCulture);
                    int speed = int.Parse(parts[3]);
                    int battery = int.Parse(parts[4]);
                    double roll = double.Parse(parts[5], CultureInfo.InvariantCulture);
                    string alert = parts[6].Trim().ToUpper();


                    if (SimulationState.HasCrashed)
                    {
                        // Mota no chão: forçamos a velocidade a zero. 
                        // Ignoramos o lat e lon do ficheiro CSV, o que congela a mota no mapa.
                        SimulationState.Speed = 0;
                        SimulationState.Battery = battery; // Bateria continua a drenar
                        Program.WriteMessage("[DEBUG-MOTOR] Mota bloqueada no chão! Ignorando GPS do CSV...");
                    }
                    else
                    {
                        // Condução normal
                        SimulationState.Latitude = lat;
                        SimulationState.Longitude = lon;
                        SimulationState.Speed = speed;
                        SimulationState.Battery = battery;
                    }

                    // 3. Injeção de alertas diretos para o cabo CAN
                    TriggerAlerts(alert);

                    // 4. Espera exata antes de processar a próxima linha
                    await Task.Delay(waitMs, _cts.Token);
                }

                Program.WriteMessage("[CENÁRIO] Concluído com sucesso.");
            }
            catch (Exception ex)
            {
                Program.WriteMessage($"[ERRO] Falha ao correr cenário: {ex.Message}");
            }
            finally
            {
                IsRunning = false;
            }
        }

        private void TriggerAlerts(string alert)
        {
            switch (alert)
            {
                case "FALL":
                    Program.WriteMessage("[!!!] A injetar Alerta de Queda no CAN [!!!]");
                    _canService.EnviarAlertaQueda();
                    break;
                case "RAIN_START":
                    Program.WriteMessage("[!!!] A injetar Chuva ON no CAN [!!!]");
                    _canService.EnviarChuva(true);
                    break;
                case "RAIN_STOP":
                    Program.WriteMessage("[!!!] A injetar Chuva OFF no CAN [!!!]");
                    _canService.EnviarChuva(false);
                    break;
                case "THEFT":
                    Program.WriteMessage("[!!!] A injetar Alerta de Roubo no CAN [!!!]");
                    _canService.EnviarAlertaRoubo();
                    break;
                case "FCW":
                    Program.WriteMessage("[!!!] A injetar Aproximação Frontal no CAN [!!!]");
                    _canService.EnviarFcw(15.0, 1.5, "CRITICAL");
                    break;
            }
        }
    }
}