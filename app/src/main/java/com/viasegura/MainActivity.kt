package com.viasegura

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Tela principal do aplicativo ViaSegura.
 * Exibe o velocímetro em tempo real, indicador de sinal GPS,
 * seletor de limite de velocidade e botão de monitoramento.
 */
class MainActivity : AppCompatActivity() {

    // Referências aos elementos de UI
    private lateinit var tvVelocidade: TextView        // Velocidade atual em km/h
    private lateinit var tvUnidade: TextView           // Rótulo "km/h"
    private lateinit var tvStatus: TextView            // Status: "Monitorando..." / "Parado"
    private lateinit var tvSinalGps: TextView          // Ícone/texto de qualidade do GPS
    private lateinit var tvVelMaxima: TextView         // Velocidade máxima da viagem atual
    private lateinit var spinnerLimite: Spinner        // Seletor do limite de velocidade
    private lateinit var btnMonitorar: Button          // Botão Iniciar/Parar
    private lateinit var btnHistorico: Button          // Botão para abrir histórico
    private lateinit var viewIndicador: View           // Painel colorido de alerta

    // Conexão com o SpeedMonitorService
    private var speedService: SpeedMonitorService? = null
    private var servicoBound = false
    private var monitorando = false

    private val PERMISSAO_LOCATION = 100

    // Limites de velocidade disponíveis no spinner (km/h)
    private val limitesDisponiveis = arrayOf(30, 40, 50, 60, 80, 100, 110, 120)

    /**
     * Callback de conexão com o serviço SpeedMonitorService.
     */
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SpeedMonitorService.LocalBinder
            speedService = binder.getService()
            servicoBound = true

            // Registra callback para receber atualizações de velocidade
            speedService?.onVelocidadeAtualizada = { velocidade, excedeu ->
                runOnUiThread {
                    atualizarUI(velocidade, excedeu)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            servicoBound = false
            speedService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        inicializarViews()
        configurarSpinner()
        configurarBotoes()
        verificarPermissoes()
    }

    /**
     * Inicializa todas as referências de views a partir do layout.
     */
    private fun inicializarViews() {
        tvVelocidade = findViewById(R.id.tv_velocidade)
        tvUnidade = findViewById(R.id.tv_unidade)
        tvStatus = findViewById(R.id.tv_status)
        tvSinalGps = findViewById(R.id.tv_sinal_gps)
        tvVelMaxima = findViewById(R.id.tv_vel_maxima)
        spinnerLimite = findViewById(R.id.spinner_limite)
        btnMonitorar = findViewById(R.id.btn_monitorar)
        btnHistorico = findViewById(R.id.btn_historico)
        viewIndicador = findViewById(R.id.view_indicador)
    }

    /**
     * Configura o Spinner com os limites de velocidade disponíveis.
     */
    private fun configurarSpinner() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            limitesDisponiveis.map { "$it km/h" }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLimite.adapter = adapter
        spinnerLimite.setSelection(3) // Padrão: 60 km/h

        spinnerLimite.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                // Atualiza o limite no serviço quando o usuário muda a seleção
                speedService?.limiteVelocidade = limitesDisponiveis[pos]
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    /**
     * Configura os listeners dos botões.
     */
    private fun configurarBotoes() {
        btnMonitorar.setOnClickListener {
            if (!monitorando) {
                iniciarMonitoramento()
            } else {
                pararMonitoramento()
            }
        }

        btnHistorico.setOnClickListener {
            startActivity(Intent(this, HistoricoActivity::class.java))
        }
    }

    /**
     * Inicia o SpeedMonitorService e atualiza o estado da UI.
     */
    private fun iniciarMonitoramento() {
        monitorando = true
        btnMonitorar.text = "Parar"
        btnMonitorar.backgroundTintList =
            ContextCompat.getColorStateList(this, android.R.color.holo_red_dark)
        tvStatus.text = "Monitorando..."

        val intent = Intent(this, SpeedMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    /**
     * Para o monitoramento e restaura o estado inicial da UI.
     */
    private fun pararMonitoramento() {
        monitorando = false
        btnMonitorar.text = "Iniciar"
        btnMonitorar.backgroundTintList =
            ContextCompat.getColorStateList(this, android.R.color.holo_green_dark)
        tvStatus.text = "Parado"
        tvVelocidade.text = "0"
        viewIndicador.setBackgroundColor(Color.parseColor("#4CAF50")) // Verde

        speedService?.pararMonitoramento()
        if (servicoBound) {
            unbindService(serviceConnection)
            servicoBound = false
        }
        stopService(Intent(this, SpeedMonitorService::class.java))
    }

    /**
     * Atualiza todos os elementos visuais com base na velocidade atual.
     * @param velocidade Velocidade em km/h
     * @param excedeu Se o limite de velocidade foi excedido
     */
    private fun atualizarUI(velocidade: Float, excedeu: Boolean) {
        tvVelocidade.text = velocidade.toInt().toString()

        // Atualiza velocidade máxima
        val maxima = speedService?.velocidadeMaxima ?: 0f
        tvVelMaxima.text = "Máx: ${maxima.toInt()} km/h"

        // Atualiza sinal GPS
        val sinalForte = speedService?.sinalGpsForte ?: false
        tvSinalGps.text = if (sinalForte) "GPS Forte" else "GPS Fraco"
        tvSinalGps.setTextColor(
            if (sinalForte) Color.parseColor("#4CAF50") else Color.parseColor("#FF9800")
        )

        // Muda cor do painel: vermelho se excedeu, verde se normal
        if (excedeu) {
            viewIndicador.setBackgroundColor(Color.parseColor("#F44336")) // Vermelho
            tvVelocidade.setTextColor(Color.parseColor("#F44336"))
        } else {
            viewIndicador.setBackgroundColor(Color.parseColor("#4CAF50")) // Verde
            tvVelocidade.setTextColor(Color.parseColor("#4CAF50"))
        }
    }

    /**
     * Solicita permissão de localização precisa se ainda não foi concedida.
     */
    private fun verificarPermissoes() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                PERMISSAO_LOCATION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSAO_LOCATION && grantResults.isNotEmpty() &&
            grantResults[0] != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(
                this,
                "Permissão de localização necessária para o funcionamento do app",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (servicoBound) {
            unbindService(serviceConnection)
        }
    }
}
