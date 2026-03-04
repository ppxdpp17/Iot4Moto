# Simulador de Motociclo

Este projeto é uma aplicação de consola em .NET C# que simula o comportamento de um motociclo elétrico. Ele interage com um sistema maior através de MQTT e pode simular dados de sensores, bateria, velocidade, geolocalização (GPS) e comunicação CAN bus.

## Funcionalidades Principais

*   **Simulação de Sensores:** Simula sensores de deteção de ângulo morto, colisão frontal/traseira, peões, etc.
*   **Gestão de Estado:** Controla o estado da mota (ligado/desligado), velocidade, bateria, mudanças, luzes, piscas, ABS, modos de condução (Eco, Sport, Standard).
*   **Navegação/Rotas:** Simula percorrer rotas pré-definidas com atualização de coordenadas GPS.
*   **Carregamento:** Simula o processo de carregamento da bateria.
*   **Integração MQTT:** Publica e subscreve tópicos MQTT para comunicar com outros componentes do sistema (ex: Dashboard).
*   **Integração CAN:** Possui capacidade de comunicação com barramento CAN (via `simplyCAN`).
*   **Logging:** Regista dados de performance e eventos em ficheiros JSON e base de dados PostgreSQL.

## Pré-requisitos

*   [.NET SDK 8.0](https://dotnet.microsoft.com/download/dotnet/8.0)
*   Servidor MQTT (ex: Mosquitto)
*   PostgreSQL (opcional, para logging avançado)
*   Docker (opcional, para executar em contentor)

## Configuração

A aplicação utiliza um ficheiro `.env` na raiz para configuração. Certifique-se de criar ou configurar este ficheiro antes de executar.

Exemplo de `.env`:

```ini
# Configuração MQTT
MQTT_HOST=172.20.0.203  # Endereço do broker MQTT
MQTT_PORT=1883          # Porta do broker MQTT

# Configuração PostgreSQL
PG_HOST=172.20.0.201
PG_PORT=5432
PG_DB=rotasimulador
PG_USER=simuser
PG_PASSWORD=Simulador
```

## Como Executar

### Opção 1: Executar Localmente (.NET CLI)

1.  Abra o terminal na pasta do projeto.
2.  Restaure as dependências:
    ```bash
    dotnet restore
    ```
3.  Execute a aplicação:
    ```bash
    dotnet run
    ```

### Opção 2: Executar com Docker

1.  Certifique-se de que o Docker está instalado e a correr.
2.  Construa a imagem Docker:
    ```bash
    docker build -t simulador-mota .
    ```
3.  Execute o contentor (certifique-se de que a rede permite acesso ao broker MQTT):
    ```bash
    docker run --env-file .env -it simulador-mota
    ```

## Utilização

Ao iniciar a aplicação, será solicitado que escolha o **VIN (Vehicle Identification Number)** da mota a simular:

```text
1 - AJP1RB5SXLB123456
2 - AJP7XZ8MWQ2Y1R3K
```

Após a seleção, entrará na consola interativa.

### Comandos Disponíveis

A consola aceita comandos de texto para controlar a simulação. Alguns dos principais comandos:

*   **Controlo Geral:**
    *   `start` - Liga a mota.
    *   `stop` - Desliga a mota.
    *   `exit` - Fecha o simulador.

*   **Carregamento:**
    *   `charge start` - Inicia o carregamento (a mota deve estar parada).
    *   `charge stop` - Para o carregamento.

*   **Condução (Requer mota ligada):**
    *   `speed +<val>` / `speed -<val>` - Aumenta/diminui a velocidade (ex: `speed +10`).
    *   `brake <val>` - Aplica travagem com intensidade específica.
    *   `gear <valor>` - Muda a mudança (`front`, `back`, `neutral`, `park`).
    *   `mode <modo>` - Altera modo de condução (`standard`, `eco`, `sport`).

*   **Luzes e Sinais:**
    *   `lights on` / `lights off` - Controla luzes principais.
    *   `indicator left` / `indicator right` / `indicator none` - Controla piscas.
    *   `maxlights on` / `maxlights off` - Máximos.
    *   `danger on` / `danger off` - Luzes de perigo (4 piscas).

*   **Sistemas:**
    *   `abs on` / `abs off` - Ativa/Desativa ABS.
    *   `battery -<val>` - Consome bateria manualmente.
    *   `temp +<val>` / `temp -<val>` - Simula alteração de temperatura.

*   **Rotas e Navegação:**
    *   `route start <nome>` - Inicia uma rota simulada (ex: `route start route1`).
    *   `route stop` - Para a simulação da rota.
    *   `gps` - Mostra coordenadas atuais.
    *   `auto <nome>` - Inicia uma simulação automática completa de um cenário.

*   **Sensores:**
    *   `send <sensor>` - Envia mensagem de sensor específica (ex: `send blindspot`).
    *   `sensors` - Entra no menu manual de envio de sensores.
