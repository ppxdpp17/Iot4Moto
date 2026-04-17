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
        //Endereço GPIO do MPU6050
        private const int MPU6050_ADDR = 0x68;
        private readonly MqttService _mqttService;
        private readonly CanService _canService;
        private I2cDevice? _i2cDevice;
        private bool _isSensorInitialized = false;
        private DateTime _lastImpactTime = DateTime.MinValue;

        public ImuService(MqttService mqttService, CanService canService)
        {
            _mqttService = mqttService;
            _canService = canService;
        }

        public async Task StartAsync(CancellationToken token)
        {
            double roll = 0;
            const double alpha = 0.98;  //Peso do Giroscópio vs Acelerómetro
            const double dt = 0.01; //Delta Time (10ms = 100Hz)

            while (!token.IsCancellationRequested)
            {
                try
                {
                    //Tenta ligar e acordar o sensor se ainda não o fez
                    if (!_isSensorInitialized)
                    {
                        var settings = new I2cConnectionSettings(1, MPU6050_ADDR);
                        _i2cDevice = I2cDevice.Create(settings);

                        //O MPU6050 arranca em Sleep Mode. Enviar 0x00 para o registo de energia (0x6B) acorda-o.
                        _i2cDevice.Write(new byte[] { 0x6B, 0x00 });
                        _isSensorInitialized = true;
                        Program.WriteMessage("[IMU] Sensor ligado e ativo.");
                    }

                    //Acesso contínuo ao buffer de memória do MPU6050 (14 bytes contendo Acel, Giro e Temp)
                    byte[] writeBuf = { 0x3B };
                    byte[] readBuf = new byte[14];
                    _i2cDevice!.WriteRead(writeBuf, readBuf);

                    //Reconstrução dos valores analógicos puros de 16-bits
                    short ax = (short)((readBuf[0] << 8) | readBuf[1]);
                    short ay = (short)((readBuf[2] << 8) | readBuf[3]);
                    short az = (short)((readBuf[4] << 8) | readBuf[5]);
                    short gx = (short)((readBuf[8] << 8) | readBuf[9]);

                    //Calcular Orientação (Roll)
                    double accRoll = Math.Atan2(ay, az) * 180 / Math.PI;
                    double gyroRollRate = gx / 131.0;
                    roll = alpha * (roll + gyroRollRate * dt) + (1.0 - alpha) * accRoll;
                    SimulationState.Roll = roll;

                    //Calcular Força G (Sum Vector Magnitude)
                    double ax_g = ax / 16384.0;
                    double ay_g = ay / 16384.0;
                    double az_g = az / 16384.0;
                    double svm = Math.Sqrt(ax_g * ax_g + ay_g * ay_g + az_g * az_g);

                    //Enviar Roll pelo CAN
                    int rollInt = (int)(roll * 100);
                    byte[] canData = new byte[2];
                    canData[0] = (byte)(rollInt & 0xFF);
                    canData[1] = (byte)((rollInt >> 8) & 0xFF);
                    _canService.EnviarDadosSimulados(0x303, canData);

                    //Máquina de Estados (Mota Ligada vs Desligada)
                    if (SimulationState.IsMotorcycleOn)
                    {
                        //Começa por registar o momento do impacto (> 2.5G)
                        if (svm > 2.5) 
                        {
                            _lastImpactTime = DateTime.UtcNow;
                        }

                        //Confirma a queda, ou seja, teve um impacto nos últimos 3 segundos E inclinou mais de 60 graus?
                        bool recentImpact = (DateTime.UtcNow - _lastImpactTime).TotalSeconds < 3;
                        bool isTippedOver = Math.Abs(roll) > 60;

                        if (recentImpact && isTippedOver && !SimulationState.FallDetected)
                        {
                            SimulationState.FallDetected = true;
                            _canService.EnviarAlertaQueda();

                            _ = Task.Run(async () => {
                                await Task.Delay(10000); //Dá mais tempo antes de dar reset (10s)
                                SimulationState.FallDetected = false;
                            });
                        }
                    }
                    else
                    {
                        //Se está desligada, o sensor passa a assumir a função de Anti-Furto (apenas para svm maiores que 1.6)
                        if (svm > 1.6 && !SimulationState.TheftAlarmActive)
                        {
                            SimulationState.TheftAlarmActive = true;
                            _canService.EnviarAlertaRoubo();

                            _ = Task.Run(async () => {
                                await Task.Delay(10000);
                                SimulationState.TheftAlarmActive = false;
                            });
                        }
                    }

                    await Task.Delay(10, token); //Loop de 10ms (100Hz) normal
                }
                catch (System.IO.IOException)
                {
                    //Se falhar o I2C (ex: hardware não está lá)
                    if (_isSensorInitialized)
                    {
                        _isSensorInitialized = false; //Força re-inicialização no futuro
                    }
                    
                    //Espera 10 segundos antes de tentar de novo, para não entupir o CPU do Raspberry
                    await Task.Delay(10000, token);
                }
                catch (Exception ex)
                {
                    Program.WriteMessage($"[IMU] Erro inesperado: {ex.Message}");
                    await Task.Delay(5000, token);
                }
            }
        }
    }
}