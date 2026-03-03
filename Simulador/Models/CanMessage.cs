using System.Text.Json.Serialization;

namespace Simulador.Models
{
    public class CanMessage
    {
        [JsonPropertyName("algorithm_id")]
        public string AlgorithmID { get; set; } = string.Empty;

        [JsonPropertyName("can_message")]
        public CanData CAN_Message { get; set; } = new CanData();
    }
}
