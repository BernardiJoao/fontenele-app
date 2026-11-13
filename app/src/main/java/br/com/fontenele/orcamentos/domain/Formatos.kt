package br.com.fontenele.orcamentos.domain

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatos {
    val localeBR: Locale = Locale.forLanguageTag("pt-BR")

    fun moeda(valor: Double): String = NumberFormat.getCurrencyInstance(localeBR).format(valor)

    /** Para valores unitários pequenos (ex.: R$ 0,048 por página) mostra até 4 casas. */
    fun moedaUnitaria(valor: Double): String {
        val nf = NumberFormat.getCurrencyInstance(localeBR)
        nf.minimumFractionDigits = 2
        nf.maximumFractionDigits = 4
        return nf.format(valor)
    }

    fun numero(valor: Double, casas: Int = 2): String {
        val nf = NumberFormat.getNumberInstance(localeBR)
        nf.minimumFractionDigits = 0
        nf.maximumFractionDigits = casas
        return nf.format(valor)
    }

    /** Valor no formato aceito pelos campos de texto (ex.: 1234,5). */
    fun paraCampo(valor: Double): String {
        val nf = NumberFormat.getNumberInstance(localeBR)
        nf.isGroupingUsed = false
        nf.minimumFractionDigits = 0
        nf.maximumFractionDigits = 4
        return nf.format(valor)
    }

    /** "100, 200 e 300 exemplares" */
    fun listaTiragens(tiragens: List<Int>): String {
        val textos = tiragens.map { numero(it.toDouble(), 0) }
        return when (textos.size) {
            0 -> ""
            1 -> "${textos[0]} exemplares"
            else -> textos.dropLast(1).joinToString(", ") + " e " + textos.last() + " exemplares"
        }
    }

    fun tiragensDoTexto(texto: String): List<Int> = texto.split(',').mapNotNull { it.trim().toIntOrNull() }

    fun numeroDocumento(numero: Int): String = numero.toString().padStart(4, '0')

    fun data(ms: Long): String = SimpleDateFormat("dd/MM/yyyy", localeBR).format(Date(ms))

    fun dataHora(ms: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", localeBR).format(Date(ms))

    fun hora(ms: Long): String = SimpleDateFormat("HH:mm", localeBR).format(Date(ms))

    fun dataPorExtenso(ms: Long): String = SimpleDateFormat("d 'de' MMMM 'de' yyyy", localeBR).format(Date(ms))

    fun carimboArquivo(ms: Long): String = SimpleDateFormat("yyyyMMdd_HHmm", localeBR).format(Date(ms))

    /** Remove acentos e caracteres especiais para nomes de arquivo. */
    fun nomeArquivo(texto: String): String {
        val semAcento = java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return semAcento.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').take(40).ifEmpty { "cliente" }
    }
}
