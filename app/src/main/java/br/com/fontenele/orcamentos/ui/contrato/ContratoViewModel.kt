package br.com.fontenele.orcamentos.ui.contrato

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.fontenele.orcamentos.data.local.ClienteEntity
import br.com.fontenele.orcamentos.data.local.ContratoEntity
import br.com.fontenele.orcamentos.data.local.OrcamentoEntity
import br.com.fontenele.orcamentos.data.local.UsuarioEntity
import br.com.fontenele.orcamentos.data.repo.DadosContrato
import br.com.fontenele.orcamentos.data.repo.EditoraRepository
import br.com.fontenele.orcamentos.domain.Formatos
import br.com.fontenele.orcamentos.domain.LinhaTiragem
import br.com.fontenele.orcamentos.domain.Resultado
import br.com.fontenele.orcamentos.domain.Validacao
import br.com.fontenele.orcamentos.pdf.GeradorPdf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Campos do contrato (os mesmos do "contrato.html" + customizações do UC12). */
data class FormContrato(
    val nome: String = "",
    val cpf: String = "",
    val email: String = "",
    val telefone: String = "",
    val cep: String = "",
    val estado: String = "",
    val cidade: String = "",
    val rua: String = "",
    val numero: String = "",
    val valor: String = "",
    val prazo: String = "30",
    val formaPagamento: String = "50% na assinatura do contrato e 50% na entrega dos exemplares",
    val clausulas: String = "",
    val orcamentoId: String? = null,
    val orcamentoNumero: Int? = null,
    val tiragem: Int? = null,
)

