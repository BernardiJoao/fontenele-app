package br.com.fontenele.orcamentos.pdf

import android.content.Context
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.TypefaceSpan
import br.com.fontenele.orcamentos.data.local.ContratoEntity
import br.com.fontenele.orcamentos.data.local.OrcamentoEntity
import br.com.fontenele.orcamentos.data.repo.EditoraRepository
import br.com.fontenele.orcamentos.domain.Formatos
import br.com.fontenele.orcamentos.domain.Impressao
import br.com.fontenele.orcamentos.domain.Cobranca
import br.com.fontenele.orcamentos.domain.TipoDocumento
import br.com.fontenele.orcamentos.domain.Validacao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * UC06 – Produzir PDF de orçamento e UC07 – Produzir PDF de contrato.
 * Os PDFs ficam no armazenamento interno do app (filesDir/pdfs); o UC08 copia para Downloads.
 */
class GeradorPdf(
    private val context: Context,
    private val editora: EditoraRepository,
) {
    private val pasta: File get() = File(context.filesDir, "pdfs").apply { mkdirs() }
    private val marca: MarcaPdf by lazy { MarcaPdf.carregar(context) }

    fun arquivo(tipo: TipoDocumento, id: String): File = File(pasta, "${tipo.name.lowercase()}_$id.pdf")

    fun nomeParaDownload(tipo: TipoDocumento, numero: Int, cliente: String): String {
        val prefixo = if (tipo == TipoDocumento.ORCAMENTO) "Orcamento" else "Contrato"
        return "${prefixo}_${Formatos.numeroDocumento(numero)}_${Formatos.nomeArquivo(cliente)}.pdf"
    }

    /**
     * Orçamento como documento simples: logo no topo, título centralizado, dados em linhas
     * "Rótulo: valor", tabela quadriculada com os valores por tiragem e as condições.
     */
    suspend fun gerarOrcamento(o: OrcamentoEntity): File = withContext(Dispatchers.IO) {
        val logo = marca.logo // também carrega as fontes, antes de criar os estilos de texto
        val resultado = editora.resultadoDoOrcamento(o)
        val numero = Formatos.numeroDocumento(o.numero)
        val validoAte = o.criadoEm + o.validadeDias * 86_400_000L
        val desconto = Formatos.numero(o.descontoAVistaPercentual)

        val e = EscritorPdf()
        val corpo = EscritorPdf.texto(11f)
        val negrito = EscritorPdf.texto(11f, negrito = true)
        val pequeno = EscritorPdf.texto(10f)

        logo?.let {
            e.imagemCentralizada(it, 64f)
            e.espaco(14f)
        }
        e.paragrafo("ORÇAMENTO Nº $numero", EscritorPdf.texto(13f, negrito = true), depois = 2f, alinhamento = Layout.Alignment.ALIGN_CENTER)
        e.paragrafo("Data: ${Formatos.data(o.atualizadoEm)}", corpo, depois = 18f, alinhamento = Layout.Alignment.ALIGN_CENTER)

        e.paragrafo(comNegrito("Cliente: ", o.clienteNome), corpo, depois = 2f)
        e.paragrafo(comNegrito("E-mail: ", o.clienteEmail), corpo, depois = 14f)

        e.paragrafo("Dados do livro", negrito, depois = 4f)
        listOf(
            "Impressão do miolo: " to (Impressao.de(o.impressao)?.rotulo ?: o.impressao),
            "Número de páginas: " to o.paginas.toString(),
            "Formato: " to "${o.proporcao} cm",
            "Tiragens: " to Formatos.listaTiragens(Formatos.tiragensDoTexto(o.tiragens)),
        ).forEach { (rotulo, valor) -> e.paragrafo(comNegrito(rotulo, valor), corpo, depois = 2f) }
        e.espaco(12f)

        e.paragrafo("Valores por tiragem", negrito, depois = 6f)
        e.tabela(
            cabecalho = listOf("Tiragem", "Valor total", "Valor por exemplar", "À vista (−$desconto%)", "À vista por exemplar"),
            linhas = resultado?.linhas.orEmpty().map {
                listOf(
                    "${Formatos.numero(it.tiragem.toDouble(), 0)} exemplares",
                    Formatos.moeda(it.total),
                    Formatos.moeda(it.valorUnitario),
                    Formatos.moeda(it.totalAVista),
                    Formatos.moeda(it.valorUnitarioAVista),
                )
            },
            pesos = listOf(1.25f, 1.2f, 1.05f, 1.2f, 1.05f),
            colunasDireita = setOf(1, 2, 3, 4),
        )
        val adicionais = resultado?.adicionais.orEmpty()
        if (adicionais.isNotEmpty()) {
            e.paragrafo(
                "Os valores já incluem os serviços adicionais: " + adicionais.joinToString("; ") {
                    "${it.descricao} (${Formatos.moedaUnitaria(it.preco)} – ${Cobranca.de(it.cobranca).rotulo.lowercase()})"
                } + ".",
                pequeno,
                depois = 4f,
            )
        }
        e.paragrafo("Pagamento à vista com $desconto% de desconto.", pequeno, depois = 14f)

        if (o.observacoes.isNotBlank()) {
            e.paragrafo("Observações", negrito, depois = 4f)
            e.paragrafo(o.observacoes, corpo, depois = 14f, justificado = true)
        }

        e.paragrafo("Condições", negrito, depois = 4f)
        e.paragrafo(
            "Este orçamento é válido por ${o.validadeDias} dias, até ${Formatos.data(validoAte)}. " +
                "Depois desse prazo os valores podem ser reajustados. A produção começa após a escolha da " +
                "tiragem, a aprovação do orçamento e a assinatura do contrato.",
            corpo,
            depois = 14f,
            justificado = true,
        )
        e.paragrafo("Atenciosamente,", corpo, depois = 2f)
        e.paragrafo(o.usuarioNome, corpo, depois = 2f)
        e.paragrafo("Fontenele Publicações", corpo)

        val arquivo = arquivo(TipoDocumento.ORCAMENTO, o.id)
        e.salvar(arquivo)
        arquivo
    }

    /**
     * Contrato em formato de documento comum (como feito no Word): fundo branco, tudo em preto,
     * Arial 11, título centralizado, partes em parágrafo, cláusulas numeradas e assinaturas no fim.
     */
    suspend fun gerarContrato(c: ContratoEntity): File = withContext(Dispatchers.IO) {
        FontesPdf.carregar(context)
        val orcamento = c.orcamentoId?.let { editora.orcamento(it) }
        val numero = Formatos.numeroDocumento(c.numero)
        val e = EscritorPdf()

        val corpo = EscritorPdf.texto(11f)
        val negrito = EscritorPdf.texto(11f, negrito = true)

        fun texto(t: CharSequence) = e.paragrafo(t, corpo, depois = 8f, justificado = true)

        e.paragrafo("CONTRATO DE PRESTAÇÃO DE SERVIÇOS EDITORIAIS E GRÁFICOS", EscritorPdf.texto(12f, negrito = true), depois = 2f, alinhamento = Layout.Alignment.ALIGN_CENTER)
        e.paragrafo("Contrato nº $numero", corpo, depois = 18f, alinhamento = Layout.Alignment.ALIGN_CENTER)

        val endereco = listOf(
            listOf(c.rua, c.numeroEndereco.takeIf { it.isNotBlank() }?.let { "nº $it" }).filterNotNull().filter { it.isNotBlank() }.joinToString(", "),
            listOf(c.cidade, c.estado).filter { it.isNotBlank() }.joinToString("/"),
            if (c.cep.isBlank()) "" else "CEP ${Validacao.formatarCep(c.cep)}",
        ).filter { it.isNotBlank() }.joinToString(", ")
        texto(
            comNegrito(
                "CONTRATANTE: ",
                "${c.nome}, inscrito(a) no CPF sob o nº ${Validacao.formatarCpf(c.cpf)}, residente em $endereco, " +
                    "e-mail ${c.email}, telefone ${Validacao.formatarTelefone(c.telefone)}.",
            )
        )
        texto(comNegrito("CONTRATADA: ", "Fontenele Publicações, editora e prestadora de serviços gráficos."))
        texto("As partes acima identificadas têm entre si justo e acertado o presente contrato, que se regerá pelas cláusulas a seguir.")

        val objeto = buildString {
            append("A CONTRATADA prestará à CONTRATANTE os serviços editoriais e gráficos para a produção de um livro")
            if (orcamento != null) {
                append(
                    ", conforme o orçamento nº ${Formatos.numeroDocumento(orcamento.numero)}: " +
                        "${orcamento.paginas} páginas, miolo ${Impressao.de(orcamento.impressao)?.rotulo?.lowercase() ?: orcamento.impressao}, " +
                        "formato ${orcamento.proporcao} cm"
                )
            }
            if (c.tiragem != null) append(", com tiragem de ${Formatos.numero(c.tiragem.toDouble(), 0)} exemplares")
            append(".")
        }
        val clausulas = mutableListOf(
            "DO OBJETO" to objeto,
            "DO VALOR" to "Pelos serviços contratados, a CONTRATANTE pagará à CONTRATADA o valor total de ${Formatos.moeda(c.valor)}.",
            "DA FORMA DE PAGAMENTO" to c.formaPagamento.trimEnd('.') + ".",
            "DO PRAZO" to "A CONTRATADA entregará os exemplares em até ${c.prazoEntregaDias} dias após a aprovação da prova final pela CONTRATANTE.",
            "DAS OBRIGAÇÕES" to "A CONTRATANTE fornecerá os originais e aprovará as provas nos prazos combinados. " +
                "A CONTRATADA zelará pela qualidade do material e informará qualquer impedimento na produção.",
            "DA RESCISÃO" to "Este contrato pode ser rescindido por qualquer das partes mediante aviso por escrito, " +
                "ficando devidos os valores dos serviços já realizados.",
        )
        if (c.clausulasAdicionais.isNotBlank()) clausulas += "DISPOSIÇÕES ADICIONAIS" to c.clausulasAdicionais
        clausulas += "DO FORO" to "As partes elegem o foro da comarca da sede da CONTRATADA para resolver as questões deste contrato."

        clausulas.forEachIndexed { i, (nome, conteudo) ->
            e.paragrafo("CLÁUSULA ${i + 1}ª – $nome", negrito, depois = 3f)
            texto(conteudo)
        }

        texto("E, por estarem de acordo, as partes assinam o presente contrato.")
        val local = c.cidade.ifBlank { null }?.let { "$it, " } ?: ""
        e.paragrafo("$local${Formatos.dataPorExtenso(c.criadoEm)}.", corpo, depois = 8f)
        e.assinaturas(c.nome to "CONTRATANTE", "Fontenele Publicações" to "CONTRATADA")

        val arquivo = arquivo(TipoDocumento.CONTRATO, c.id)
        e.salvar(arquivo)
        arquivo
    }
}

/** Texto com o começo em negrito (ex.: "Cliente: " em negrito e o nome normal). */
private fun comNegrito(inicio: String, resto: String): CharSequence =
    SpannableStringBuilder(inicio).apply {
        setSpan(TypefaceSpan(FontesPdf.negrito), 0, inicio.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        append(resto)
    }
