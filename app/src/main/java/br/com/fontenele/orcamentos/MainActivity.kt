package br.com.fontenele.orcamentos

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import br.com.fontenele.orcamentos.ui.navegacao.AppNavegacao
import br.com.fontenele.orcamentos.ui.theme.TemaFontenele

/** FragmentActivity (e não ComponentActivity) porque o BiometricPrompt precisa dela. */
class MainActivity : FragmentActivity() {
    private val container get() = (application as FonteneleApp).container

    /** App na tela: sincroniza com o servidor agora e a cada 30 segundos. */
    override fun onStart() {
        super.onStart()
        container.sincronizacao.iniciar()
    }

    /** App fora da tela: para o ciclo (as gravações ainda pendentes terminam de ser enviadas). */
    override fun onStop() {
        container.sincronizacao.parar()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TemaFontenele {
                AppNavegacao(container)
            }
        }
    }
}
