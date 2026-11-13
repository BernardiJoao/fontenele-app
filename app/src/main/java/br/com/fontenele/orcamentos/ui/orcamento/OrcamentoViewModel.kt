package br.com.fontenele.orcamentos.ui.orcamento

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.fontenele.orcamentos.data.local.ClienteEntity
import br.com.fontenele.orcamentos.data.local.ProdutoEntity
import br.com.fontenele.orcamentos.data.local.UsuarioEntity
import br.com.fontenele.orcamentos.data.repo.DadosOrcamento
import br.com.fontenele.orcamentos.data.repo.EditoraRepository
import br.com.fontenele.orcamentos.domain.CalculadoraOrcamento
import br.com.fontenele.orcamentos.domain.EntradaOrcamento
import br.com.fontenele.orcamentos.domain.Formatos
import br.com.fontenele.orcamentos.domain.Impressao
import br.com.fontenele.orcamentos.domain.ParametrosCalculo
import br.com.fontenele.orcamentos.domain.Resultado
import br.com.fontenele.orcamentos.domain.ResultadoOrcamento
import br.com.fontenele.orcamentos.domain.TabelaValores
import br.com.fontenele.orcamentos.domain.Validacao
import br.com.fontenele.orcamentos.pdf.GeradorPdf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Campos do formulário (os do sistema web + coeficiente de inflação, tiragens, adicionais e condições). */
data class FormOrcamento(
    val clienteNome: String = "",
    val clienteEmail: String = "",
    val impressao: Impressao? = null,
    val paginas: String = "",
    val proporcao: String? = null,
    val coeficiente: String = "",
    val tiragens: Set<Int> = TabelaValores.TIRAGENS.toSet(),
    val adicionais: Set<String> = emptySet(),
    val validadeDias: String = "15",
    val observacoes: String = "",
)

