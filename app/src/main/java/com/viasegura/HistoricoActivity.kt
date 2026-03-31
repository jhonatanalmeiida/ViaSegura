package com.viasegura

import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.viasegura.data.AppDatabase
import com.viasegura.data.ViagemEntity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

/**
 * Activity que exibe o histórico de todas as viagens registradas no banco Room.
 */
class HistoricoActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvVazio: TextView
    private lateinit var adapter: ViagemAdapter
    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_historico)

        // Habilita botão "voltar" na barra de ação
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Histórico de Viagens"

        recyclerView = findViewById(R.id.recycler_viagens)
        tvVazio = findViewById(R.id.tv_historico_vazio)

        adapter = ViagemAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        database = AppDatabase.getInstance(this)

        // Observa as viagens do banco de dados em tempo real usando Flow
        lifecycleScope.launch {
            database.viagemDao().obterTodasViagens().collectLatest { viagens ->
                adapter.submeterLista(viagens)
                // Mostra mensagem quando não há viagens registradas
                if (viagens.isEmpty()) {
                    tvVazio.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    tvVazio.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}

/**
 * Adapter do RecyclerView para exibir a lista de viagens.
 */
class ViagemAdapter : RecyclerView.Adapter<ViagemAdapter.ViagemViewHolder>() {

    private var viagens: List<ViagemEntity> = emptyList()
    private val formatoData = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))

    fun submeterLista(novaLista: List<ViagemEntity>) {
        viagens = novaLista
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViagemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.two_line_list_item, parent, false)
        return ViagemViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViagemViewHolder, position: Int) {
        val viagem = viagens[position]
        val dataInicio = formatoData.format(Date(viagem.inicio))
        val duracaoMin = ((viagem.fim - viagem.inicio) / 60000).toInt()
        val distanciaKm = viagem.distanciaMetros / 1000f

        holder.linha1.text = "Viagem em $dataInicio"
        holder.linha2.text =
            "Vel. Máx: ${viagem.velocidadeMaxima.toInt()} km/h | " +
            "Distância: ${"%.1f".format(distanciaKm)} km | " +
            "Duração: $duracaoMin min"
    }

    override fun getItemCount() = viagens.size

    class ViagemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val linha1: TextView = view.findViewById(android.R.id.text1)
        val linha2: TextView = view.findViewById(android.R.id.text2)
    }
}
