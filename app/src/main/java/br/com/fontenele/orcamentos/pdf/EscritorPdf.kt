package br.com.fontenele.orcamentos.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import br.com.fontenele.orcamentos.R
import java.io.File
import java.io.FileOutputStream

/** Fontes dos PDFs: Arimo (mesmas medidas da Arial). Carregadas uma vez a partir de res/font. */
internal object FontesPdf {
    @Volatile var normal: Typeface = Typeface.SANS_SERIF
    @Volatile var negrito: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

    fun carregar(context: Context) {
        runCatching { ResourcesCompat.getFont(context, R.font.arimo_regular) }.getOrNull()?.let { normal = it }
        runCatching { ResourcesCompat.getFont(context, R.font.arimo_bold) }.getOrNull()?.let { negrito = it }
    }
}

/** Logo da editora (imagem PNG), colocado no topo do orçamento. */
internal class MarcaPdf(val logo: Bitmap?) {
    companion object {
        fun carregar(context: Context): MarcaPdf {
            FontesPdf.carregar(context)
            return MarcaPdf(runCatching { BitmapFactory.decodeResource(context.resources, R.drawable.logo_fontenele) }.getOrNull())
        }
    }
}

/**
 * Monta PDFs simples, como um documento comum de Word, usando o PdfDocument nativo do Android:
 * página A4 branca, margens de 2,5 cm, texto em preto na fonte Arial (Arimo), sem cores.
 * Cuida de quebra de página, parágrafos, tabela quadriculada e linhas de assinatura.
 */
internal class EscritorPdf {

    companion object {
        const val LARGURA = 595 // A4 em pontos (1/72 pol.)
        const val ALTURA = 842
        const val MARGEM = 71f // 2,5 cm
        const val LARGURA_UTIL = LARGURA - 2 * MARGEM

        fun texto(tamanho: Float, negrito: Boolean = false, alinhamento: Paint.Align = Paint.Align.LEFT) =
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = tamanho
                color = Color.BLACK
                textAlign = alinhamento
                typeface = if (negrito) FontesPdf.negrito else FontesPdf.normal
            }

        private val TRACO = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = 0.6f
            style = Paint.Style.STROKE
        }
    }

    private val doc = PdfDocument()
    private var pagina: PdfDocument.Page? = null
    private var numeroPagina = 0

    var y = 0f
        private set

    private val canvas: Canvas get() = pagina!!.canvas
    private val limiteInferior = ALTURA - MARGEM

    init {
        novaPagina()
    }

    private fun novaPagina() {
        pagina?.let { doc.finishPage(it) }
        numeroPagina++
        pagina = doc.startPage(PdfDocument.PageInfo.Builder(LARGURA, ALTURA, numeroPagina).create())
        y = MARGEM
    }

    private fun layout(
        texto: CharSequence,
        paint: TextPaint,
        largura: Int,
        alinhamento: Layout.Alignment,
        justificado: Boolean = false,
    ): StaticLayout =
        StaticLayout.Builder.obtain(texto, 0, texto.length, paint, largura.coerceAtLeast(10))
            .setAlignment(alinhamento)
            .setLineSpacing(0f, 1.15f)
            .setIncludePad(false)
            .setJustificationMode(if (justificado) Layout.JUSTIFICATION_MODE_INTER_WORD else Layout.JUSTIFICATION_MODE_NONE)
            .build()

    fun espaco(altura: Float) {
        y += altura
    }

    fun garantirEspaco(altura: Float) {
        if (y + altura > limiteInferior) novaPagina()
    }

    /** Parágrafo com quebra de linha automática e continuação na página seguinte, se precisar. */
    fun paragrafo(
        texto: CharSequence,
        paint: TextPaint,
        depois: Float = 8f,
        alinhamento: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
        justificado: Boolean = false,
    ) {
        val l = layout(texto, paint, LARGURA_UTIL.toInt(), alinhamento, justificado)
        var linha = 0
        while (linha < l.lineCount) {
            val topo = l.getLineTop(linha)
            var fim = linha
            while (fim < l.lineCount && (l.getLineBottom(fim) - topo) <= (limiteInferior - y)) fim++
            if (fim == linha) {
                novaPagina()
                continue
            }
            val alturaBloco = (l.getLineTop(fim) - topo).toFloat()
            val c = canvas
            c.save()
            c.translate(MARGEM, y - topo)
            c.clipRect(0f, topo.toFloat(), LARGURA_UTIL, topo + alturaBloco)
            l.draw(c)
            c.restore()
            y += alturaBloco
            linha = fim
            if (linha < l.lineCount) novaPagina()
        }
        y += depois
    }

    /** Imagem centralizada (ex.: o logo no topo do orçamento). */
    fun imagemCentralizada(imagem: Bitmap, altura: Float) {
        val largura = altura * imagem.width / imagem.height
        garantirEspaco(altura)
        val x = MARGEM + (LARGURA_UTIL - largura) / 2
        canvas.drawBitmap(imagem, null, RectF(x, y, x + largura, y + altura), Paint(Paint.FILTER_BITMAP_FLAG))
        y += altura
    }

    /**
     * Tabela quadriculada: todas as linhas e colunas separadas por traços pretos,
     * cabeçalho em negrito, sem cores de fundo.
     */
    fun tabela(
        cabecalho: List<String>,
        linhas: List<List<String>>,
        pesos: List<Float>,
        colunasDireita: Set<Int>,
        tamanho: Float = 10f,
    ) {
        val soma = pesos.sum()
        val larguras = pesos.map { it / soma * LARGURA_UTIL }
        linhaTabela(cabecalho, larguras, colunasDireita, texto(tamanho, negrito = true))
        linhas.forEach { linhaTabela(it, larguras, colunasDireita, texto(tamanho)) }
        y += 8f
    }

    private fun linhaTabela(celulas: List<String>, larguras: List<Float>, colunasDireita: Set<Int>, paint: TextPaint) {
        val pad = 4f
        val layouts = celulas.mapIndexed { i, t ->
            layout(
                t, paint, (larguras[i] - 2 * pad).toInt(),
                if (i in colunasDireita) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL,
            )
        }
        val altura = layouts.maxOf { it.height } + 2 * pad
        garantirEspaco(altura)
        val c = canvas
        var x = MARGEM
        layouts.forEachIndexed { i, l ->
            c.drawRect(x, y, x + larguras[i], y + altura, TRACO) // borda da célula
            c.save()
            c.translate(x + pad, y + pad)
            l.draw(c)
            c.restore()
            x += larguras[i]
        }
        y += altura
    }

    /** Duas linhas de assinatura lado a lado, com nome e papel embaixo. */
    fun assinaturas(esquerda: Pair<String, String>, direita: Pair<String, String>, tamanho: Float = 11f) {
        garantirEspaco(90f)
        y += 50f
        val meia = LARGURA_UTIL / 2
        val p = texto(tamanho, alinhamento = Paint.Align.CENTER)
        listOf(esquerda to MARGEM, direita to MARGEM + meia).forEach { (par, x) ->
            canvas.drawLine(x + 14f, y, x + meia - 14f, y, TRACO)
            val centro = x + meia / 2
            canvas.drawText(par.first, centro, y + 14f, p)
            canvas.drawText(par.second, centro, y + 28f, p)
        }
        y += 36f
    }

    fun salvar(arquivo: File) {
        pagina?.let { doc.finishPage(it) }
        pagina = null
        arquivo.parentFile?.mkdirs()
        FileOutputStream(arquivo).use { doc.writeTo(it) }
        doc.close()
    }
}
