package com.example.uimoto

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import androidx.core.content.ContextCompat

class HelperFunction(
    private val leftTurnSignal: ImageView?,
    private val rightTurnSignal: ImageView?,
    private val headlightsMedium: ImageView?,
    private val highBeam: ImageView?,
    private val abs : ImageView?,
    private val context: Context
) {
    private var isABSOn = false
    private var isMediumsOn = false
    private var isHighBeamOn = false
    private var isLeftBlinking = false
    private var isRightBlinking = false
    private var dangerLights = false
    private val blinkInterval = 500L

    // Toggle ABS
    fun toggleABS() {
        if (isABSOn) {
            abs?.imageTintList =
                ContextCompat.getColorStateList(context, R.color.lightGray)
            isABSOn = false
        } else{
            abs?.imageTintList =
                ContextCompat.getColorStateList(context, R.color.yellow)
            isABSOn = true
        }
    }

    // Toggle Headlights
    fun toggleHeadlights() {
        if (isMediumsOn) {
            headlightsMedium?.imageTintList =
                ContextCompat.getColorStateList(context, R.color.lightGray)
            isMediumsOn = false
        } else if (!isHighBeamOn) {
            headlightsMedium?.imageTintList =
                ContextCompat.getColorStateList(context, R.color.green)
            isMediumsOn = true
        }
    }

    // Toggle High Beam
    fun toggleHighBeam() {
        if (isHighBeamOn) {
            highBeam?.imageTintList =
                ContextCompat.getColorStateList(context, R.color.lightGray)
            isHighBeamOn = false
            if (isMediumsOn) {
                headlightsMedium?.imageTintList =
                    ContextCompat.getColorStateList(context, R.color.green)
            }
        } else {
            highBeam?.imageTintList =
                ContextCompat.getColorStateList(context, R.color.darkBlue)
            isHighBeamOn = true
            headlightsMedium?.imageTintList =
                ContextCompat.getColorStateList(context, R.color.lightGray)
        }
    }

    // FUNCOES RESPONSAVEIS PELOS PISCAS -----------------------------------------------------------------------------------------------
    // AS PROXIMAS TRES FUNCOES TRATAM DA ALTERNACAO DOS PISCAS E LUZES DE PERIGO

    fun toggleLeftSignalBlinking() {
        if (isLeftBlinking) {   // caso o pisca esteja ativado, quando a seta é pressionada no teclado o pisca desativa
            stopLeftSignalBlinking()
        } else {
            if (!isRightBlinking)
                startLeftSignalBlinking()   // caso o pisca esteja desativado, o pisca é ativado
        }
    }

    fun toggleRightSignalBlinking() {
        // o mesmo que a funcao acima, mas para o pisca da direita
        if (isRightBlinking) {
            stopRightSignalBlinking()
        } else {
            if (!isLeftBlinking)
                startRightSignalBlinking()
        }
    }

    fun toggleDangerLightsBlinking() {
        if (dangerLights) {   // caso luzes de perigo estejam ativadas, quando "P" é pressionado no teclado as luzes desativam
            stopDangerLightsBlinking()
        } else {
            startDangerLightsBlinking()   // caso luzes de perigo estejam desativadas, ativam
        }
    }

    // AS PROXIMAS TRES FUNCOES SAO RESPONSAVEIS POR COMECAR O CICLO DAS LUZES DOS PISCAS E LUZES DE PERIGO
    private fun startLeftSignalBlinking() {
        isLeftBlinking = true
        if (!isRightBlinking && !dangerLights) {   // CASO O PISCA DA DIREITA ESTEJA LIGADO, NAO PODE LIGAR O DA ESQUERDA
            blinkLeftSignal()
        }
    }

    private fun startRightSignalBlinking() {
        isRightBlinking = true
        if (!isLeftBlinking && !dangerLights) {  // CASO O PISCA DA ESQUERDA ESTEJA LIGADO, NAO PODE LIGAR O DA DIREITA
            blinkRightSignal()
        }
    }

    private fun startDangerLightsBlinking() {
        dangerLights = true
        isRightBlinking = false
        isLeftBlinking = false
        leftTurnSignal?.imageTintList =
            ContextCompat.getColorStateList(context, R.color.defaultLightGray)
        rightTurnSignal?.imageTintList =
            ContextCompat.getColorStateList(context, R.color.defaultLightGray)
        blinkDangerLights()
    }


    // AS PROXIMAS TRES FUNCOES SAO RESPONSAVEIS POR TERMINAR O CICLO DAS LUZES DOS PISCAS E LUZES DE PERIGO
    private fun stopLeftSignalBlinking() {
        isLeftBlinking = false
        leftTurnSignal?.imageTintList =
            ContextCompat.getColorStateList(context, R.color.defaultLightGray)
    }

    private fun stopRightSignalBlinking() {
        isRightBlinking = false
        rightTurnSignal?.imageTintList =
            ContextCompat.getColorStateList(context, R.color.defaultLightGray)
    }

    private fun stopDangerLightsBlinking() {
        dangerLights = false
        rightTurnSignal?.imageTintList =
            ContextCompat.getColorStateList(context, R.color.defaultLightGray)
        leftTurnSignal?.imageTintList = ContextCompat.getColorStateList(
            context,
            R.color.defaultLightGray
        )  // QUANDO O CICLO ACABA, OS SINALIZADORES DO PISCA NA UI VOLTAM A COR DEFAULT
    }

    // AS PROXIMAS TRES FUNCOES SAO RESPONSAVEIS POR FAZER PISCAR AS LUZES DOS PISCAS E LUZES DE PERIGO
    // PISCA ESQUERDO
    private fun blinkLeftSignal() {
        val handler = Handler(Looper.getMainLooper())    // inicializa o handler
        handler.post(object : Runnable {
            override fun run() {
                if (isLeftBlinking) {
                    leftTurnSignal?.apply {
                        imageTintList = if (imageTintList == ContextCompat.getColorStateList(
                                context,
                                R.color.defaultLightGray
                            )
                        ) {
                            ContextCompat.getColorStateList(
                                context,
                                R.color.green
                            )    // Turn signal "on" state (Green)
                        } else {
                            ContextCompat.getColorStateList(
                                context,
                                R.color.defaultLightGray
                            ) // Turn signal "off" state (grayDefault)
                        }
                    }
                    handler.postDelayed(
                        this,
                        blinkInterval
                    ) // atrasa o ciclo pelo intervalo do pisca definido acima
                }
            }
        })
    }

    // PISCA DIREITO IGUAL AO PISCA ESQUERDO ACIMA
    private fun blinkRightSignal() {
        val handler = Handler(Looper.getMainLooper())    // inicializa o handler
        handler.post(object : Runnable {
            override fun run() {
                if (isRightBlinking) {
                    rightTurnSignal?.apply {
                        imageTintList = if (imageTintList == ContextCompat.getColorStateList(
                                context,
                                R.color.defaultLightGray
                            )
                        ) {
                            ContextCompat.getColorStateList(
                                context,
                                R.color.green
                            )    // Turn signal "on" state (lightBlue)
                        } else {
                            ContextCompat.getColorStateList(
                                context,
                                R.color.defaultLightGray
                            ) // Turn signal "off" state (grayDefault)
                        }
                    }
                    handler.postDelayed(this, blinkInterval)
                }
            }
        })
    }

    // LUZES DE PERIGO
    private fun blinkDangerLights() {
        val handler = Handler(Looper.getMainLooper())    // inicializa o handler
        handler.post(object : Runnable {
            override fun run() {
                if (dangerLights) {
                    leftTurnSignal?.apply {
                        imageTintList = if (imageTintList == ContextCompat.getColorStateList(
                                context,
                                R.color.defaultLightGray
                            )
                        ) {
                            ContextCompat.getColorStateList(
                                context,
                                R.color.green
                            )    // Turn signal "on" state (lightBlue)
                        } else {
                            ContextCompat.getColorStateList(
                                context,
                                R.color.defaultLightGray
                            ) // Turn signal "off" state (grayDefault)
                        }
                    }
                    rightTurnSignal?.apply {
                        imageTintList = if (imageTintList == ContextCompat.getColorStateList(
                                context,
                                R.color.defaultLightGray
                            )
                        ) {
                            ContextCompat.getColorStateList(
                                context,
                                R.color.green
                            )    // Turn signal "on" state (lightBlue)
                        } else {
                            ContextCompat.getColorStateList(
                                context,
                                R.color.defaultLightGray
                            ) // Turn signal "off" state (grayDefault)
                        }
                    }
                    handler.postDelayed(this, blinkInterval)
                }
            }
        })
    }
}
