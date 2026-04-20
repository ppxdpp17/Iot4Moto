package com.example.rideriot.fragments

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.rideriot.R
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import java.io.FileOutputStream
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import android.util.Log
import java.io.OutputStream

class CameraFragment : Fragment() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var btnCapture: FloatingActionButton

    // Novo link RTSP para o MediaMTX
    private val STREAM_URL = "rtsp://172.20.0.154:8554/moto"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_camera, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playerView = view.findViewById(R.id.video_view)
        btnCapture = view.findViewById(R.id.btnCapture)

        btnCapture.setOnClickListener {
            captureAndSaveImage()
        }
    }

    // Liga o vídeo apenas quando o ecrã está visível
    override fun onStart() {
        super.onStart()
        player = ExoPlayer.Builder(requireContext()).build()
        playerView.player = player

        // 1. O ESPIÃO: Se a tela ficar preta, o erro real vai aparecer no Logcat do Android Studio
        player?.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e("VIDEO_MOTO", "O Leitor crashou: ${error.message}")
                error.printStackTrace()
            }
        })

        // 2. FORÇAR RTSP: Criar a fonte com timeout estendido para aguentar o arranque da câmara
        val mediaItem = MediaItem.fromUri(STREAM_URL)
        val mediaSource = RtspMediaSource.Factory()
            .setTimeoutMs(10000) // Espera 10 segundos antes de desistir
            .createMediaSource(mediaItem)

        player?.setMediaSource(mediaSource)
        player?.prepare()
        player?.playWhenReady = true
    }

    // Corta o vídeo e a câmara física quando sais do ecrã
    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
    }

    private fun captureAndSaveImage() {
        // Extrai o frame atual diretamente da memória da gráfica (TextureView)
        val textureView = playerView.videoSurfaceView as? TextureView
        val bitmap = textureView?.bitmap

        if (bitmap == null) {
            Toast.makeText(requireContext(), "Erro: frame indisponível.", Toast.LENGTH_SHORT).show()
            return
        }

        val filename = "RiderIoT_${System.currentTimeMillis()}.jpg"
        var fos: OutputStream? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = requireContext().contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/RiderIoT")
                }
                val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                fos = imageUri?.let { resolver.openOutputStream(it) }
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val appDir = File(imagesDir, "RiderIoT")
                if (!appDir.exists()) appDir.mkdir()
                val image = File(appDir, filename)
                fos = FileOutputStream(image)
            }

            fos?.use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
                Toast.makeText(requireContext(), "Foto guardada com sucesso", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Erro ao guardar foto.", Toast.LENGTH_SHORT).show()
        }
    }
}