package com.example.uimoto

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
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException

object MqttHelper {

    private var mqttClient: MqttClient? = null

    // Connect to the MQTT broker
    fun connectToBroker(
        context: Context,
        brokerUrl: String = "tcp://192.168.1.10:1883",
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
                subscribe("moto/range")
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
                subscribe("moto/alarm")


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
        gearBox.text = gear
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
        driveModeValue.text = driveMode
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

    // FUNCAO QUE ATUALIZA OS VALORES RELACIONADOS COM A TEMPERATURA DA BATERIA
    fun updateBatteryTemperature(
        context: Context,
        batteryTemperature: Int,
        batteryTemperatureFilling: ImageView
    ) {
        // Cria um BitMap com a forma inicial
        val originalBitmap =
            BitmapFactory.decodeResource(context.resources, R.drawable.batterytemperaturefilling)

        // Calcula a altura do enchimento com base na temperatura da bateria
        val fillHeight = (originalBitmap.height * (batteryTemperature / 100f)).toInt()
        val gradientColor = ContextCompat.getColor(context, R.color.lightBlue)

        // Cria um bitmap para guardar o resultado final
        val finalBitmap = Bitmap.createBitmap(
            originalBitmap.width,
            originalBitmap.height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(finalBitmap)

        // Desenha o Bitmap no Canvas
        canvas.drawBitmap(originalBitmap, 0f, 0f, null)

        // Cria o gradiente para preencher a barra da temperatura da bateria
        val gradient = LinearGradient(
            0f, (originalBitmap.height - fillHeight).toFloat(), 0f, originalBitmap.height.toFloat(),
            gradientColor, gradientColor, Shader.TileMode.CLAMP
        )

        // Aplica o gradiente apenas a áreas não transparentes de maneira a não pintar o fundo da imagem
        val gradientPaint = Paint().apply {
            shader = gradient
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        }

        // Desenha o gradiente em cima do Bitmap para preencher de acordo com a temperatura da bateria
        canvas.drawRect(
            0f, (originalBitmap.height - fillHeight).toFloat(),
            originalBitmap.width.toFloat(), originalBitmap.height.toFloat(),
            gradientPaint
        )

        // Coloca o bitmap final com o efeito de gradiente na ImageView
        batteryTemperatureFilling.setImageBitmap(finalBitmap)
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
}