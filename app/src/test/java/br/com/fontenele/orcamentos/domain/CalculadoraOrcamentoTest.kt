package br.com.fontenele.orcamentos.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Os valores esperados foram tirados da própria planilha "TABELA DE VALORES PARA ORÇAMENTO"
 * (células K6:P12 com J2 = 100 páginas e fator de correção J3 = 1,5).
 */
class CalculadoraOrcamentoTest {

    private val tabela: TabelaValores by lazy {
        val arquivo = listOf(
            File("src/main/assets/tabela_valores.csv"),
            File("app/src/main/assets/tabela_valores.csv"),
            File(System.getProperty("tabela.csv", "tabela_valores.csv")),
        ).first { it.exists() }
        TabelaValores.carregar(arquivo.readText())
    }
    private val parametros = ParametrosCalculo()

    private fun calcular(
        paginas: Int = 100,
        proporcao: String = "14x21",
        impressao: Impressao = Impressao.PB,
        coeficiente: Double = 1.5,
        tiragens: List<Int> = TabelaValores.TIRAGENS,
        adicionais: List<ProdutoPreco> = emptyList(),
        parametros: ParametrosCalculo = this.parametros,
    ) = CalculadoraOrcamento.calcular(
        EntradaOrcamento(impressao, paginas, proporcao, tiragens, coeficiente, adicionais.map { it.id }.toSet()),
        tabela, parametros, adicionais,
    )

    private fun totais(r: Resultado<ResultadoOrcamento>) = (r as Resultado.Ok).valor.linhas.map { it.total }

    @Test
    fun tabelaCobre16a1088Paginas() {
        assertEquals(16, tabela.paginaMinima)
        assertEquals(1088, tabela.paginaMaxima)
    }

    @Test
    fun formato14x21ComFator15IgualAPlanilha() {
        val esperado = listOf(3161.22, 4485.0, 5742.0, 9384.79, 12108.69, 18423.37)
        totais(calcular()).zip(esperado).forEach { (obtido, planilha) -> assertEquals(planilha, obtido, 0.01) }
    }

    @Test
    fun fatoresDeFormatoIguaisAPlanilha() {
        assertEquals(listOf(3003.16, 4260.75, 5454.9, 8915.55, 11503.25, 17502.2), totais(calcular(proporcao = "10x15")))
        assertEquals(listOf(3477.34, 4933.5, 6316.2, 10323.27, 13319.56, 20265.7), totais(calcular(proporcao = "16x23")))
        assertEquals(listOf(4741.83, 6727.5, 8613.0, 14077.19, 18163.03, 27635.05), totais(calcular(proporcao = "21x28")))
    }

    @Test
    fun extremosDaTabela() {
        // A17:G17 e A1089:G1089 da planilha (já com o fator 1,5)
        assertEquals(1.5 * 1942.5, totais(calcular(paginas = 16))[0], 0.01)
        assertEquals(1.5 * 75956.0, totais(calcular(paginas = 1088)).last(), 0.01)
    }

    @Test
    fun descontoAVistaEValorUnitario() {
        val linha = (calcular(tiragens = listOf(100)) as Resultado.Ok).valor.linhas.single()
        assertEquals(3161.22 * 0.9, linha.totalAVista, 0.01)
        assertEquals(31.61, linha.valorUnitario, 0.001)
        assertEquals(28.45, linha.valorUnitarioAVista, 0.001)
    }

    /**
     * Casos em que arredondar no meio da conta dava 1 centavo de diferença da planilha.
     * Valores esperados = texto exibido pela planilha recalculada no LibreOffice.
     */
    @Test
    fun bateCentavoACentavoComAPlanilhaNosCasosDeArredondamento() {
        fun linha(pag: Int, formato: String, tiragem: Int) =
            (calcular(paginas = pag, proporcao = formato, tiragens = listOf(tiragem)) as Resultado.Ok).valor.linhas.single()
        assertEquals(7997.93, linha(17, "18x26", 1000).total, 0.0)
        assertEquals(9444.83, linha(18, "21x28", 1000).total, 0.0)
        assertEquals(6963.77, linha(16, "14x21", 2000).totalAVista, 0.0)
        assertEquals(2884.61, linha(16, "16x23", 100).totalAVista, 0.0)
        assertEquals(49.40, linha(176, "16x23", 100).valorUnitario, 0.0)
        assertEquals(5.27, linha(39, "14x21", 2000).valorUnitarioAVista, 0.0)
    }

    @Test
    fun coeficienteDeInflacaoMultiplicaATabela() {
        val r = totais(calcular(tiragens = listOf(100), coeficiente = 1.0))
        assertEquals(2107.48, r.single(), 0.01)
    }

    @Test
    fun adicionaisSaoSomadosPorTiragem() {
        val revisao = ProdutoPreco("rev", "Revisão", 2.0, Cobranca.POR_PAGINA)
        val marcador = ProdutoPreco("mar", "Marcador", 0.5, Cobranca.POR_EXEMPLAR)
        val r = (calcular(tiragens = listOf(100, 200), adicionais = listOf(revisao, marcador)) as Resultado.Ok).valor
        assertEquals(200.0 + 50.0, r.linhas[0].valorAdicionais, 0.001)
        assertEquals(200.0 + 100.0, r.linhas[1].valorAdicionais, 0.001)
        assertEquals(3161.22 + 250.0, r.linhas[0].total, 0.01)
    }

    @Test
    fun rejeitaEntradasInvalidas() {
        assertTrue(calcular(paginas = 8) is Resultado.Erro)
        assertTrue(calcular(paginas = 2000) is Resultado.Erro)
        assertTrue(calcular(proporcao = "99x99") is Resultado.Erro)
        assertTrue(calcular(tiragens = emptyList()) is Resultado.Erro)
        assertTrue(calcular(coeficiente = 0.0) is Resultado.Erro)
    }

    @Test
    fun coloridoSoFuncionaDepoisDeOAdminDefinirOFator() {
        assertTrue(calcular(impressao = Impressao.COLORIDO) is Resultado.Erro)
        val comColorido = ParametrosCalculo(fatorColorido = 2.0)
        assertEquals(2 * 3161.22, totais(calcular(impressao = Impressao.COLORIDO, tiragens = listOf(100), parametros = comColorido)).single(), 0.02)
    }
}
