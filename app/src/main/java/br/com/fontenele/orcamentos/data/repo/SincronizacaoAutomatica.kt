package br.com.fontenele.orcamentos.data.repo

import androidx.room.InvalidationTracker
import br.com.fontenele.orcamentos.data.local.AppDatabase
import br.com.fontenele.orcamentos.data.prefs.Preferencias
import br.com.fontenele.orcamentos.domain.Resultado
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Situação da ligação com o servidor, mostrada no login e na tela inicial. */
sealed interface EstadoSync {
    /** Ainda não conseguiu sincronizar nenhuma vez neste aparelho. */
    data object Nunca : EstadoSync

    data class Conectado(val quando: Long) : EstadoSync

    data class SemConexao(val mensagem: String) : EstadoSync

    /** O servidor está no ar, mas este celular precisa entrar com e-mail e senha para sincronizar. */
    data object PrecisaEntrar : EstadoSync
}

/**
 * Mantém o banco interno de cada celular igual ao banco do servidor (o banco unificado):
 *  - sincroniza ao abrir o app e a cada [INTERVALO_MS] enquanto ele está aberto;
 *  - qualquer gravação no banco interno (orçamento, contrato, cliente, usuário...) agenda um envio;
 *  - uma sincronização por vez (as chamadas seguintes esperam a anterior terminar).
 */
class SincronizacaoAutomatica(
    private val sync: SyncRepository,
    private val auth: AuthRepository,
    prefs: Preferencias,
    db: AppDatabase,
) {
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val umaPorVez = Mutex()
    private var agendada: Job? = null
    private var ciclo: Job? = null

    private val _estado = MutableStateFlow<EstadoSync>(
        if (prefs.ultimaSincronizacao > 0) EstadoSync.Conectado(prefs.ultimaSincronizacao) else EstadoSync.Nunca
    )
    val estado: StateFlow<EstadoSync> = _estado.asStateFlow()

    init {
        // Room avisa quando alguma tabela muda; aí o envio é agendado (com uma pequena espera para juntar várias gravações)
        val observador = object : InvalidationTracker.Observer(TABELAS) {
            override fun onInvalidated(tables: Set<String>) {
                agendar()
            }
        }
        escopo.launch { db.invalidationTracker.addObserver(observador) }
    }

    /** Sincroniza agora e devolve uma mensagem para mostrar ao usuário. */
    suspend fun sincronizarAgora(): Resultado<String> = umaPorVez.withLock {
        when (val r = sync.sincronizarContando()) {
            is Resultado.Ok -> {
                _estado.value = EstadoSync.Conectado(System.currentTimeMillis())
                val (enviados, atualizados) = r.valor
                if (atualizados > 0) auth.recarregarUsuarioLogado()
                Resultado.Ok("Sincronizado: $enviados registros enviados e $atualizados atualizados neste aparelho.")
            }
            is Resultado.Erro -> {
                _estado.value = if (sync.precisaEntrar) EstadoSync.PrecisaEntrar else EstadoSync.SemConexao(r.mensagem)
                r
            }
        }
    }

    // ------------------------------------------------------------------ Contas no servidor

    /** Cria a conta no servidor (que decide o perfil) e já sincroniza. */
    suspend fun cadastrarNoServidor(nome: String, email: String, senha: String): SyncRepository.RespostaConta =
        sync.cadastrarNoServidor(nome, email, senha).also { if (it is SyncRepository.RespostaConta.Ok) agendar(0) }

    /** Entra no servidor com e-mail e senha (pega o token deste celular) e traz os dados de todos. */
    suspend fun entrarNoServidor(email: String, senha: String): SyncRepository.RespostaConta {
        val r = sync.entrarNoServidor(email, senha)
        if (r is SyncRepository.RespostaConta.Ok) sincronizarAgora()
        return r
    }

    /**
     * Depois de entrar com a senha guardada no celular, renova o token no servidor em segundo plano
     * (não atrasa a entrada no app). Roda no escopo próprio, que não é cancelado ao trocar de tela.
     */
    fun renovarLoginNoServidor(email: String, senha: String) {
        escopo.launch {
            if (sync.entrarNoServidor(email, senha) is SyncRepository.RespostaConta.Ok) sincronizarAgora()
        }
    }

    /** Quantas contas o servidor tem (null = sem conexão). Usado na tela de cadastro. */
    suspend fun contasNoServidor(): Long? = sync.contasNoServidor()

    /** Ao sair da conta: invalida o token deste celular no servidor. */
    fun sairDoServidor() {
        val token = sync.esquecerToken() ?: return
        escopo.launch { sync.avisarSaida(token) }
    }

    /** Agenda uma sincronização em segundo plano (várias chamadas seguidas viram uma só). */
    fun agendar(esperaMs: Long = 2_000) {
        agendada?.cancel()
        agendada = escopo.launch {
            delay(esperaMs)
            sincronizarAgora()
        }
    }

    /** Chamado quando o app aparece na tela: sincroniza já e depois de tempos em tempos. */
    fun iniciar() {
        if (ciclo?.isActive == true) return
        ciclo = escopo.launch {
            while (isActive) {
                sincronizarAgora()
                delay(INTERVALO_MS)
            }
        }
    }

    /** Chamado quando o app sai da tela, para não gastar bateria nem internet em segundo plano. */
    fun parar() {
        ciclo?.cancel()
        ciclo = null
    }

    companion object {
        const val INTERVALO_MS = 30_000L
        private val TABELAS = arrayOf(
            "usuarios", "clientes", "produtos", "orcamentos", "contratos", "historicos", "email_logs", "parametros",
        )
    }
}
