package com.fetin.innav

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent

class DestinosActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Conecta esta classe ao XML que acabamos de criar
        setContentView(R.layout.activity_destinos)

        val btnDestinoLab = findViewById<Button>(R.id.btnDestinoLab)

        btnDestinoLab.setOnClickListener {
            // Em vez de finish(), iniciamos a NavegacaoActivity
            val intent = Intent(this, NavegacaoActivity::class.java)
            startActivity(intent)
            finish() // Fecha a tela de destino para ela não ficar no fundo
        }
    }
}