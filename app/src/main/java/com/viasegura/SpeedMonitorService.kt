package com.viasegura

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.viasegura.data.AppDatabase
import com.viasegura.data.ViagemEntity
import com.viasegura.utils.SpeedAlertManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Serviço em foreground responsável por monitorar a velocidade via GPS continuamente,
 * mesmo com o aplicativo em background.
 */
class SpeedMonitorService : Service(), LocationListener {

    // Binder para comunicação com a Activity
    private val binder = LocalBinder()

    // Gerenciador de localização do sistema
    private lateinit var locationManager: LocationManager

    // Gerenciador de alertas sonoros/vibração
    private lateinit var alertManager: SpeedAlertManager

    // Banco de dados Room
    private lateinit var database: AppDatabase

    // --- Estado da viagem atual ---
    var velocidadeAtual: Float = 0f        // km/h
    var velocidadeMaxima: Float = 0f       // km/h nesta viagem
    var distanciaTotal: Float = 0f         // metros
    var limiteVelocidade: Int = 60         // km/h configurado pelo usuário
    var sinalGpsForte: Boolean = false

    private var ultimaLocalizacao: Location? = null
    private var inicioViagem: Long = 0L
    private var monitorando: Boolean = false

    // Callback para atualizar a UI na Activity
    var onVelocidadeAtualizada: ((Float, Boolean) -> Unit)? = null

    // Identificador do canal de notificação
    private val CHANNEL_ID = "viasegura_channel"
    private val NOTIFICATION_ID = 1

    inner class LocalBinder : Binder() {
        fun getService(): SpeedMonitorService = this@SpeedMonitorService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        alertManager = SpeedAlertManager(this)
        database = AppDatabase.getInstance(this)
        criarCanalNotificacao()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, construirNotificacao("Monitorando velocidade..."))
        iniciarMonitoramento()
        return START_STICKY // Reinicia o serviço automaticamente se for encerrado pelo sistema
    }

    /**
     * Inicia o monitoramento GPS com atualização a cada 1 segundo ou 5 metros.
     */
    fun iniciarMonitoramento() {
        if (monitorando) return
        monitorando = true
        inicioViagem = System.currentTimeMillis()
        velocidadeMaxima = 0f
        distanciaTotal = 0f
        ultimaLocalizacao = null

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            // Atualiza a cada 1000ms (1 segundo) ou 5 metros
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 1000L, 5f, this
            )
        }
    }

    /**
     * Para o monitoramento GPS e salva a viagem no banco de dados.
     */
    fun pararMonitoramento() {
        if (!monitorando) return
        monitorando = false
        locationManager.removeUpdates(this)
        alertManager.cancelarAlerta()

        // Salva a viagem somente se durou mais de 5 segundos
        val duracao = System.currentTimeMillis() - inicioViagem
        if (duracao > 5000 && velocidadeMaxima > 0) {
            salvarViagem()
        }
    }

    /**
     * Salva os dados da viagem atual no banco Room usando Coroutine.
     */
    private fun salvarViagem() {
        CoroutineScope(Dispatchers.IO).launch {
            database.viagemDao().inserirViagem(
                ViagemEntity(
                    inicio = inicioViagem,
                    fim = System.currentTimeMillis(),
                    velocidadeMaxima = velocidadeMaxima,
                    distanciaMetros = distanciaTotal,
                    limiteConfigurado = limiteVelocidade
                )
            )
        }
    }

    // --- Callbacks do LocationListener ---

    override fun onLocationChanged(location: Location) {
        // Verifica qualidade do sinal GPS (precisão < 20m = forte)
        sinalGpsForte = location.accuracy < 20f

        // Converte velocidade de m/s para km/h
        velocidadeAtual = if (location.hasSpeed()) location.speed * 3.6f else 0f

        // Atualiza velocidade máxima da viagem
        if (velocidadeAtual > velocidadeMaxima) {
            velocidadeMaxima = velocidadeAtual
        }

        // Calcula distância percorrida
        ultimaLocalizacao?.let { anterior ->
            distanciaTotal += anterior.distanceTo(location)
        }
        ultimaLocalizacao = location

        // Verifica se excedeu o limite e emite/cancela alerta
        val excedeuLimite = velocidadeAtual > limiteVelocidade
        if (excedeuLimite) {
            alertManager.emitirAlerta()
        } else {
            alertManager.cancelarAlerta()
        }

        // Notifica a Activity via callback para atualizar a UI
        onVelocidadeAtualizada?.invoke(velocidadeAtual, excedeuLimite)

        // Atualiza texto da notificação persistente
        atualizarNotificacao("${velocidadeAtual.toInt()} km/h | Limite: $limiteVelocidade km/h")
    }

    override fun onProviderEnabled(provider: String) {
        sinalGpsForte = true
    }

    override fun onProviderDisabled(provider: String) {
        sinalGpsForte = false
    }

    // Mantido para compatibilidade com API < 29
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    // --- Notificação Foreground ---

    /**
     * Cria o canal de notificação (obrigatório para Android 8+).
     */
    private fun criarCanalNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                CHANNEL_ID,
                "ViaSegura - Monitoramento",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificação de monitoramento de velocidade"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(canal)
        }
    }

    private fun construirNotificacao(texto: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ViaSegura")
            .setContentText(texto)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Notificação persistente (não pode ser removida pelo usuário)
            .build()
    }

    private fun atualizarNotificacao(texto: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, construirNotificacao(texto))
    }

    override fun onDestroy() {
        super.onDestroy()
        pararMonitoramento()
        alertManager.release()
    }
}