class OrcamentoViewModel(
    private val editora: EditoraRepository,
    private val pdf: GeradorPdf,
    private val orcamentoId: String?,
    clienteId: String?,
) : ViewModel() {

    var form by mutableStateOf(FormOrcamento())
        private set
    var resultado by mutableStateOf<ResultadoOrcamento?>(null)
        private set
    var erro by mutableStateOf<String?>(null)
        private set
    var processando by mutableStateOf(false)
        private set
    var numeroExistente by mutableStateOf<Int?>(null)
        private set
    var faixaPaginas by mutableStateOf("16 a 1088")
        private set

    private var coeficientePadrao = Formatos.paraCampo(ParametrosCalculo.COEFICIENTE_PADRAO)

    val parametros: StateFlow<ParametrosCalculo> = editora.observarParametros()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ParametrosCalculo())

    val produtos: StateFlow<List<ProdutoEntity>> = editora.observarProdutosAtivos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val clientes: StateFlow<List<ClienteEntity>> = editora.observarClientes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val tabela = editora.tabela()
            faixaPaginas = "${tabela.paginaMinima} a ${tabela.paginaMaxima}"
            coeficientePadrao = Formatos.paraCampo(editora.parametros().coeficientePadrao)
            if (form.coeficiente.isBlank()) form = form.copy(coeficiente = coeficientePadrao)
            if (orcamentoId != null) {
                // UC15 – alterar um orçamento já gerado
                editora.orcamento(orcamentoId)?.let { o ->
                    numeroExistente = o.numero
                    form = FormOrcamento(
                        clienteNome = o.clienteNome,
                        clienteEmail = o.clienteEmail,
                        impressao = Impressao.de(o.impressao),
                        paginas = o.paginas.toString(),
                        proporcao = o.proporcao,
                        coeficiente = Formatos.paraCampo(o.coeficienteInflacao),
                        tiragens = Formatos.tiragensDoTexto(o.tiragens).toSet(),
                        adicionais = o.adicionaisIds.split(',').filter { it.isNotBlank() }.toSet(),
                        validadeDias = o.validadeDias.toString(),
                        observacoes = o.observacoes,
                    )
                }
            } else if (clienteId != null) {
                editora.cliente(clienteId)?.let { c -> form = form.copy(clienteNome = c.nome, clienteEmail = c.email) }
            }
        }
    }

    /** Qualquer alteração no formulário invalida o cálculo anterior. */
    fun atualizar(novo: FormOrcamento) {
        form = novo
        resultado = null
        erro = null
    }

    fun escolherCliente(c: ClienteEntity) = atualizar(form.copy(clienteNome = c.nome, clienteEmail = c.email))

    fun alternarTiragem(tiragem: Int) {
        val atual = form.tiragens
        atualizar(form.copy(tiragens = if (tiragem in atual) atual - tiragem else atual + tiragem))
    }

    fun alternarAdicional(id: String) {
        val atual = form.adicionais
        atualizar(form.copy(adicionais = if (id in atual) atual - id else atual + id))
    }

    private fun lerEntrada(): Resultado<Pair<EntradaOrcamento, Int>> {
        val f = form
        val paginas = Validacao.lerInteiro(f.paginas)
        val coeficiente = Validacao.lerDecimal(f.coeficiente)
        val validade = Validacao.lerInteiro(f.validadeDias)
        if (f.clienteNome.isBlank() || f.clienteEmail.isBlank() || f.impressao == null || f.proporcao == null ||
            paginas == null || coeficiente == null || validade == null
        ) {
            // UC05 fluxo 3.A
            return Resultado.Erro("Preencha todos os campos corretamente")
        }
        if (!Validacao.emailValido(f.clienteEmail)) return Resultado.Erro("Informe um e-mail válido para o cliente.")
        if (validade !in 1..365) return Resultado.Erro("A validade do orçamento deve ser de 1 a 365 dias.")
        val entrada = EntradaOrcamento(
            impressao = f.impressao,
            paginas = paginas,
            proporcao = f.proporcao,
            tiragens = f.tiragens.sorted(),
            coeficienteInflacao = coeficiente,
            adicionaisIds = f.adicionais,
        )
        return Resultado.Ok(entrada to validade)
    }

    /** UC05 – usa a tabela de valores, os parâmetros atuais e os serviços adicionais cadastrados. */
    private suspend fun calcularAgora(): Pair<ResultadoOrcamento, Pair<EntradaOrcamento, Int>>? {
        val lido = lerEntrada()
        if (lido is Resultado.Erro) {
            erro = lido.mensagem
            return null
        }
        val (entrada, validade) = (lido as Resultado.Ok).valor
        val calculo = CalculadoraOrcamento.calcular(
            entrada, editora.tabela(), editora.parametros(), editora.precosAtuais(),
        )
        return when (calculo) {
            is Resultado.Ok -> {
                resultado = calculo.valor
                erro = null
                calculo.valor to (entrada to validade)
            }
            is Resultado.Erro -> {
                resultado = null
                erro = calculo.mensagem
                null
            }
        }
    }

    fun calcular() {
        viewModelScope.launch { calcularAgora() }
    }

    /** UC06 – gera o PDF; UC13/UC17/UC02 são feitos pelo repositório na mesma transação. */
    fun gerarPdf(usuario: UsuarioEntity, aoConcluir: (String) -> Unit) {
        if (processando) return
        processando = true
        viewModelScope.launch {
            try {
                val calculado = calcularAgora()
                if (calculado != null) {
                    val (res, dados) = calculado
                    val (entrada, validade) = dados
                    val orcamento = editora.salvarOrcamento(
                        idExistente = orcamentoId,
                        d = DadosOrcamento(form.clienteNome, form.clienteEmail, entrada, validade, form.observacoes),
                        resultado = res,
                        usuario = usuario,
                    )
                    pdf.gerarOrcamento(orcamento)
                    aoConcluir(orcamento.id)
                }
            } catch (e: Exception) {
                erro = "Não foi possível gerar o PDF: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                processando = false
            }
        }
    }
}
