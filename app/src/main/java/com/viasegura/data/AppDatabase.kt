package com.viasegura.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Classe principal do banco de dados Room.
 * Utiliza o padrão Singleton para garantir uma única instância em toda a aplicação.
 */
@Database(entities = [ViagemEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun viagemDao(): ViagemDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Retorna a instância do banco de dados, criando-a se necessário.
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "viasegura_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
