using System.Text.Json.Serialization;

namespace Simulador.Models
{
    public class CanData
    {
        [JsonPropertyName("arbitration_id")]
        public int ArbitrationId { get; set; }

        [JsonPropertyName("data")]
        public int[] Data { get; set; } = new int[8];
    }
}
