@file:OptIn(ExperimentalMaterial3Api::class)

package br.com.fontenele.orcamentos.ui.login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.fontenele.orcamentos.AppContainer
import br.com.fontenele.orcamentos.domain.Resultado
import br.com.fontenele.orcamentos.data.repo.SyncRepository
import br.com.fontenele.orcamentos.sensores.Biometria
import br.com.fontenele.orcamentos.sensores.acharActivity
import br.com.fontenele.orcamentos.ui.componentes.BotaoPrincipal
import br.com.fontenele.orcamentos.ui.componentes.BotaoContorno
import br.com.fontenele.orcamentos.ui.componentes.TextoBotao
import br.com.fontenele.orcamentos.ui.componentes.StatusServidor
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.fontenele.orcamentos.ui.componentes.CampoSenha
import br.com.fontenele.orcamentos.ui.componentes.LogoFontenele
import br.com.fontenele.orcamentos.ui.componentes.CampoTexto
import br.com.fontenele.orcamentos.ui.componentes.CartaoErro
import br.com.fontenele.orcamentos.ui.config.DialogoServidor
import kotlinx.coroutines.launch

/** UC01 – Fazer login (e-mail + senha, ou digital). */
@Composable
fun LoginTela(
    container: AppContainer,
    aoEntrar: () -> Unit,
    aoCriarConta: () -> Unit,
) {
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var email by rememberSaveable { mutableStateOf("") }
    var senha by rememberSaveable { mutableStateOf("") }
    var manterConectado by rememberSaveable { mutableStateOf(true) }
    var erro by remember { mutableStateOf<String?>(null) }
    var carregando by remember { mutableStateOf(false) }
    var procurandoNoServidor by remember { mutableStateOf(false) }
    var mostrarServidor by remember { mutableStateOf(false) }
    val estadoServidor by container.sincronizacao.estado.collectAsStateWithLifecycle()
    var biometriaPronta by remember {
        mutableStateOf(Biometria.disponivel(context) && container.auth.biometriaAtivadaParaAlguem())
    }

    // Fluxo 4 do UC01: limpar os campos preenchidos
    fun limpar() {
        email = ""
        senha = ""
        erro = null
    }

    fun entrar() {
        if (carregando) return
        carregando = true
        erro = null
        escopo.launch {
            var r = container.auth.login(email, senha, manterConectado)
            if (r is Resultado.Ok) {
                // A conta já estava no celular: renova o login no servidor em segundo plano (para sincronizar)
                container.sincronizacao.renovarLoginNoServidor(email, senha)
            } else if (email.isNotBlank() && senha.isNotBlank()) {
                // Conta criada (ou senha trocada) em outro celular: entra pelo servidor
                procurandoNoServidor = true
                r = when (val s = container.sincronizacao.entrarNoServidor(email, senha)) {
                    is SyncRepository.RespostaConta.Ok -> container.auth.entrarComContaDoServidor(s.usuario, manterConectado)
                    is SyncRepository.RespostaConta.Recusado -> Resultado.Erro(s.mensagem)
                    is SyncRepository.RespostaConta.SemConexao ->
                        Resultado.Erro((r as Resultado.Erro).mensagem + " (sem conexão com o servidor para procurar a conta)")
                }
                procurandoNoServidor = false
            }
            when (r) {
                is Resultado.Ok -> aoEntrar()
                is Resultado.Erro -> {
                    erro = r.mensagem
                    if (r.mensagem.startsWith("Senha")) senha = ""
                }
            }
            carregando = false
        }
    }

    fun entrarComDigital() {
        val activity = context.acharActivity()
        if (activity == null) {
            erro = "Não foi possível abrir o leitor biométrico."
            return
        }
        Biometria.autenticar(
            activity = activity,
            titulo = "Entrar no Fontenele",
            subtitulo = "Use a digital cadastrada neste aparelho",
            aoSucesso = {
                escopo.launch {
                    when (val r = container.auth.loginBiometrico()) {
                        is Resultado.Ok -> {
                            aoEntrar()
                        }
                        is Resultado.Erro -> {
                            erro = r.mensagem
                            biometriaPronta = false
                        }
                    }
                }
            },
            aoErro = { mensagem -> erro = mensagem },
        )
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            LogoFontenele(Modifier.height(120.dp))
            Spacer(Modifier.height(24.dp))

            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Entrar",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                CampoTexto(
                    valor = email,
                    aoMudar = { email = it },
                    rotulo = "E-mail",
                    teclado = KeyboardType.Email,
                )
                CampoSenha(valor = senha, aoMudar = { senha = it }, rotulo = "Senha")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { manterConectado = !manterConectado },
                ) {
                    Checkbox(checked = manterConectado, onCheckedChange = { manterConectado = it })
                    Text("Manter conectado")
                }
                CartaoErro(erro)
                if (procurandoNoServidor) {
                    Text(
                        "Procurando sua conta no servidor... Na primeira vez do dia pode levar até 1 minuto.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BotaoPrincipal(onClick = { entrar() }, enabled = !carregando, modifier = Modifier.fillMaxWidth()) {
                    if (carregando) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        TextoBotao("Entrar")
                    }
                }
                if (biometriaPronta) {
                    BotaoContorno(onClick = { entrarComDigital() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Fingerprint, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        TextoBotao("Entrar com digital")
                    }
                }
                // Fluxos 3 e 4 do UC01: cancelar e limpar
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { limpar() }) { Text("Limpar") }
                    TextButton(onClick = { context.acharActivity()?.finish() }) { Text("Cancelar") }
                }
                HorizontalDivider()
                TextButton(onClick = aoCriarConta) {
                    Text("Criar conta")
                }
                // Banco unificado: mostra se este celular está falando com o servidor
                StatusServidor(
                    estado = estadoServidor,
                    logado = false,
                    endereco = container.prefs.urlServidor,
                    acao = "Alterar",
                    aoClicarAcao = { mostrarServidor = true },
                )
            }
        }
    }

    if (mostrarServidor) {
        DialogoServidor(
            container = container,
            aoFechar = { mensagem ->
                mostrarServidor = false
                if (mensagem != null) escopo.launch { snackbar.showSnackbar(mensagem) }
            },
        )
    }
}
