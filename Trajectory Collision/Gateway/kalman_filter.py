class KalmanFilter:
    def __init__(self, process_noise=0.01, measurement_noise=0.1, estimation_error=1.0, initial_value=0.0):
        self.q = process_noise       # Ruído do processo (o carro muda de velocidade?)
        self.r = measurement_noise   # Ruído da medição (a câmara treme?)
        self.p = estimation_error    # Erro de estimativa
        self.x = initial_value       # Valor atual (estado)
        self.k = 0.0                 # Ganho de Kalman

    def update(self, measurement):
        # 1. Predição
        self.p = self.p + self.q

        # 2. Atualização
        self.k = self.p / (self.p + self.r)
        self.x = self.x + self.k * (measurement - self.x)
        self.p = (1 - self.k) * self.p

        return self.x
