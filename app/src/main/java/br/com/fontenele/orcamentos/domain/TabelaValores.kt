package br.com.fontenele.orcamentos.domain

/**
 * Tabela de valores da editora (planilha "TABELA DE VALORES PARA ORÇAMENTO", colunas S:Y).
 * Para cada número de páginas (16 a 1088) traz o preço total do livro 14x21 cm em 6 tiragens,
 * ANTES do fator de correção. O arquivo fica em app/src/main/assets/tabela_valores.csv.
 */
class TabelaValores private constructor(private val valores: Map<Int, DoubleArray>) {

    val paginaMinima: Int = valores.keys.min()
    val paginaMaxima: Int = valores.keys.max()

    /** Valor da tabela para as páginas e a tiragem; null se estiver fora da tabela. */
    fun valor(paginas: Int, tiragem: Int): Double? {
        val coluna = TIRAGENS.indexOf(tiragem)
        if (coluna < 0) return null
        return valores[paginas]?.get(coluna)
    }

    companion object {
        /** Tiragens da planilha (colunas 100, 200, 300, 500, 1000 e 2000 exemplares). */
        val TIRAGENS = listOf(100, 200, 300, 500, 1000, 2000)

        /** Lê o CSV "paginas;100;200;300;500;1000;2000" (linhas com # são comentários). */
        fun carregar(csv: String): TabelaValores {
            val mapa = HashMap<Int, DoubleArray>()
            csv.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("paginas") }
                .forEach { linha ->
                    val partes = linha.split(';')
                    require(partes.size == TIRAGENS.size + 1) { "Linha inválida na tabela de valores: $linha" }
                    mapa[partes[0].toInt()] = DoubleArray(TIRAGENS.size) { i -> partes[i + 1].toDouble() }
                }
            require(mapa.isNotEmpty()) { "Tabela de valores vazia." }
            return TabelaValores(mapa)
        }
    }
}

/**
 * Parâmetros que o Administrador ajusta (UC04 – "alterar os preços e valores").
 * Os padrões são os da planilha: fator de correção 1,5 (J3), fatores de formato (J6:J12)
 * e desconto à vista de até 10% (Q15). A planilha não tem preço para impressão colorida,
 * por isso o fator colorido começa sem valor (0 = não definido).
 */
data class ParametrosCalculo(
    val coeficientePadrao: Double = COEFICIENTE_PADRAO,
    val descontoAVistaPercentual: Double = DESCONTO_PADRAO,
    val fatorColorido: Double = 0.0,
    val fatoresFormato: Map<String, Double> = Proporcoes.fatoresPadrao,
) {
    val coloridoDefinido: Boolean get() = fatorColorido > 0.0

    companion object {
        const val COEFICIENTE_PADRAO = 1.5
        const val DESCONTO_PADRAO = 10.0

        // Chaves gravadas na tabela "parametros" do banco
        const val CHAVE_COEFICIENTE = "coeficiente_inflacao"
        const val CHAVE_DESCONTO = "desconto_a_vista"
        const val CHAVE_COLORIDO = "fator_colorido"
        const val PREFIXO_FORMATO = "formato_"

        fun deMapa(valores: Map<String, Double>): ParametrosCalculo = ParametrosCalculo(
            coeficientePadrao = valores[CHAVE_COEFICIENTE] ?: COEFICIENTE_PADRAO,
            descontoAVistaPercentual = valores[CHAVE_DESCONTO] ?: DESCONTO_PADRAO,
            fatorColorido = valores[CHAVE_COLORIDO] ?: 0.0,
            fatoresFormato = Proporcoes.formatos.associateWith { f ->
                valores[PREFIXO_FORMATO + f] ?: Proporcoes.fatoresPadrao.getValue(f)
            },
        )
    }
}
