package br.com.fontenele.orcamentos.data.prefs

import android.content.Context
import br.com.fontenele.orcamentos.BuildConfig
import androidx.core.content.edit

/** Armazenamento interno com SharedPreferences: sessão, biometria e configurações. */
class Preferencias(context: Context) {
    private val prefs = context.getSharedPreferences("fontenele_prefs", Context.MODE_PRIVATE)

    /** Usuário com sessão salva ("manter conectado"). */
    var usuarioLogadoId: String?
        get() = prefs.getString(CHAVE_USUARIO, null)
        set(valor) = prefs.edit { putString(CHAVE_USUARIO, valor) }

    /** Usuário que ativou o login por digital neste aparelho. */
    var usuarioBiometriaId: String?
        get() = prefs.getString(CHAVE_BIOMETRIA, null)
        set(valor) = prefs.edit { putString(CHAVE_BIOMETRIA, valor) }

    var urlServidor: String
        get() = prefs.getString(CHAVE_URL, URL_PADRAO) ?: URL_PADRAO
        set(valor) = prefs.edit { putString(CHAVE_URL, valor.trim()) }

    /** Token de login no servidor (recebido em /api/entrar ou /api/cadastro). Sem ele o servidor não sincroniza. */
    var tokenServidor: String?
        get() = prefs.getString(CHAVE_TOKEN, null)
        set(valor) = prefs.edit { putString(CHAVE_TOKEN, valor) }

    var ultimaSincronizacao: Long
        get() = prefs.getLong(CHAVE_ULTIMA_SYNC, 0L)
        set(valor) = prefs.edit { putLong(CHAVE_ULTIMA_SYNC, valor) }

    companion object {
        /**
         * Endereço usado por quem acabou de instalar o app. Vem do app/build.gradle.kts (URL_SERVIDOR):
         * troque lá pelo endereço do servidor da editora para todos os celulares já saírem conectados.
         */
        val URL_PADRAO: String = BuildConfig.URL_SERVIDOR
        private const val CHAVE_USUARIO = "usuario_logado"
        private const val CHAVE_BIOMETRIA = "usuario_biometria"
        private const val CHAVE_URL = "url_servidor"
        private const val CHAVE_ULTIMA_SYNC = "ultima_sync"
        private const val CHAVE_TOKEN = "token_servidor"
    }
}
