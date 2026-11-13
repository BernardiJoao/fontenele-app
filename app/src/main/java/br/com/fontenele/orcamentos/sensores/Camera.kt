package br.com.fontenele.orcamentos.sensores

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Recurso físico: câmera.
 *
 * A foto é tirada pelo app de câmera do próprio aparelho (ActivityResultContracts.TakePicture),
 * que grava a imagem num arquivo da pasta interna do app (filesDir/fotos) recebido por um FileProvider.
 * Por isso não é preciso pedir a permissão CAMERA.
 */
object Camera {

    /** O aparelho tem alguma câmera (frontal ou traseira)? */
    fun disponivel(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    private fun pasta(context: Context): File = File(context.filesDir, "fotos").apply { mkdirs() }

    fun arquivo(context: Context, nome: String): File = File(pasta(context), nome)

    /** Arquivo onde o app de câmera grava a foto; só vira definitivo se a foto for confirmada. */
    fun arquivoTemporario(context: Context, contratoId: String): File = File(pasta(context), "tmp_$contratoId.jpg")

    /** Endereço (content://) que o app de câmera usa para gravar no arquivo da pasta interna. */
    fun uriPara(context: Context, arquivo: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", arquivo)

    /**
     * Torna a foto temporária definitiva com um nome novo (assim a tela recarrega a imagem)
     * e apaga a foto anterior do pedido, se houver. Devolve o nome do arquivo salvo.
     */
    fun guardar(context: Context, temporario: File, contratoId: String, fotoAnterior: String?): String {
        val nome = "pedido_${contratoId}_${System.currentTimeMillis()}.jpg"
        val destino = arquivo(context, nome)
        if (!temporario.renameTo(destino)) {
            temporario.copyTo(destino, overwrite = true)
            temporario.delete()
        }
        if (fotoAnterior != null && fotoAnterior != nome) arquivo(context, fotoAnterior).delete()
        return nome
    }

    /** Foto válida = arquivo existe e não está vazio (a câmera pode ser cancelada no meio). */
    fun fotoValida(arquivo: File): Boolean = arquivo.exists() && arquivo.length() > 0

    /**
     * Lê a foto já reduzida para caber em [ladoMaximo] pixels (economiza memória)
     * e girada conforme o EXIF, porque muitas câmeras salvam a imagem "deitada".
     */
    fun carregar(arquivo: File, ladoMaximo: Int): Bitmap? {
        if (!fotoValida(arquivo)) return null
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(arquivo.path, limites)
        if (limites.outWidth <= 0 || limites.outHeight <= 0) return null

        var amostra = 1
        while (maxOf(limites.outWidth, limites.outHeight) / (amostra * 2) >= ladoMaximo) amostra *= 2
        val imagem = BitmapFactory.decodeFile(arquivo.path, BitmapFactory.Options().apply { inSampleSize = amostra })
            ?: return null

        val graus = when (runCatching { ExifInterface(arquivo.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            .getOrDefault(ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (graus == 0f) return imagem
        val girada = Bitmap.createBitmap(imagem, 0, 0, imagem.width, imagem.height, Matrix().apply { postRotate(graus) }, true)
        if (girada != imagem) imagem.recycle()
        return girada
    }
}
