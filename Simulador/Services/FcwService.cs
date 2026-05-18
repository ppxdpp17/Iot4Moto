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
        //Pinos onde o sensor liga
        private const int TRIG_PIN = 23; 
        private const int ECHO_PIN = 24; 

        private readonly MqttService _mqttService;
        private readonly CanService _canService;
        private GpioController? _gpio;

        //Para evitar spam de alertas
        private DateTime _lastAlarmTime = DateTime.MinValue;

        //Histórico para cálculo de velocidade (Tempo, Distância)
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

                //Loop principal de aquisição
                while (!token.IsCancellationRequested)
                {
                    //Poupança de energia/processamento: Só mede se o motor estiver a trabalhar
                    if (SimulationState.IsMotorcycleOn)
                    {
                        double dist = MeasureDistance();

                        //O HC-SR04 só alcança até 4 metros
                        if (dist > 0 && dist < 4) 
                        {
                            ProcessFcwLogic(dist);
                        }
                        else
                        {
                            _history.Clear(); 
                        }
                    }

                    await Task.Delay(100, token); //Loop a 10Hz (100ms)
                }
            }
            catch (Exception ex)
            {
                Program.WriteMessage($"[FCW] Erro GPIO: {ex.Message}");
            }
            finally
            {
                _gpio?.Dispose();   //Previne hardware locks caso o software vá abaixo
            }
        }

        private double MeasureDistance()
        {
            if (_gpio == null) return -1;

            //Envia um pulso de 10ms
            _gpio.Write(TRIG_PIN, PinValue.High);
            DelayMicroseconds(10);
            _gpio.Write(TRIG_PIN, PinValue.Low);

            Stopwatch sw = new Stopwatch();
            
            //Espera o sinal de retorno ficar Alto (Timeout de 50ms para não bloquear a thread)
            sw.Start();
            while (_gpio.Read(ECHO_PIN) == PinValue.Low && sw.ElapsedMilliseconds < 50) { }
            if (sw.ElapsedMilliseconds >= 50) return -1;

            //Mede o tempo que o sinal fica Alto
            sw.Restart();
            while (_gpio.Read(ECHO_PIN) == PinValue.High && sw.ElapsedMilliseconds < 50) { }
            double elapsedSeconds = sw.Elapsed.TotalSeconds;

            //Distância = (Tempo * Velocidade do Som 343m/s) / 2 (ida e volta)
            return (elapsedSeconds * 343.0) / 2.0;
        }


        //Tomada de decisão
        private void ProcessFcwLogic(double currentDistance)
        {
            double ttc = 999.0;
            string type = "INFO"; 

            //Converte a velocidade do simulador de km/h para metros por segundo
            double speedMetersPerSecond = SimulationState.Speed / 3.6;

            //Se a mota estiver parada ou a andar muito devagar, não há colisão iminente
            if (speedMetersPerSecond > 0.1) 
            {
                //TTC = Distância / Velocidade
                ttc = currentDistance / speedMetersPerSecond;
            }

            //Só avisa acima de 30 km/h
            if (SimulationState.Speed >= 30)
            {
                if (ttc < 1.0)
                {
                    type = "CRITICAL";
                }
                else if (ttc < 2.5)
                {
                    type = "WARNING";
                }
            }

            _canService.EnviarFcw(currentDistance, ttc, type);
        }

        //Função utilitária de Busy Waiting (Micro-sleep)
        private void DelayMicroseconds(long microseconds)
        {
            var sw = Stopwatch.StartNew();
            long ticks = microseconds * Stopwatch.Frequency / 1000000;
            while (sw.ElapsedTicks < ticks) { }
        }
    }
}