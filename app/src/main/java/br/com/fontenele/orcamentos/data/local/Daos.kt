package br.com.fontenele.orcamentos.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UsuarioDao {
    @Query("SELECT * FROM usuarios WHERE lower(email) = lower(:email) LIMIT 1")
    suspend fun porEmail(email: String): UsuarioEntity?

    @Query("SELECT * FROM usuarios WHERE id = :id")
    suspend fun porId(id: String): UsuarioEntity?

    @Query("SELECT COUNT(*) FROM usuarios")
    suspend fun contar(): Int

    @Query("SELECT COUNT(*) FROM usuarios WHERE perfil = 'ADMIN' AND ativo = 1")
    suspend fun contarAdminsAtivos(): Int

    @Query("SELECT * FROM usuarios ORDER BY nome COLLATE NOCASE")
    fun observarTodos(): Flow<List<UsuarioEntity>>

    @Query("SELECT * FROM usuarios")
    suspend fun todos(): List<UsuarioEntity>

    @Upsert
    suspend fun salvar(usuario: UsuarioEntity)

    /** Remove contas repetidas com o mesmo e-mail (a do servidor vence). Devolve quantas apagou. */
    @Query("DELETE FROM usuarios WHERE lower(email) = lower(:email) AND id <> :id")
    suspend fun apagarOutrosComEmail(email: String, id: String): Int
}

@Dao
interface ClienteDao {
    @Query("SELECT * FROM clientes ORDER BY nome COLLATE NOCASE")
    fun observarTodos(): Flow<List<ClienteEntity>>

    @Query("SELECT * FROM clientes WHERE id = :id")
    fun observarPorId(id: String): Flow<ClienteEntity?>

    @Query("SELECT * FROM clientes WHERE id = :id")
    suspend fun porId(id: String): ClienteEntity?

    @Query("SELECT * FROM clientes WHERE lower(email) = lower(:email) LIMIT 1")
    suspend fun porEmail(email: String): ClienteEntity?

    @Query("SELECT COUNT(*) FROM clientes")
    fun observarQuantidade(): Flow<Int>

    @Query("SELECT * FROM clientes")
    suspend fun todos(): List<ClienteEntity>

    @Upsert
    suspend fun salvar(cliente: ClienteEntity)
}

@Dao
interface ProdutoDao {
    @Query("SELECT * FROM produtos ORDER BY nome COLLATE NOCASE")
    fun observarTodos(): Flow<List<ProdutoEntity>>

    @Query("SELECT * FROM produtos WHERE ativo = 1 ORDER BY nome COLLATE NOCASE")
    fun observarAtivos(): Flow<List<ProdutoEntity>>

    @Query("SELECT * FROM produtos WHERE ativo = 1")
    suspend fun ativos(): List<ProdutoEntity>

    @Query("SELECT * FROM produtos WHERE id = :id")
    suspend fun porId(id: String): ProdutoEntity?

    @Query("SELECT * FROM produtos")
    suspend fun todos(): List<ProdutoEntity>

    @Upsert
    suspend fun salvar(produto: ProdutoEntity)
}

@Dao
interface OrcamentoDao {
    @Query("SELECT * FROM orcamentos ORDER BY criadoEm DESC")
    fun observarTodos(): Flow<List<OrcamentoEntity>>

    @Query("SELECT * FROM orcamentos WHERE id = :id")
    suspend fun porId(id: String): OrcamentoEntity?

    @Query("SELECT COALESCE(MAX(numero), 0) + 1 FROM orcamentos")
    suspend fun proximoNumero(): Int

    @Query("SELECT COUNT(*) FROM orcamentos")
    fun observarQuantidade(): Flow<Int>

    @Query("SELECT * FROM orcamentos")
    suspend fun todos(): List<OrcamentoEntity>

    @Upsert
    suspend fun salvar(orcamento: OrcamentoEntity)
}

@Dao
interface ContratoDao {
    @Query("SELECT * FROM contratos ORDER BY criadoEm DESC")
    fun observarTodos(): Flow<List<ContratoEntity>>

    @Query("SELECT * FROM contratos WHERE status = 'ABERTO' ORDER BY criadoEm DESC")
    fun observarAbertos(): Flow<List<ContratoEntity>>

    @Query("SELECT * FROM contratos WHERE id = :id")
    suspend fun porId(id: String): ContratoEntity?

    /** Pedidos (contratos) de um cliente, do mais novo para o mais antigo. */
    @Query("SELECT * FROM contratos WHERE clienteId = :clienteId ORDER BY criadoEm DESC")
    fun observarPorCliente(clienteId: String): Flow<List<ContratoEntity>>

    @Query("SELECT * FROM contratos WHERE clienteId = :clienteId AND status = 'ABERTO' ORDER BY criadoEm DESC LIMIT 1")
    suspend fun ultimoAbertoDoCliente(clienteId: String): ContratoEntity?

    @Query("SELECT COALESCE(MAX(numero), 0) + 1 FROM contratos")
    suspend fun proximoNumero(): Int

    @Query("SELECT COUNT(*) FROM contratos WHERE status = 'FECHADO'")
    fun observarQuantidadeFechados(): Flow<Int>

    @Query("SELECT * FROM contratos")
    suspend fun todos(): List<ContratoEntity>

    @Upsert
    suspend fun salvar(contrato: ContratoEntity)
}

@Dao
interface HistoricoDao {
    @Query("SELECT * FROM historicos WHERE clienteId = :clienteId ORDER BY data DESC")
    fun observarPorCliente(clienteId: String): Flow<List<HistoricoEntity>>

    @Query("SELECT * FROM historicos WHERE id = :id")
    suspend fun porId(id: String): HistoricoEntity?

    @Query("SELECT * FROM historicos")
    suspend fun todos(): List<HistoricoEntity>

    @Upsert
    suspend fun salvar(historico: HistoricoEntity)
}

@Dao
interface EmailLogDao {
    @Query("SELECT * FROM email_logs ORDER BY data DESC LIMIT 50")
    fun observarRecentes(): Flow<List<EmailLogEntity>>

    @Query("SELECT * FROM email_logs WHERE id = :id")
    suspend fun porId(id: String): EmailLogEntity?

    @Query("SELECT * FROM email_logs")
    suspend fun todos(): List<EmailLogEntity>

    @Upsert
    suspend fun salvar(log: EmailLogEntity)
}

@Dao
interface ParametroDao {
    @Query("SELECT * FROM parametros")
    fun observarTodos(): Flow<List<ParametroEntity>>

    @Query("SELECT * FROM parametros")
    suspend fun todos(): List<ParametroEntity>

    @Query("SELECT * FROM parametros WHERE id = :id")
    suspend fun porId(id: String): ParametroEntity?

    @Upsert
    suspend fun salvar(parametro: ParametroEntity)
}
