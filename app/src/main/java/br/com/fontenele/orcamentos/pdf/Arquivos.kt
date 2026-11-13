package br.com.fontenele.orcamentos.pdf

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.core.content.FileProvider
import br.com.fontenele.orcamentos.domain.Resultado
import java.io.File
import java.io.IOException
import java.io.OutputStream

/** Operações com arquivos: armazenamento externo (Downloads), compartilhamento e pré-visualização. */
object Arquivos {

    const val PASTA_DOWNLOADS = "Fontenele"

    fun uriCompartilhavel(context: Context, arquivo: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", arquivo)

    /** UC08 – Baixar PDF gerado: copia o PDF interno para Downloads/Fontenele (armazenamento externo). */
    fun salvarEmDownloads(context: Context, origem: File, nome: String): Resultado<String> {
        if (!origem.exists()) return Resultado.Erro("Arquivo não encontrado. Gere o PDF novamente.")
        return salvarEmDownloads(context, nome, "application/pdf") { saida ->
            origem.inputStream().use { it.copyTo(saida) }
        }
    }

    fun salvarTextoEmDownloads(context: Context, nome: String, mime: String, conteudo: String): Resultado<String> =
        salvarEmDownloads(context, nome, mime) { it.write(conteudo.toByteArray(Charsets.UTF_8)) }

    private fun salvarEmDownloads(
        context: Context,
        nome: String,
        mime: String,
        escrever: (OutputStream) -> Unit,
    ): Resultado<String> {
        val resolver = context.contentResolver
        val valores = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, nome)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$PASTA_DOWNLOADS")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, valores)
            ?: return Resultado.Erro("Não foi possível criar o arquivo em Downloads.")
        return try {
            resolver.openOutputStream(uri)?.use(escrever) ?: throw IOException("sem acesso ao arquivo")
            valores.clear()
            valores.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, valores, null, null)
            Resultado.Ok("Salvo em Downloads/$PASTA_DOWNLOADS/$nome")
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            Resultado.Erro("Falha ao salvar em Downloads: ${e.message}")
        }
    }

    /** UC09 / UC10 – e-mail para o cliente com o PDF anexado. */
    fun intentEmail(context: Context, arquivo: File, destinatario: String, assunto: String, corpo: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(destinatario))
            putExtra(Intent.EXTRA_SUBJECT, assunto)
            putExtra(Intent.EXTRA_TEXT, corpo)
            putExtra(Intent.EXTRA_STREAM, uriCompartilhavel(context, arquivo))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    fun intentAbrir(context: Context, arquivo: File): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriCompartilhavel(context, arquivo), "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    /** Renderiza as páginas do PDF em imagens para a pré-visualização dentro do app. */
    fun renderizarPaginas(arquivo: File, larguraPx: Int): List<Bitmap> {
        val descritor = ParcelFileDescriptor.open(arquivo, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(descritor)
        } catch (e: Exception) {
            descritor.close()
            throw e
        }
        return renderer.use { r ->
            (0 until r.pageCount).map { indice ->
                r.openPage(indice).use { pagina ->
                    val altura = (larguraPx.toFloat() * pagina.height / pagina.width).toInt()
                    val bitmap = Bitmap.createBitmap(larguraPx, altura, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    pagina.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }
    }
}
