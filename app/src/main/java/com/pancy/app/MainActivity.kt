package com.pancy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PancyTheme { PancyApp() } }
    }

    override fun onResume() {
        super.onResume()
        // ogni volta che l'app torna in primo piano guarda se nel calendario ci sono appuntamenti nuovi
        val m = Model.di(this)
        m.importaDalCalendario(silenzioso = true)
        m.sincronizzaDrive(silenzioso = true)
        // una volta per avvio: c'è su Drive un backup più recente (per esempio dopo una reinstallazione)?
        m.controllaBackup()
    }

    override fun onStop() {
        super.onStop()
        // l'app va in secondo piano: il backup in attesa parte subito
        Model.di(this).backupInSospeso()
    }
}
