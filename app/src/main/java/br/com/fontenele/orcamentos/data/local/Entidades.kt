package br.com.fontenele.orcamentos.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/*
 * Tabelas do banco interno (Room/SQLite, armazenamento interno do app).
 * Os ids são UUID e todas as tabelas têm "atualizadoEm" para a sincronização com o servidor
 * (armazenamento externo) usar a regra "a alteração mais recente vence".
 * Estas mesmas classes são enviadas em JSON para o backend, por isso os nomes dos campos
 * são iguais aos das entidades Java do servidor.
 */

private fun novoId() = UUID.randomUUID().toString()
private fun agora() = System.currentTimeMillis()

@Entity(tableName = "usuarios", indices = [Index("email")])
data class UsuarioEntity(
    @PrimaryKey val id: String = novoId(),
    val nome: String,
    val email: String,
    val senhaHash: String,
    val salt: String,
    val perfil: String,
    val ativo: Boolean = true,
    val criadoEm: Long = agora(),
    val atualizadoEm: Long = agora(),
)

@Entity(tableName = "clientes", indices = [Index("email")])
data class ClienteEntity(
    @PrimaryKey val id: String = novoId(),
    val nome: String,
    val email: String,
    val cpf: String = "",
    val telefone: String = "",
    val cep: String = "",
    val estado: String = "",
    val cidade: String = "",
    val rua: String = "",
    val numero: String = "",
    val orcamentosPedidos: Int = 0,
    val contratosFechados: Int = 0,
    val criadoEm: Long = agora(),
    val atualizadoEm: Long = agora(),
)

@Entity(tableName = "produtos")
data class ProdutoEntity(
    @PrimaryKey val id: String = novoId(),
    val nome: String,
    val descricao: String,
    val preco: Double,
    val cobranca: String,
    val ativo: Boolean = true,
    val criadoEm: Long = agora(),
    val atualizadoEm: Long = agora(),
)

@Entity(tableName = "orcamentos", indices = [Index("clienteId")])
data class OrcamentoEntity(
    @PrimaryKey val id: String = novoId(),
    val numero: Int,
    val clienteId: String,
    val clienteNome: String,
    val clienteEmail: String,
    val impressao: String,
    val paginas: Int,
    val proporcao: String,
    /** Tiragens orçadas, separadas por vírgula (ex.: "100,200,300,500,1000,2000"). */
    val tiragens: String,
    val coeficienteInflacao: Double,
    val descontoAVistaPercentual: Double,
    val adicionaisIds: String,
    /** ResultadoOrcamento em JSON (linhas por tiragem + adicionais). */
    val itensJson: String,
    /** Total da menor tiragem orçada (usado nas listas). */
    val total: Double,
    val validadeDias: Int,
    val observacoes: String,
    val usuarioId: String,
    val usuarioNome: String,
    val criadoEm: Long = agora(),
    val atualizadoEm: Long = agora(),
)

@Entity(tableName = "contratos", indices = [Index("clienteId")])
data class ContratoEntity(
    @PrimaryKey val id: String = novoId(),
    val numero: Int,
    val clienteId: String,
    val orcamentoId: String? = null,
    val tiragem: Int? = null,
    val nome: String,
    val cpf: String,
    val email: String,
    val telefone: String,
    val cep: String,
    val estado: String,
    val cidade: String,
    val rua: String,
    val numeroEndereco: String,
    val valor: Double,
    val prazoEntregaDias: Int,
    val formaPagamento: String,
    val clausulasAdicionais: String,
    val status: String,
    val fechadoEm: Long? = null,
    /** Data em que o pedido ficou pronto (preenchida ao tirar a foto). */
    val prontoEm: Long? = null,
    /** Nome do arquivo da foto do pedido pronto, na pasta interna filesDir/fotos. */
    val fotoPronto: String? = null,
    val usuarioId: String,
    val usuarioNome: String,
    val criadoEm: Long = agora(),
    val atualizadoEm: Long = agora(),
)

@Entity(tableName = "historicos", indices = [Index("clienteId")])
data class HistoricoEntity(
    @PrimaryKey val id: String = novoId(),
    val clienteId: String,
    val tipo: String,
    val descricao: String,
    val valor: Double? = null,
    val referenciaId: String? = null,
    val usuarioNome: String,
    val data: Long = agora(),
    val atualizadoEm: Long = agora(),
)

@Entity(tableName = "email_logs")
data class EmailLogEntity(
    @PrimaryKey val id: String = novoId(),
    val documentoTipo: String,
    val documentoId: String,
    val numeroDocumento: Int,
    val destinatario: String,
    val sucesso: Boolean,
    val mensagem: String,
    val usuarioNome: String,
    val data: Long = agora(),
    val atualizadoEm: Long = agora(),
)

/** Parâmetros de cálculo ajustados pelo Administrador (fator de correção, formatos, desconto, colorido). */
@Entity(tableName = "parametros")
data class ParametroEntity(
    @PrimaryKey val id: String,
    val valor: Double,
    val atualizadoEm: Long = agora(),
)
