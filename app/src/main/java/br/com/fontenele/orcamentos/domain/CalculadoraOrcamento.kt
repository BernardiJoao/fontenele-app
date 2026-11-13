package br.com.fontenele.orcamentos.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Serviço adicional cadastrado pelo Administrador (UC03), cobrado à parte da tabela. */
data class ProdutoPreco(
    val id: String,
    val nome: String,
    val preco: Double,
    val cobranca: Cobranca,
)

/** Uma linha do orçamento: o preço para uma tiragem, normal e à vista (como o bloco I:Q da planilha). */
data class LinhaTiragem(
    val tiragem: Int,
    val valorTabela: Double,
    val valorAdicionais: Double,
    val total: Double,
    val valorUnitario: Double,
    val totalAVista: Double,
    val valorUnitarioAVista: Double,
)

data class AdicionalAplicado(
    val descricao: String,
    val preco: Double,
    val cobranca: String,
)

/** Resultado salvo junto com o orçamento (o PDF não muda se a tabela ou os preços mudarem depois). */
data class ResultadoOrcamento(
    val linhas: List<LinhaTiragem>,
    val adicionais: List<AdicionalAplicado>,
    val fatorFormato: Double,
    val coeficienteInflacao: Double,
    val fatorImpressao: Double,
    val descontoAVistaPercentual: Double,
)

/** Variáveis informadas pelo usuário (UC05 e UC11). */
data class EntradaOrcamento(
    val impressao: Impressao,
    val paginas: Int,
    val proporcao: String,
    val tiragens: List<Int>,
    val coeficienteInflacao: Double,
    val adicionaisIds: Set<String>,
)

/**
 * UC05 – Calcular automaticamente o orçamento, seguindo a planilha da editora:
 *
 *   valor da tabela (páginas × tiragem) × fator do formato × coeficiente de inflação (× fator colorido)
 *   + serviços adicionais
 *   valor à vista = total − desconto à vista (padrão 10%)
 *   valor unitário = total ÷ tiragem
 *
 * As contas são feitas na MESMA ORDEM da planilha e sem arredondar no meio do caminho
 * (coeficiente × tabela, depois × formato; à vista = total − total × desconto). Só o valor
 * final é arredondado em centavos, como a planilha faz ao exibir. Assim o app bate centavo
 * a centavo com a planilha (conferido em todas as 45.066 combinações de páginas, formatos e tiragens).
 */
object CalculadoraOrcamento {
    const val COEFICIENTE_MAXIMO = 10.0

    fun calcular(
        entrada: EntradaOrcamento,
        tabela: TabelaValores,
        parametros: ParametrosCalculo,
        adicionaisDisponiveis: List<ProdutoPreco>,
    ): Resultado<ResultadoOrcamento> {
        if (entrada.paginas !in tabela.paginaMinima..tabela.paginaMaxima) {
            return Resultado.Erro("O número de páginas deve estar entre ${tabela.paginaMinima} e ${tabela.paginaMaxima}.")
        }
        if (entrada.tiragens.isEmpty()) return Resultado.Erro("Selecione pelo menos uma tiragem.")
        if (entrada.tiragens.any { it !in TabelaValores.TIRAGENS }) return Resultado.Erro("Tiragem inválida.")
        if (entrada.coeficienteInflacao <= 0.0 || entrada.coeficienteInflacao > COEFICIENTE_MAXIMO) {
            return Resultado.Erro("O coeficiente de inflação deve ser maior que zero (padrão da tabela: 1,5).")
        }
        val fatorFormato = parametros.fatoresFormato[entrada.proporcao]
            ?: return Resultado.Erro("Selecione uma proporção válida.")
        val fatorImpressao = when (entrada.impressao) {
            Impressao.PB -> 1.0
            Impressao.COLORIDO -> {
                if (!parametros.coloridoDefinido) {
                    return Resultado.Erro(
                        "A tabela da editora só tem preços de miolo preto e branco. Para orçar colorido, " +
                            "o Administrador precisa definir o fator da impressão colorida em Preços e parâmetros."
                    )
                }
                parametros.fatorColorido
            }
        }
        val adicionais = adicionaisDisponiveis.filter { it.id in entrada.adicionaisIds }
        val desconto = parametros.descontoAVistaPercentual

        val linhas = entrada.tiragens.distinct().sorted().map { tiragem ->
            val base = tabela.valor(entrada.paginas, tiragem)
                ?: return Resultado.Erro("Não há valor na tabela para ${entrada.paginas} páginas e $tiragem exemplares.")
            // Planilha: A:G = J3 × S:Y; K6:P12 = PROCV(...) × fator do formato
            val valorTabela = entrada.coeficienteInflacao * base * fatorFormato * fatorImpressao
            val valorAdicionais = adicionais.sumOf { custoAdicional(it, entrada.paginas, tiragem) }
            val total = valorTabela + valorAdicionais
            // Planilha: O17 = J17 − (J17 × Q15); L17 = J17 ÷ tiragem; Q17 = O17 ÷ tiragem
            val totalAVista = total - total * (desconto / 100.0)
            LinhaTiragem(
                tiragem = tiragem,
                valorTabela = arredondar(valorTabela),
                valorAdicionais = arredondar(valorAdicionais),
                total = arredondar(total),
                valorUnitario = arredondar(total / tiragem),
                totalAVista = arredondar(totalAVista),
                valorUnitarioAVista = arredondar(totalAVista / tiragem),
            )
        }

        return Resultado.Ok(
            ResultadoOrcamento(
                linhas = linhas,
                adicionais = adicionais.map { AdicionalAplicado(it.nome, it.preco, it.cobranca.name) },
                fatorFormato = fatorFormato,
                coeficienteInflacao = entrada.coeficienteInflacao,
                fatorImpressao = fatorImpressao,
                descontoAVistaPercentual = desconto,
            )
        )
    }

    fun custoAdicional(p: ProdutoPreco, paginas: Int, tiragem: Int): Double = when (p.cobranca) {
        Cobranca.FIXO -> p.preco
        Cobranca.POR_PAGINA -> p.preco * paginas
        Cobranca.POR_EXEMPLAR -> p.preco * tiragem
        Cobranca.POR_PAGINA_EXEMPLAR -> p.preco * paginas * tiragem
    }

    /**
     * Arredonda para centavos como a planilha exibe: primeiro limita a 15 algarismos significativos
     * (precisão que Excel/LibreOffice mostram, o que corrige restos como 7997,924999999999),
     * depois arredonda "meio para cima".
     */
    fun arredondar(valor: Double): Double {
        if (valor.isNaN() || valor.isInfinite()) return valor
        return BigDecimal(valor).round(MathContext(15, RoundingMode.HALF_EVEN))
            .setScale(2, RoundingMode.HALF_UP).toDouble()
    }
}
