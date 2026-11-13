package br.com.fontenele.orcamentos.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Banco SQLite interno do aplicativo (fica em /data/data/<pacote>/databases/fontenele.db). */
@Database(
    entities = [
        UsuarioEntity::class,
        ClienteEntity::class,
        ProdutoEntity::class,
        OrcamentoEntity::class,
        ContratoEntity::class,
        HistoricoEntity::class,
        EmailLogEntity::class,
        ParametroEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun usuarioDao(): UsuarioDao
    abstract fun clienteDao(): ClienteDao
    abstract fun produtoDao(): ProdutoDao
    abstract fun orcamentoDao(): OrcamentoDao
    abstract fun contratoDao(): ContratoDao
    abstract fun historicoDao(): HistoricoDao
    abstract fun emailLogDao(): EmailLogDao
    abstract fun parametroDao(): ParametroDao

    companion object {
        /** Versão 3: data e foto do pedido pronto no contrato (mantém os dados já salvos). */
        private val MIGRACAO_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contratos ADD COLUMN prontoEm INTEGER")
                db.execSQL("ALTER TABLE contratos ADD COLUMN fotoPronto TEXT")
            }
        }

        fun criar(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "fontenele.db")
                .addMigrations(MIGRACAO_2_3)
                .fallbackToDestructiveMigration() // versões mais antigas: recria o banco
                .build()
    }
}
