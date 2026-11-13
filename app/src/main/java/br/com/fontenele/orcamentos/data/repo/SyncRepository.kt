package br.com.fontenele.orcamentos.data.repo

import androidx.room.withTransaction
import br.com.fontenele.orcamentos.data.local.AppDatabase
import br.com.fontenele.orcamentos.data.prefs.Preferencias
import br.com.fontenele.orcamentos.data.local.UsuarioEntity
import br.com.fontenele.orcamentos.data.remote.Credenciais
import br.com.fontenele.orcamentos.data.remote.PacoteSync
import br.com.fontenele.orcamentos.data.remote.SessaoServidor
import br.com.fontenele.orcamentos.data.remote.SyncApi
import br.com.fontenele.orcamentos.domain.Resultado
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Armazenamento de dados externo: envia o banco interno para o servidor (PostgreSQL via Spring Boot)
 * e recebe de volta o que outros aparelhos/usuários cadastraram. Em cada registro vence a versão
 * com "atualizadoEm" mais recente.
 */
class SyncRepository(
    private val db: AppDatabase,
    private val prefs: Preferencias,
    private val gson: Gson,
) {
    /** true quando a última resposta do servidor foi "precisa entrar com e-mail e senha" (HTTP 401). */
    @Volatile
    var precisaEntrar: Boolean = false
        private set

    private fun criarApi(url: String): SyncApi {
        val http = OkHttpClient.Builder()
            // O servidor gratuito "dorme" sem uso e leva cerca de 1 minuto para acordar: espera mais
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            // Manda o token deste celular em todas as chamadas (o servidor recusa a sincronização sem ele)
            .addInterceptor { cadeia ->
                val token = prefs.tokenServidor
                val pedido = if (token.isNullOrBlank()) {
                    cadeia.request()
                } else {
                    cadeia.request().newBuilder().header("Authorization", "Bearer $token").build()
                }
                cadeia.proceed(pedido)
            }
            .build()
        return Retrofit.Builder()
            .baseUrl(normalizarUrl(url))
            .client(http)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(SyncApi::class.java)
    }

    /** Quantas contas o servidor tem (null = sem conexão). */
    suspend fun contasNoServidor(): Long? = withContext(Dispatchers.IO) {
        try {
            criarApi(prefs.urlServidor).status().contas
        } catch (e: Exception) {
            null
        }
    }

    /** Resposta das chamadas de conta: deu certo, o servidor recusou (com motivo) ou não houve conexão. */
    sealed interface RespostaConta {
        data class Ok(val usuario: UsuarioEntity) : RespostaConta
        data class Recusado(val mensagem: String) : RespostaConta
        data class SemConexao(val mensagem: String) : RespostaConta
    }

    /** Login no servidor: guarda o token deste celular e a conta no banco interno. */
    suspend fun entrarNoServidor(email: String, senha: String): RespostaConta =
        chamarConta { criarApi(prefs.urlServidor).entrar(Credenciais(email = email.trim(), senha = senha)) }

    /** Cria a conta no servidor (a primeira do sistema vira Administrador) e já guarda o token. */
    suspend fun cadastrarNoServidor(nome: String, email: String, senha: String): RespostaConta =
        chamarConta { criarApi(prefs.urlServidor).cadastrar(Credenciais(email = email.trim(), senha = senha, nome = nome.trim())) }

    private suspend fun chamarConta(chamada: suspend () -> SessaoServidor): RespostaConta = withContext(Dispatchers.IO) {
        try {
            val sessao = chamada()
            val u = sessao.usuario
            if (sessao.token.isNullOrBlank() || u == null) return@withContext RespostaConta.Recusado("Resposta inválida do servidor.")
            prefs.tokenServidor = sessao.token
            precisaEntrar = false
            db.usuarioDao().salvar(u)
            db.usuarioDao().apagarOutrosComEmail(u.email, u.id)
            RespostaConta.Ok(u)
        } catch (e: HttpException) {
            RespostaConta.Recusado(mensagemDoServidor(e) ?: mensagemDeErro(e))
        } catch (e: Exception) {
            RespostaConta.SemConexao(mensagemDeErro(e))
        }
    }

    /** Sair: esquece o token neste celular na hora e devolve o antigo (para avisar o servidor). */
    fun esquecerToken(): String? = prefs.tokenServidor.also { prefs.tokenServidor = null }

    /** Avisa o servidor que este token não vale mais (se não houver conexão, ele vence sozinho). */
    suspend fun avisarSaida(token: String) {
        withContext(Dispatchers.IO) { runCatching { criarApi(prefs.urlServidor).sair("Bearer $token") } }
    }

    suspend fun testarConexao(url: String): Resultado<String> = withContext(Dispatchers.IO) {
        try {
            val status = criarApi(url).status()
            Resultado.Ok("Conectado! O servidor tem ${status.registros ?: 0} registros do app.")
        } catch (e: Exception) {
            Resultado.Erro(mensagemDeErro(e))
        }
    }

    /**
     * Envia tudo o que está no banco interno e aplica o que veio do servidor.
     * Devolve quantos registros foram enviados e quantos mudaram neste aparelho.
     * Quem chama no dia a dia é a [SincronizacaoAutomatica] (uma sincronização por vez).
     */
    suspend fun sincronizarContando(): Resultado<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        try {
            val local = pacoteLocal(incluirUsuarios = true)
            val remoto = criarApi(prefs.urlServidor).sincronizar(local)
            val atualizados = aplicar(remoto)
            prefs.ultimaSincronizacao = System.currentTimeMillis()
            precisaEntrar = false
            Resultado.Ok(local.total() to atualizados)
        } catch (e: Exception) {
            precisaEntrar = e is HttpException && e.code() == 401
            Resultado.Erro(mensagemDeErro(e))
        }
    }

    suspend fun pacoteLocal(incluirUsuarios: Boolean): PacoteSync = PacoteSync(
        usuarios = if (incluirUsuarios) db.usuarioDao().todos() else null,
        clientes = db.clienteDao().todos(),
        produtos = db.produtoDao().todos(),
        orcamentos = db.orcamentoDao().todos(),
        contratos = db.contratoDao().todos(),
        historicos = db.historicoDao().todos(),
        emailLogs = db.emailLogDao().todos(),
        parametros = db.parametroDao().todos(),
    )

    /** JSON legível usado no backup exportado para a pasta Downloads (sem senhas). */
    suspend fun backupJson(): String =
        GsonBuilder().setPrettyPrinting().create().toJson(pacoteLocal(incluirUsuarios = false))

    private fun maisNovo(remoto: Long, local: Long?): Boolean = local == null || remoto > local

    private suspend fun aplicar(p: PacoteSync): Int = db.withTransaction {
        var n = 0
        p.usuarios.orEmpty().forEach { r ->
            if (maisNovo(r.atualizadoEm, db.usuarioDao().porId(r.id)?.atualizadoEm)) { db.usuarioDao().salvar(r); n++ }
            // Mesmo e-mail com outro id = conta criada em outro aparelho antes de sincronizar: vale a do servidor
            n += db.usuarioDao().apagarOutrosComEmail(r.email, r.id)
        }
        p.clientes.orEmpty().forEach { r ->
            if (maisNovo(r.atualizadoEm, db.clienteDao().porId(r.id)?.atualizadoEm)) { db.clienteDao().salvar(r); n++ }
        }
        p.produtos.orEmpty().forEach { r ->
            if (maisNovo(r.atualizadoEm, db.produtoDao().porId(r.id)?.atualizadoEm)) { db.produtoDao().salvar(r); n++ }
        }
        p.orcamentos.orEmpty().forEach { r ->
            if (maisNovo(r.atualizadoEm, db.orcamentoDao().porId(r.id)?.atualizadoEm)) { db.orcamentoDao().salvar(r); n++ }
        }
        p.contratos.orEmpty().forEach { r ->
            if (maisNovo(r.atualizadoEm, db.contratoDao().porId(r.id)?.atualizadoEm)) { db.contratoDao().salvar(r); n++ }
        }
        p.historicos.orEmpty().forEach { r ->
            if (maisNovo(r.atualizadoEm, db.historicoDao().porId(r.id)?.atualizadoEm)) { db.historicoDao().salvar(r); n++ }
        }
        p.emailLogs.orEmpty().forEach { r ->
            if (maisNovo(r.atualizadoEm, db.emailLogDao().porId(r.id)?.atualizadoEm)) { db.emailLogDao().salvar(r); n++ }
        }
        p.parametros.orEmpty().forEach { r ->
            if (maisNovo(r.atualizadoEm, db.parametroDao().porId(r.id)?.atualizadoEm)) { db.parametroDao().salvar(r); n++ }
        }
        n
    }

    /** Lê o {"mensagem": "..."} que o servidor manda junto com o erro. */
    private fun mensagemDoServidor(e: HttpException): String? = runCatching {
        e.response()?.errorBody()?.string()?.let { gson.fromJson(it, Map::class.java)["mensagem"] as? String }
    }.getOrNull()

    private fun mensagemDeErro(e: Exception): String = when (e) {
        is HttpException -> when (e.code()) {
            401 -> "Entre de novo com seu e-mail e senha para sincronizar."
            else -> mensagemDoServidor(e) ?: "O servidor respondeu com erro ${e.code()}."
        }
        is IOException -> "Sem conexão com o servidor (${prefs.urlServidor}). Confira o endereço e se o servidor está ligado."
        is IllegalArgumentException -> "Endereço do servidor inválido."
        else -> "Falha na sincronização: ${e.message ?: e.javaClass.simpleName}"
    }

    companion object {
        fun normalizarUrl(url: String): String {
            var u = url.trim()
            if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://$u"
            if (!u.endsWith("/")) u += "/"
            return u
        }
    }
}
