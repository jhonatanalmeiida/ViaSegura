package com.viasegura.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO (Data Access Object) para operações de banco de dados relacionadas a viagens.
 */
@Dao
interface ViagemDao {

    // Insere uma nova viagem no banco de dados
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirViagem(viagem: ViagemEntity): Long

    // Retorna todas as viagens ordenadas pela mais recente
    @Query("SELECT * FROM viagens ORDER BY inicio DESC")
    fun obterTodasViagens(): Flow<List<ViagemEntity>>

    // Retorna o total de viagens registradas
    @Query("SELECT COUNT(*) FROM viagens")
    suspend fun totalViagens(): Int

    // Remove todas as viagens (para fins de reset)
    @Query("DELETE FROM viagens")
    suspend fun limparHistorico()
}
