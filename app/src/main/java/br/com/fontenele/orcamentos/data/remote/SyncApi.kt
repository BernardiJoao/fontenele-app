package br.com.fontenele.orcamentos.data.remote

import br.com.fontenele.orcamentos.data.local.ClienteEntity
import br.com.fontenele.orcamentos.data.local.ContratoEntity
import br.com.fontenele.orcamentos.data.local.EmailLogEntity
import br.com.fontenele.orcamentos.data.local.HistoricoEntity
import br.com.fontenele.orcamentos.data.local.OrcamentoEntity
import br.com.fontenele.orcamentos.data.local.ParametroEntity
import br.com.fontenele.orcamentos.data.local.ProdutoEntity
import br.com.fontenele.orcamentos.data.local.UsuarioEntity
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/** Pacote trocado com o backend. Os nomes batem com com.APSproject.demo.sync.PacoteSync (Java). */
data class PacoteSync(
    val usuarios: List<UsuarioEntity>? = null,
    val clientes: List<ClienteEntity>? = null,
    val produtos: List<ProdutoEntity>? = null,
    val orcamentos: List<OrcamentoEntity>? = null,
    val contratos: List<ContratoEntity>? = null,
    val historicos: List<HistoricoEntity>? = null,
    val emailLogs: List<EmailLogEntity>? = null,
    val parametros: List<ParametroEntity>? = null,
) {
    fun total(): Int = listOfNotNull(usuarios, clientes, produtos, orcamentos, contratos, historicos, emailLogs, parametros)
        .sumOf { it.size }
}

data class StatusServidor(
    val status: String? = null,
    val registros: Long? = null,
    /** Quantas contas o servidor tem (0 = a próxima conta criada vira Administrador). */
    val contas: Long? = null,
)

/** Corpo de /api/entrar e /api/cadastro. */
data class Credenciais(
    val email: String,
    val senha: String,
    val nome: String? = null,
)

/** Resposta do login no servidor: o token deste celular e a conta (com hash e salt, para entrar sem internet). */
data class SessaoServidor(
    val token: String? = null,
    val usuario: UsuarioEntity? = null,
)

/** Endpoints adicionados ao backend Spring Boot do sistema web (pasta backend/ do projeto). */
interface SyncApi {
    @GET("api/status")
    suspend fun status(): StatusServidor

    @POST("api/sync")
    suspend fun sincronizar(@Body pacote: PacoteSync): PacoteSync

    @POST("api/entrar")
    suspend fun entrar(@Body credenciais: Credenciais): SessaoServidor

    @POST("api/cadastro")
    suspend fun cadastrar(@Body credenciais: Credenciais): SessaoServidor

    @POST("api/sair")
    suspend fun sair(@Header("Authorization") autorizacao: String): Response<Unit>
}
