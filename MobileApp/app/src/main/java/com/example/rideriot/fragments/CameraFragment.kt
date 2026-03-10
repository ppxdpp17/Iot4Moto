package com.example.rideriot.fragments

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.rideriot.R
import com.example.rideriot.utils.MqttManager

class CameraFragment : Fragment() {

    private lateinit var ivCameraFeed: ImageView
    private lateinit var tvCameraStatus: TextView
    private lateinit var mqttManager: MqttManager

    private val BROKER_URL = "tcp://172.20.0.203:1883" //IP da CSVU
    private val CLIENT_ID = "AndroidApp_Camera_${System.currentTimeMillis()}"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_camera, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ivCameraFeed = view.findViewById(R.id.ivCameraFeed)
        tvCameraStatus = view.findViewById(R.id.tvCameraStatus)

        setupCameraReceiver()
    }

    private fun setupCameraReceiver() {
        mqttManager = MqttManager(requireContext())

        mqttManager.messageListener = { topic, payload ->
            if (topic == "moto/camera") {
                try {
                    //Esconde o texto de "a aguardar" quando chega a primeira frame
                    activity?.runOnUiThread {
                        if (tvCameraStatus.visibility == View.VISIBLE) {
                            tvCameraStatus.visibility = View.GONE
                        }
                    }

                    //Converte a string Base64 de volta para Imagem
                    val imageBytes = Base64.decode(payload, Base64.DEFAULT)
                    val decodedImage = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

                    //Cola a imagem no ecrã (tem de ser na UI Thread)
                    activity?.runOnUiThread {
                        ivCameraFeed.setImageBitmap(decodedImage)
                    }
                } catch (e: IllegalArgumentException) {
                    //Se o C# mandar lixo que não é Base64, ignoramos para não crachar a app
                    e.printStackTrace()
                }
            }
        }

        mqttManager.connect(BROKER_URL, CLIENT_ID) {
            mqttManager.subscribe("moto/camera")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        //Limpa a ligação quando sai deste ecrã
        mqttManager.disconnect()
    }
}