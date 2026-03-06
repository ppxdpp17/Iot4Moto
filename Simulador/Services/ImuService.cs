using System;
using System.Device.I2c;
using System.Threading;
using System.Threading.Tasks;
using Simulador.Core;
using CAN;

namespace Simulador.Services
{
    public class ImuService
    {
        private const int MPU6050_ADDR = 0x68;
        private readonly MqttService _mqttService;
        private readonly CanService _canService;
        private I2cDevice? _i2cDevice;

        public ImuService(MqttService mqttService, CanService canService)
        {
            _mqttService = mqttService;
            _canService = canService;
        }

        public async Task StartAsync(CancellationToken token)
        {
            try
            {
                var settings = new I2cConnectionSettings(1, MPU6050_ADDR);
                _i2cDevice = I2cDevice.Create(settings);

                // Acordar o sensor
                _i2cDevice.Write(new byte[] { 0x6B, 0x00 });

                double roll = 0;
                const double alpha = 0.98;
                const double dt = 0.01;

                while (!token.IsCancellationRequested)
                {
                    byte[] writeBuf = { 0x3B };
                    byte[] readBuf = new byte[14];
                    _i2cDevice.WriteRead(writeBuf, readBuf);

                    short ax = (short)((readBuf[0] << 8) | readBuf[1]);
                    short ay = (short)((readBuf[2] << 8) | readBuf[3]);
                    short az = (short)((readBuf[4] << 8) | readBuf[5]);
                    short gx = (short)((readBuf[8] << 8) | readBuf[9]);
                    // gy e gz são irrelevantes para já

                    // 1. Calcular Orientação (Roll)
                    double accRoll = Math.Atan2(ay, az) * 180 / Math.PI;
                    double gyroRollRate = gx / 131.0;
                    roll = alpha * (roll + gyroRollRate * dt) + (1.0 - alpha) * accRoll;
                    SimulationState.Roll = roll;

                    // 2. Calcular Força G (Sum Vector Magnitude)
                    double ax_g = ax / 16384.0;
                    double ay_g = ay / 16384.0;
                    double az_g = az / 16384.0;
                    double svm = Math.Sqrt(ax_g * ax_g + ay_g * ay_g + az_g * az_g);

                    // Envia Roll pelo CAN para o Dashboard Web
                    int rollInt = (int)(roll * 100);
                    byte[] canData = new byte[2];
                    canData[0] = (byte)(rollInt & 0xFF);
                    canData[1] = (byte)((rollInt >> 8) & 0xFF);
                    _canService.EnviarDadosSimulados(0x300, canData);

                    // 3. Máquina de Estados (Mota Ligada vs Desligada)
                    if (SimulationState.IsMotorcycleOn)
                    {
                        // Queda (Ex: SVM > 2.5G)
                        if (svm > 2.5 && !SimulationState.FallDetected)
                        {
                            SimulationState.FallDetected = true;
                            await _mqttService.PublishAsync("moto/alarm", "FALL");
                            Program.WriteMessage("\n!!! ALERTA: Queda Detetada !!!");

                            // Cooldown não bloqueante
                            _ = Task.Run(async () => {
                                await Task.Delay(5000);
                                SimulationState.FallDetected = false;
                            });
                        }
                    }
                    else
                    {
                        // Anti-Furto (Ex: SVM > 1.2G quando desligada)
                        if (svm > 1.2 && !SimulationState.TheftAlarmActive)
                        {
                            SimulationState.TheftAlarmActive = true;
                            await _mqttService.PublishAsync("moto/alarm", "THEFT");
                            Program.WriteMessage("\n!!! ALERTA: Tentativa de Furto !!!");

                            // Cooldown não bloqueante
                            _ = Task.Run(async () => {
                                await Task.Delay(5000);
                                SimulationState.TheftAlarmActive = false;
                            });
                        }
                        
                    }

                    await Task.Delay(10, token); // Loop de 10ms (100Hz)
                }
            }
            catch (Exception ex)
            {
                Program.WriteMessage($"[IMU] Erro I2C: {ex.Message}");
            }
        }
    }
}