package com.fetin.innav

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class DestinosActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Conecta esta classe ao XML que acabamos de criar
        setContentView(R.layout.activity_destinos)

        val btnDestinoLab = findViewById<Button>(R.id.btnDestinoLab)

        btnDestinoLab.setOnClickListener {
            // Como é um MVP, clicar aqui apenas confirma a escolha e volta para o Radar
            Toast.makeText(this, "Rota para o Laboratório selecionada!", Toast.LENGTH_LONG).show()

            // O comando finish() encerra esta tela e devolve o usuário para a MainActivity
            finish()
        }
    }
}