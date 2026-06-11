package com.winghaeger.app.splash

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.winghaeger.app.databinding.ActivitySplashBinding
import com.winghaeger.app.main.MainActivity
import com.winghaeger.app.ui.setContentWithWingInsets

class SplashActivity : AppCompatActivity() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val goMain = Runnable {
        if (isFinishing) return@Runnable
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentWithWingInsets(binding.root)
        mainHandler.postDelayed(goMain, 1800L)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(goMain)
        super.onDestroy()
    }
}
