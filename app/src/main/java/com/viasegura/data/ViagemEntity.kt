package com.viasegura.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidade que representa uma viagem registrada no banco de dados Room.
 */
@Entity(tableName = "viagens")
data class ViagemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    // Data/hora de início da viagem (milissegundos)
    val inicio: Long,

    // Data/hora de fim da viagem (milissegundos)
    val fim: Long,

    // Velocidade máxima registrada durante a viagem (km/h)
    val velocidadeMaxima: Float,

    // Distância total percorrida durante a viagem (metros)
    val distanciaMetros: Float,

    // Limite de velocidade configurado pelo usuário nesta viagem (km/h)
    val limiteConfigurado: Int
)
