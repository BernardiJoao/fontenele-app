package br.com.fontenele.orcamentos.sensores

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Sensor físico 3 – LEITOR BIOMÉTRICO (digital/rosto) para entrar sem digitar a senha. */
object Biometria {
    private const val AUTENTICADORES = BiometricManager.Authenticators.BIOMETRIC_WEAK

    fun disponivel(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTENTICADORES) == BiometricManager.BIOMETRIC_SUCCESS

    fun autenticar(
        activity: FragmentActivity,
        titulo: String,
        subtitulo: String,
        aoSucesso: () -> Unit,
        aoErro: (String) -> Unit,
    ) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = aoSucesso()

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val cancelado = errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                if (!cancelado) aoErro(errString.toString())
            }
        }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(titulo)
            .setSubtitle(subtitulo)
            .setNegativeButtonText("Cancelar")
            .setAllowedAuthenticators(AUTENTICADORES)
            .build()
        prompt.authenticate(info)
    }
}

/** Encontra a FragmentActivity a partir do Context do Compose (necessária para o BiometricPrompt). */
fun Context.acharActivity(): FragmentActivity? {
    var atual: Context? = this
    while (atual is ContextWrapper) {
        if (atual is FragmentActivity) return atual
        atual = atual.baseContext
    }
    return null
}
