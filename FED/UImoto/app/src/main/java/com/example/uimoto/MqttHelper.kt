package com.example.uimoto

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.json.JSONException
import org.json.JSONObject

object MqttHelper {

    private var mqttClient: MqttClient? = null
    private var mediaPlayer: MediaPlayer? = null

    // Connect to the MQTT broker
    fun connectToBroker(
        context: Context,
        brokerUrl: String = "tcp://172.20.0.203:1884",
        clientId: String = "AndroidClient"
    ) {
        if (mqttClient == null) {
            mqttClient = MqttClient(brokerUrl, clientId, null)
        }

        // Define as opcoes de conexao do cliente mqtt
        val options = MqttConnectOptions().apply {
            isCleanSession =
                true // Verifica se o cliente e o servidor se lembram do estado do cliente após vários reconnects
        }

        try {
            mqttClient?.connect(options)
            subscribeToTopics()  // Call subscribe function once connected
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    // Subscribe to MQTT topics
    private fun subscribeToTopics() {
        mqttClient?.apply {
            try {
                subscribe("moto/gear")
                subscribe("moto/autonomy")
                subscribe("moto/drive_mode")
                subscribe("moto/indicator_right")
                subscribe("moto/indicator_left")
                subscribe("moto/lights")
                subscribe("moto/maxlights")
                subscribe("moto/abs")
                subscribe("moto/speed")
                subscribe("moto/battery")
                subscribe("moto/temperature")
                subscribe("moto/time")
                subscribe("moto/total_kilometers")
                subscribe("moto/danger-lights")
                subscribe("simsensor/blindspot")
                subscribe("simsensor/pedestrian")
                subscribe("simsensor/rearcollision")
                subscribe("simsensor/frontalcollision")
            } catch (e: MqttException) {
                e.printStackTrace()
            }
        }
    }

    // Set the callback for MQTT messages
    fun setCallback(callback: MqttCallback) {
        mqttClient?.setCallback(callback)
    }

    // Disconnect from MQTT broker
    fun disconnect() {
        mqttClient?.apply {
            try {
                disconnect()
            } catch (e: MqttException) {
                e.printStackTrace()
            }
        }
    }

    // Check if the client is connected
    fun isConnected(): Boolean {
        return mqttClient?.isConnected == true
    }

    // Get the current MQTT client instance
    fun getClient(): MqttClient? {
        return mqttClient
    }

    // ============================================================================================================================

    // FUNCAO QUE ATUALIZA O VALOR DA MARCHA DA MOTA
    fun updateGear(context: Context, gear: String, gearBox: TextView) {
        val gearDisplay = when (gear) {
            "front" -> "D"  // "front" translates to "Drive"
            "back" -> "R"   // "rear" translates to "Reverse"
            "park" -> "P"   // "park" translates to "Park"
            "neutral" -> "N" // "neutral" translates to "Neutral"
            else -> "?"     // In case of an invalid gear value
        }

        // Get the current locale (language) of the app
        val currentLanguage = context.resources.configuration.locales.get(0).language

        // Check if the current language is not English, then use translations
        val gearTranslation = if (currentLanguage != "en") {
            when (gear) {
                "front" -> context.getString(R.string.gear_drive) // Replace with your translation for "Drive"
                "back" -> context.getString(R.string.gear_rear) // Replace with your translation for "Reverse"
                "park" -> context.getString(R.string.gear_park)    // Replace with your translation for "Park"
                "neutral" -> context.getString(R.string.gear_neutral) // Replace with your translation for "Neutral"
                else -> ""
            }
        } else {
            gearDisplay  // Default to English gear value (D, R, P, N)
        }

        // Update the TextView with the gear value (and translated value if not English)
        gearBox.text = gearTranslation
    }

    // FUNCAO QUE ATUALIZA O VALOR DA AUTONOMIA DA MOTA
    fun updateAutonomy(context: Context, autonomy: String, autonomyValue: TextView) {
        autonomyValue.text = autonomy + "km"
    }

    // FUNCAO QUE ATUALIZA O VALOR DO TEMPO DA MOTA
    fun updateTime(context: Context, time: String, timeDisplay: TextView) {
        timeDisplay.text = time
    }

    // FUNCAO QUE ATUALIZA O MODO DE CONDUCAO DA MOTA
    fun updateDriveMode(context: Context, driveMode: String, driveModeValue: TextView) {
        val driveModeDisplay = when (driveMode) {
            "eco" -> "Eco"  // "front" translates to "Drive"
            "standard" -> "Standard"   // "rear" translates to "Reverse"
            "sport" -> "Sport"   // "park" translates to "Park"
            else -> "?"     // In case of an invalid gear value
        }

        // Get the current locale (language) of the app
        val currentLanguage = context.resources.configuration.locales.get(0).language

        // Check if the current language is not English, then use translations
        val driveModeTranslation = if (currentLanguage != "en") {
            when (driveMode) {
                "eco" -> "Eco"  // "front" translates to "Drive"
                "standard" -> "Normal"   // "rear" translates to "Reverse"
                "sport" -> "Desportivo"   // "park" translates to "Park"
                else -> ""
            }
        } else {
            driveModeDisplay  // Default to English gear value (D, R, P, N)
        }

        // Update the TextView with the gear value (and translated value if not English)
        driveModeValue.text = driveModeTranslation
    }

    // FUNCAO QUE ATUALIZA O VALOR DA QUILOMETRAGEM DA MOTA
    fun updateTotalKilometers(context: Context, kilometers: String, totalKm: TextView) {
        totalKm.text = kilometers
    }


    // FUNCAO QUE CONTROLA O PISCA ESQUERDO DA MOTA
    fun updateIndicatorLeft(context: Context, value: Boolean, helperFunction: HelperFunction) {
        if (value) {
            helperFunction.toggleLeftSignalBlinking()
        }
    }

    // FUNCAO QUE CONTROLA O PISCA DIREITO DA MOTA
    fun updateIndicatorRight(context: Context, value: Boolean, helperFunction: HelperFunction) {
        if (value) {
            helperFunction.toggleRightSignalBlinking()
        }
    }

    // FUNCAO QUE CONTROLA AS LUZES DE PERIGO DA MOTA
    fun updateDangerLights(context: Context, value: Boolean, helperFunction: HelperFunction) {
        if (value) {
            helperFunction.toggleDangerLightsBlinking()
        }
    }

    // FUNCAO QUE CONTROLA AS LUZES MÉDIAS DA MOTA
    fun updateMediumLights(context: Context, value: Boolean, helperFunction: HelperFunction) {
        if (value) {
            helperFunction.toggleHeadlights()
        }
    }

    // FUNCAO QUE CONTROLA OS MAXIMOS DA MOTA
    fun updateHighBeam(context: Context, value: Boolean, helperFunction: HelperFunction) {
        if (value) {
            helperFunction.toggleHighBeam()
        }
    }

    // FUNCAO QUE CONTROLA A LUZ DO SISTEMA ABS DA MOTA
    fun updateABS(context: Context, value: Boolean, helperFunction: HelperFunction) {
        if (value) {
            helperFunction.toggleABS()
        }
    }

    // FUNCAO QUE ATUALIZA OS VALORES RELACIONADOS COM A BATERIA
    fun updateBattery(
        context: Context,
        percentage: Int,
        batteryFilling: ImageView,
        batteryDisplay: TextView
    ) {
        // Cria um BitMap com a forma inicial
        val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.batteryfillingnew)

        // Calcula a altura do enchimento com base na percentagem
        val fillHeight = (bitmap.height * (percentage / 100f)).toInt()

        // Determina a cor do gradiente a ser utilizado com base no valor da temperatura da bateria
        val gradientColor = when {
            percentage <= 10 -> ContextCompat.getColor(
                context,
                R.color.red
            )      // Quando for <= 10% é vermelho
            percentage <= 30 -> ContextCompat.getColor(
                context,
                R.color.orange
            )  // Quando for <= 30% é laranja
            else -> ContextCompat.getColor(context, R.color.green)              // De resto é verde
        }

        // Cria um bitmap para guardar o resultado final
        val finalBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(finalBitmap)

        // Desenha o Bitmap no Canvas
        canvas.drawBitmap(bitmap, 0f, 0f, null)

        // Cria o gradiente para preencher a barra da bateria
        val gradient = LinearGradient(
            0f, (bitmap.height - fillHeight).toFloat(), 0f, bitmap.height.toFloat(),
            gradientColor, gradientColor, Shader.TileMode.CLAMP
        )

        // Aplica o gradiente apenas a áreas não transparentes de maneira a não pintar o fundo da imagem
        val paint = Paint().apply {
            shader = gradient
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        }

        // Desenha o gradiente em cima do Bitmap para preencher de acordo com a percentagem da bateria
        canvas.drawRect(
            0f, (bitmap.height - fillHeight).toFloat(),
            bitmap.width.toFloat(), bitmap.height.toFloat(),
            paint
        )

        // Coloca o bitmap final com o efeito de gradiente na ImageView
        batteryFilling.setImageBitmap(finalBitmap)

        // Atualiza o texto da bateria
        batteryDisplay.text = "$percentage"
    }

    // FUNCAO QUE ATUALIZA OS VALORES RELACIONADOS COM O VELOCIMETRO
    fun updateSpeedometer(
        context: Context,
        speed: Int,
        speedometerFilling: ImageView?,
        speedBox: TextView,
        maxSpeed: Int?
    ) {
        // Cria um BitMap com a forma inicial
        val bitmap =
            BitmapFactory.decodeResource(context.resources, R.drawable.speedometerfillingnew)

        // Calcula a altura do enchimento com base na velocidade
        val fillHeight = (bitmap.height * (speed / maxSpeed!!.toFloat())).toInt()
        val gradientColor = ContextCompat.getColor(context, R.color.lightBlue)

        // Cria um bitmap para guardar o resultado final
        val finalBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(finalBitmap)

        // Desenha o Bitmap no Canvas
        canvas.drawBitmap(bitmap, 0f, 0f, null)

        // Cria o gradiente para preencher a barra da velocidade
        val gradient = LinearGradient(
            0f, (bitmap.height - fillHeight).toFloat(), 0f, bitmap.height.toFloat(),
            gradientColor, gradientColor, Shader.TileMode.CLAMP
        )

        // Aplica o gradiente apenas a áreas não transparentes de maneira a não pintar o fundo da imagem
        val paint = Paint().apply {
            shader = gradient
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        }

        // Desenha o gradiente em cima do Bitmap para preencher de acordo com a velocidade
        canvas.drawRect(
            0f,
            (bitmap.height - fillHeight).toFloat(),
            bitmap.width.toFloat(),
            bitmap.height.toFloat(),
            paint
        )

        // Coloca o bitmap final com o efeito de gradiente na ImageView
        speedometerFilling?.setImageBitmap(finalBitmap)

        // Muda o valor da velocidade em texto
        speedBox.text = speed.toString()
    }

    // FUNCAO QUE ATUALIZA OS VALORES RELACIONADOS COM O VELOCIMETRO NO MENU DO GPS
    fun updateBarMenus(
        context: Context,
        value: Int,
        valueBar: ImageView,
        valueDisplay: TextView,
        maxValue: Int,
        gradientColor: Int,
        unit : String,
        topic: String
    ) {
        // Load the SVG resource as a drawable
        val svgDrawable: Drawable? = AppCompatResources.getDrawable(context, R.drawable.gpsvaluebar)

        // Check if the drawable is null
        if (svgDrawable == null) {
            Log.e("com.example.uimoto.MqttHelper", "Failed to load SVG drawable.")
            return // Handle the error or fallback
        }

        // Convert the drawable to a bitmap
        val baseBitmap = svgDrawable.toBitmap()

        // Calculate the fill percentage based on the speed
        val fillPercentage = (value.coerceIn(0, maxValue).toFloat() / maxValue)

        var adjustedGradientColor = gradientColor

        if(topic=="battery") {
            // Determine if the value is below a certain threshold and if the topic is "battery"
            if (value <= 10) {
                adjustedGradientColor = ContextCompat.getColor(
                    context,
                    R.color.red
                ) // Change the color to red for low battery
            } else if (value <= 30 && value > 10) {
                adjustedGradientColor = ContextCompat.getColor(context, R.color.orange)
            } else adjustedGradientColor = ContextCompat.getColor(context, R.color.green)
        }
        // Create a bitmap to hold the result (final image)
        val finalBitmap =
            Bitmap.createBitmap(baseBitmap.width, baseBitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(finalBitmap)

        // Draw the base bitmap (SVG image)
        canvas.drawBitmap(baseBitmap, 0f, 0f, null)

        // Create a gradient to fill the speedometer from right to left
        val gradient = LinearGradient(
            baseBitmap.width.toFloat(),
            0f, // Start at the right edge
            baseBitmap.width * (1 - fillPercentage).toFloat(),
            0f, // End at the left, adjusting based on speed
            adjustedGradientColor,
            adjustedGradientColor,
            Shader.TileMode.CLAMP
        )

        // Create a paint object with the gradient
        val paint = Paint().apply {
            shader = gradient
            xfermode =
                PorterDuffXfermode(PorterDuff.Mode.SRC_IN) // Only fill the non-transparent areas
        }

        // Draw the gradient on the canvas, filling the speedometer
        canvas.drawRect(
            0f, 0f, baseBitmap.width * fillPercentage, baseBitmap.height.toFloat(),
            paint
        )

        // Set the final bitmap (filled speedometer) as the ImageView's drawable
        valueBar.setImageBitmap(finalBitmap)

        // Update the speed value in the TextView
        valueDisplay.text = "$value $unit"
    }

    // FUNCAO QUE ATUALIZA OS VALORES RELACIONADOS COM O VELOCIMETRO
    fun updateSpeedometerInterface1(
        context: Context,
        speed: Int,
        speedometerFilling: ImageView,
        speedBox: TextView,
        maxSpeed: Int
    ) {
        // Load the base bitmap
        val originalBitmap =
            BitmapFactory.decodeResource(context.resources, R.drawable.speedometerfillingtest)

        // Create a new bitmap with the same dimensions
        val finalBitmap = Bitmap.createBitmap(
            originalBitmap.width,
            originalBitmap.height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(finalBitmap)

        // Clear the canvas to fully transparent
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        // Calculate the fill height based on speed
        val fillHeight = (originalBitmap.height * (speed / maxSpeed.toFloat())).toInt()

        // Create the gradient for the fill
        val gradientColor = ContextCompat.getColor(context, R.color.lightBlue)
        val gradient = LinearGradient(
            0f,
            (originalBitmap.height - fillHeight).toFloat(),
            0f,
            originalBitmap.height.toFloat(),
            gradientColor,
            gradientColor,
            Shader.TileMode.CLAMP
        )
        val paintGradient = Paint().apply {
            shader = gradient
        }

        // Draw the gradient directly onto the canvas
        canvas.drawRect(
            0f,
            (originalBitmap.height - fillHeight).toFloat(),
            originalBitmap.width.toFloat(),
            originalBitmap.height.toFloat(),
            paintGradient
        )

        // Mask the gradient using the original bitmap's alpha
        val maskPaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.drawBitmap(originalBitmap, 0f, 0f, maskPaint)

        // Set the final bitmap to the ImageView
        speedometerFilling.setImageBitmap(finalBitmap)

        // Update the speed value in the TextView
        speedBox.text = speed.toString()
    }

    // FUNCAO QUE ATUALIZA OS VALORES RELACIONADOS COM OS SENSORES
    fun updateSensorWarning(context: Context, topic: String, payload: String, sensorText: TextView) {
        try {
            val jsonObject = JSONObject(payload)
            val status = jsonObject.optString("Status", "false") // Default to "false"

            // Ensure the message only appears if status is "true"
            if (status.lowercase() == "true") {
                Log.d("SENSOR", "Status is true")


            // Extract 'Data' object correctly
            val dataObject = jsonObject.optJSONObject("Data")
            val side = dataObject?.optString("Side", "Unknown") ?: "Unknown"
            val distance = dataObject?.optDouble("DistanceToVehicle", -1.0) ?: -1.0

            // Translate side from Portuguese to English
            val sideFormatted = when (side.lowercase()) {
                "esquerda" -> context.getString(R.string.left)
                "direita" ->  context.getString(R.string.right)
                else -> context.getString(R.string.unknown_side)
            }

            // Format distance properly
            val distanceFormatted = if (distance >= 0) "$distance meters" else context.getString(R.string.unknown_distance)

            // Select the message based on the topic
            val formattedMessage = when (topic) {
                "simsensor/blindspot" -> {
                    stopCollisionSound()  // Stop sound if it's a non-collision topic
                    context.getString(R.string.blindspot_warning, sideFormatted, distanceFormatted)
                }
                "simsensor/pedestrian" -> {
                    stopCollisionSound()  // Stop sound if it's a non-collision topic
                    context.getString(R.string.pedestrian_warning, sideFormatted, distanceFormatted)
                }
                "simsensor/rearcollision" -> {
                    playCollisionSound(context) // Play sound for rear collision
                    context.getString(R.string.rear_collision_warning, distanceFormatted)
                }
                "simsensor/frontalcollision" -> {
                    playCollisionSound(context) // Play sound for frontal collision
                    context.getString(R.string.frontal_collision_warning, distanceFormatted)
                }
                else -> {
                    stopCollisionSound()  // Stop sound for unknown topics
                    "Unknown sensor warning."
                }
            }

            (context as Activity).runOnUiThread {
                sensorText.text = formattedMessage
                sensorText.visibility = View.VISIBLE
            }
            }else {
                Log.d("SENSOR", "Status is not true, no message will be displayed.")
                (context as Activity).runOnUiThread {
                    sensorText.visibility = View.GONE
                }
            }
        } catch (e: JSONException) {
            Log.e("ERRORJSON", "Error parsing JSON: ${e.message}")
        }
    }

    private fun playCollisionSound(context: Context) {
        stopCollisionSound()
        mediaPlayer = MediaPlayer.create(context, R.raw.collisionsound).apply{  // Replace with your sound file
        isLooping = true // Loop the sound continuously
        start() // Start playing the sound
        }
    }
    private fun stopCollisionSound() {
        mediaPlayer?.stop() // Stop the sound
        mediaPlayer?.release() // Release MediaPlayer resources
        mediaPlayer = null // Reset MediaPlayer
    }
}



