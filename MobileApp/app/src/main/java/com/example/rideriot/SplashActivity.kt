package com.example.rideriot

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.drawable.ClipDrawable
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val imgLogoFill = findViewById<ImageView>(R.id.img_logo_fill)
        val clipDrawable = imgLogoFill.drawable as ClipDrawable

        //Garante que começa vazio
        clipDrawable.level = 0

        //Anima de 0 a 10000 durante 1500ms (1.5s)
        val animator = ValueAnimator.ofInt(0, 10000)
        animator.duration = 1500
        animator.addUpdateListener { animation ->
            clipDrawable.level = animation.animatedValue as Int
        }

        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                //Quando acaba de encher, salta para a app e destrói a splash
                startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                finish()
            }
        })

        animator.start()
    }
}