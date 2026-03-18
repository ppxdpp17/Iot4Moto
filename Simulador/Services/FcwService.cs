using System;
using System.Collections.Generic;
using System.Device.Gpio;
using System.Diagnostics;
using System.Linq;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using CAN;
using Simulador.Core;

namespace Simulador.Services
{
    public class FcwService
    {
        // Define os pinos BCM onde ligaste o sensor (Muda para os teus pinos reais)
        private const int TRIG_PIN = 23; 
        private const int ECHO_PIN = 24; 

        private readonly MqttService _mqttService;
        private readonly CanService _canService;
        private GpioController? _gpio;

        //Para evitar spam de alertas
        private DateTime _lastAlarmTime = DateTime.MinValue;

        // Histórico para cálculo de velocidade (Tempo, Distância)
        private Queue<Tuple<DateTime, double>> _history = new Queue<Tuple<DateTime, double>>();
        private const int MAX_HISTORY = 6;

        public FcwService(MqttService mqttService, CanService canService)
        {
            _mqttService = mqttService;
            _canService = canService;
        }

        public async Task StartAsync(CancellationToken token)
        {
            try
            {
                _gpio = new GpioController();
                _gpio.OpenPin(TRIG_PIN, PinMode.Output);
                _gpio.OpenPin(ECHO_PIN, PinMode.Input);
                _gpio.Write(TRIG_PIN, PinValue.Low);

                //Program.WriteMessage("[FCW] Serviço Ultrassom Iniciado.");

                while (!token.IsCancellationRequested)
                {
                    if (SimulationState.IsMotorcycleOn)
                    {
                        //Se a velocidade for 15 km/h ou menos, ignora e limpa o histórico
                        if (SimulationState.Speed <= 15)
                        {
                            _history.Clear();
                            await Task.Delay(100, token);
                            continue;
                        }

                        double dist = MeasureDistance();

                        if (dist > 0 && dist < 4.0) 
                        {
                            ProcessFcwLogic(dist);
                        }
                        else
                        {
                            _history.Clear(); 
                        }
                    }

                    await Task.Delay(100, token); // Loop a 10Hz (100ms) é ideal para o sensor sonoro
                }
            }
            catch (Exception ex)
            {
                Program.WriteMessage($"[FCW] Erro GPIO: {ex.Message}");
            }
            finally
            {
                _gpio?.Dispose();
            }
        }

        private double MeasureDistance()
        {
            if (_gpio == null) return -1;

            // Envia um pulso de 10 microsegundos
            _gpio.Write(TRIG_PIN, PinValue.High);
            DelayMicroseconds(10);
            _gpio.Write(TRIG_PIN, PinValue.Low);

            Stopwatch sw = new Stopwatch();
            
            // Espera o sinal de retorno ficar Alto (Timeout de 50ms para não bloquear a thread)
            sw.Start();
            while (_gpio.Read(ECHO_PIN) == PinValue.Low && sw.ElapsedMilliseconds < 50) { }
            if (sw.ElapsedMilliseconds >= 50) return -1;

            // Mede o tempo que o sinal fica Alto
            sw.Restart();
            while (_gpio.Read(ECHO_PIN) == PinValue.High && sw.ElapsedMilliseconds < 50) { }
            double elapsedSeconds = sw.Elapsed.TotalSeconds;

            // Distância = (Tempo * Velocidade do Som 343m/s) / 2 (ida e volta)
            return (elapsedSeconds * 343.0) / 2.0;
        }

        private void ProcessFcwLogic(double currentDistance)
        {
            var now = DateTime.UtcNow;
            _history.Enqueue(new Tuple<DateTime, double>(now, currentDistance));
            if (_history.Count > MAX_HISTORY) _history.Dequeue();

            double vel = 0.0;
            double ttc = 999.0;
            string type = "INFO";

            if (_history.Count >= 3)
            {
                var oldest = _history.First();
                var newest = _history.Last();
                double dt = (newest.Item1 - oldest.Item1).TotalSeconds;

                if (dt > 0)
                {
                    // Velocidade de aproximação (m/s)
                    vel = (oldest.Item2 - newest.Item2) / dt;

                    // Ignora velocidades residuais/ruído sonoro (< 10cm/s)
                    if (vel > 0.1) 
                    {
                        ttc = currentDistance / vel;
                    }
                }
            }

            if (ttc < 1.0)
            {
                type = "CRITICAL";
                //Só dispara se tiverem passado 5 segundos desde o último alarme
                if ((now - _lastAlarmTime).TotalSeconds > 5)
                {
                    _lastAlarmTime = now;
                    //Program.WriteMessage("\n!!! ALERTA ECU: Colisão Frontal Iminente !!!");
                    _canService.EnviarFcw(currentDistance, ttc, type);
                }
            }
            else if (ttc < 2.5)
            {
                type = "WARNING";
                _canService.EnviarFcw(currentDistance, ttc, type);
            }
            else 
            {
                //Envia INFO normal
                _canService.EnviarFcw(currentDistance, ttc, type);
            }

            _canService.EnviarFcw(currentDistance, ttc, type);
        }

        // Função auxiliar para delays muito curtos em C# Linux
        private void DelayMicroseconds(long microseconds)
        {
            var sw = Stopwatch.StartNew();
            long ticks = microseconds * Stopwatch.Frequency / 1000000;
            while (sw.ElapsedTicks < ticks) { }
        }
    }
}