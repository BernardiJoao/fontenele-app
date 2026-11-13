package br.com.fontenele.orcamentos

import android.app.Application
import android.content.Context
import br.com.fontenele.orcamentos.data.local.AppDatabase
import br.com.fontenele.orcamentos.data.prefs.Preferencias
import br.com.fontenele.orcamentos.data.repo.AuthRepository
import br.com.fontenele.orcamentos.data.repo.EditoraRepository
import br.com.fontenele.orcamentos.data.repo.SincronizacaoAutomatica
import br.com.fontenele.orcamentos.data.repo.SyncRepository
import br.com.fontenele.orcamentos.pdf.GeradorPdf
import com.google.gson.Gson

class FonteneleApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Injeção de dependências manual: um único lugar cria banco, repositórios e serviços. */
class AppContainer(context: Context) {
    private val gson = Gson()

    val prefs = Preferencias(context)
    val db = AppDatabase.criar(context)
    val auth = AuthRepository(db.usuarioDao(), prefs)
    val editora = EditoraRepository(db, gson) {
        // Tabela de valores da planilha da editora, empacotada no app (app/src/main/assets)
        context.assets.open("tabela_valores.csv").bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
    val sync = SyncRepository(db, prefs, gson)

    /** Mantém o banco deste celular igual ao do servidor (banco unificado entre todos os aparelhos). */
    val sincronizacao = SincronizacaoAutomatica(sync, auth, prefs, db)
    val pdf = GeradorPdf(context.applicationContext, editora)
}