class ContratoViewModel(
    private val editora: EditoraRepository,
    private val pdf: GeradorPdf,
    private val contratoId: String?,
    orcamentoId: String?,
    clienteId: String?,
) : ViewModel() {

    var form by mutableStateOf(FormContrato())
        private set
    var erro by mutableStateOf<String?>(null)
        private set
    var processando by mutableStateOf(false)
        private set
    var numeroExistente by mutableStateOf<Int?>(null)
        private set

    /** Orçamento recém-escolhido cuja tiragem o usuário ainda precisa selecionar. */
    var orcamentoParaTiragem by mutableStateOf<OrcamentoEntity?>(null)
        private set

    // Aba "Fechamento" (UC18)
    var emailFechamento by mutableStateOf("")
    var erroFechamento by mutableStateOf<String?>(null)
        private set

    val orcamentos: StateFlow<List<OrcamentoEntity>> = editora.observarOrcamentos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val clientes: StateFlow<List<ClienteEntity>> = editora.observarClientes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val abertos: StateFlow<List<ContratoEntity>> = editora.observarContratosAbertos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            when {
                contratoId != null -> editora.contrato(contratoId)?.let { c ->
                    // UC16 – alterar as informações de um contrato já gerado
                    numeroExistente = c.numero
                    val orc = c.orcamentoId?.let { editora.orcamento(it) }
                    form = FormContrato(
                        nome = c.nome,
                        cpf = Validacao.formatarCpf(c.cpf),
                        email = c.email,
                        telefone = Validacao.formatarTelefone(c.telefone),
                        cep = Validacao.formatarCep(c.cep),
                        estado = c.estado,
                        cidade = c.cidade,
                        rua = c.rua,
                        numero = c.numeroEndereco,
                        valor = Formatos.paraCampo(c.valor),
                        prazo = c.prazoEntregaDias.toString(),
                        formaPagamento = c.formaPagamento,
                        clausulas = c.clausulasAdicionais,
                        orcamentoId = c.orcamentoId,
                        orcamentoNumero = orc?.numero,
                        tiragem = c.tiragem,
                    )
                }
                orcamentoId != null -> editora.orcamento(orcamentoId)?.let { usarOrcamento(it) }
                clienteId != null -> editora.cliente(clienteId)?.let { usarCliente(it) }
            }
        }
    }

    fun atualizar(novo: FormContrato) {
        form = novo
        erro = null
    }

    fun usarCliente(c: ClienteEntity) {
        atualizar(
            form.copy(
                nome = c.nome,
                email = c.email,
                cpf = if (c.cpf.isBlank()) form.cpf else Validacao.formatarCpf(c.cpf),
                telefone = if (c.telefone.isBlank()) form.telefone else Validacao.formatarTelefone(c.telefone),
                cep = if (c.cep.isBlank()) form.cep else Validacao.formatarCep(c.cep),
                estado = c.estado.ifBlank { form.estado },
                cidade = c.cidade.ifBlank { form.cidade },
                rua = c.rua.ifBlank { form.rua },
                numero = c.numero.ifBlank { form.numero },
            )
        )
    }

    private suspend fun usarOrcamento(o: OrcamentoEntity) {
        atualizar(
            form.copy(
                orcamentoId = o.id,
                orcamentoNumero = o.numero,
                nome = o.clienteNome,
                email = o.clienteEmail,
            )
        )
        editora.cliente(o.clienteId)?.let { usarCliente(it) }
        orcamentoParaTiragem = o // a tela pede a tiragem e se o preço é normal ou à vista
    }

    fun linhasDoOrcamento(o: OrcamentoEntity): List<LinhaTiragem> = editora.resultadoDoOrcamento(o)?.linhas.orEmpty()

    fun escolherTiragem(o: OrcamentoEntity, linha: LinhaTiragem, aVista: Boolean) {
        orcamentoParaTiragem = null
        atualizar(
            form.copy(
                tiragem = linha.tiragem,
                valor = Formatos.paraCampo(if (aVista) linha.totalAVista else linha.total),
                formaPagamento = if (aVista) {
                    "À vista, com desconto de ${Formatos.numero(o.descontoAVistaPercentual)}%"
                } else {
                    form.formaPagamento
                },
            )
        )
    }

    fun cancelarEscolhaTiragem() {
        orcamentoParaTiragem = null
    }

    fun escolherOrcamento(o: OrcamentoEntity) {
        viewModelScope.launch { usarOrcamento(o) }
    }

    fun desvincularOrcamento() = atualizar(form.copy(orcamentoId = null, orcamentoNumero = null, tiragem = null))

    /** UC07 – gera o PDF do contrato (UC16: os dados também atualizam o cadastro do cliente). */
    fun gerarPdf(usuario: UsuarioEntity, aoConcluir: (String) -> Unit) {
        if (processando) return
        processando = true
        viewModelScope.launch {
            try {
                val f = form
                val dados = DadosContrato(
                    orcamentoId = f.orcamentoId,
                    nome = f.nome,
                    cpf = f.cpf,
                    email = f.email,
                    telefone = f.telefone,
                    cep = f.cep,
                    estado = f.estado,
                    cidade = f.cidade,
                    rua = f.rua,
                    numero = f.numero,
                    tiragem = f.tiragem,
                    valorTexto = f.valor,
                    prazoTexto = f.prazo,
                    formaPagamento = f.formaPagamento,
                    clausulasAdicionais = f.clausulas,
                )
                when (val r = editora.salvarContrato(contratoId, dados, usuario)) {
                    is Resultado.Ok -> {
                        pdf.gerarContrato(r.valor)
                        aoConcluir(r.valor.id)
                    }
                    is Resultado.Erro -> erro = r.mensagem
                }
            } catch (e: Exception) {
                erro = "Não foi possível gerar o PDF: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                processando = false
            }
        }
    }

    /** UC18 – Registrar contrato fechado pelo e-mail do cliente. */
    fun registrarFechamento(usuario: UsuarioEntity, aoSucesso: (String) -> Unit) {
        viewModelScope.launch {
            when (val r = editora.registrarFechamentoPorEmail(emailFechamento, usuario)) {
                is Resultado.Ok -> {
                    erroFechamento = null
                    emailFechamento = ""
                    runCatching { pdf.gerarContrato(r.valor) } // atualiza o PDF com a data de fechamento
                    aoSucesso("Contrato nº ${Formatos.numeroDocumento(r.valor.numero)} de ${r.valor.nome} registrado como fechado.")
                }
                is Resultado.Erro -> erroFechamento = r.mensagem
            }
        }
    }
}
