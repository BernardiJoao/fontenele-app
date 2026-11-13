@file:OptIn(ExperimentalMaterial3Api::class)

package br.com.fontenele.orcamentos.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.fontenele.orcamentos.AppContainer
import br.com.fontenele.orcamentos.domain.Perfil
import br.com.fontenele.orcamentos.data.repo.SyncRepository
import br.com.fontenele.orcamentos.domain.Resultado
import br.com.fontenele.orcamentos.ui.componentes.BotaoPrincipal
import br.com.fontenele.orcamentos.ui.componentes.TextoBotao
import br.com.fontenele.orcamentos.ui.config.DialogoServidor
import br.com.fontenele.orcamentos.ui.componentes.StatusServidor
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import br.com.fontenele.orcamentos.ui.componentes.BarraSuperior
import br.com.fontenele.orcamentos.ui.componentes.CampoSenha
import br.com.fontenele.orcamentos.ui.componentes.CampoTexto
import br.com.fontenele.orcamentos.ui.componentes.CartaoErro
import br.com.fontenele.orcamentos.ui.componentes.TituloSecao
import kotlinx.coroutines.launch

/**
 * Registro de usuários com separação de perfil.
 *  - modoAdmin = false: cadastro público (1ª conta do sistema = Administrador; demais = Funcionário).
 *    Antes de criar, sincroniza com o servidor: assim o celular conhece as contas de todos os aparelhos.
 *  - modoAdmin = true: o Administrador cria a conta e escolhe o perfil.
 */
@Composable
fun CadastroTela(
    container: AppContainer,
    modoAdmin: Boolean,
    aoConcluir: (String) -> Unit,
    aoVoltar: () -> Unit,
) {
    val escopo = rememberCoroutineScope()
    var nome by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var senha by rememberSaveable { mutableStateOf("") }
    var confirmacao by rememberSaveable { mutableStateOf("") }
    var perfil by rememberSaveable { mutableStateOf(Perfil.FUNCIONARIO) }
    var erro by remember { mutableStateOf<String?>(null) }
    var salvando by remember { mutableStateOf(false) }
    var primeiraConta by remember { mutableStateOf<Boolean?>(null) }
    var semServidor by remember { mutableStateOf<String?>(null) }
    var criarSoNoAparelho by remember { mutableStateOf(false) }
    val estadoServidor by container.sincronizacao.estado.collectAsStateWithLifecycle()
    var mostrarServidor by remember { mutableStateOf(false) }

    // Pergunta ao servidor se já existe alguma conta (a primeira do sistema vira Administrador)
    suspend fun verificarPrimeiraConta() {
        val contas = container.sincronizacao.contasNoServidor()
        primeiraConta = if (contas != null) contas == 0L else !container.auth.existeUsuario()
    }
    LaunchedEffect(Unit) { verificarPrimeiraConta() }

    fun salvar() {
        if (salvando) return
        salvando = true
        erro = null
        escopo.launch {
            if (!modoAdmin && !criarSoNoAparelho) {
                // Cadastro público é feito no servidor: ele decide o perfil e a conta vale em todos os celulares
                val invalido = container.auth.validarCadastro(nome, email, senha, confirmacao)
                if (invalido != null) {
                    erro = invalido
                    salvando = false
                    return@launch
                }
                when (val s = container.sincronizacao.cadastrarNoServidor(nome, email, senha)) {
                    is SyncRepository.RespostaConta.Ok -> {
                        container.auth.entrarComContaDoServidor(s.usuario, manterConectado = true)
                        aoConcluir("Conta de ${s.usuario.nome} criada com perfil ${Perfil.de(s.usuario.perfil).rotulo}.")
                    }
                    is SyncRepository.RespostaConta.Recusado -> erro = s.mensagem
                    // Sem servidor a conta não é compartilhada: pergunta antes de criar só aqui
                    is SyncRepository.RespostaConta.SemConexao -> semServidor = s.mensagem
                }
                salvando = false
                return@launch
            }
            val r = if (modoAdmin) {
                container.auth.cadastrar(nome, email, senha, confirmacao, perfil)
            } else {
                container.auth.cadastrarEEntrar(nome, email, senha, confirmacao)
            }
            when (r) {
                is Resultado.Ok -> aoConcluir(
                    "Conta de ${r.valor.nome} criada com perfil ${Perfil.de(r.valor.perfil).rotulo}."
                )
                is Resultado.Erro -> erro = r.mensagem
            }
            salvando = false
        }
    }

    Scaffold(
        topBar = { BarraSuperior(if (modoAdmin) "Novo usuário" else "Criar conta", aoVoltar = aoVoltar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TituloSecao("Dados de acesso")
            CampoTexto(nome, { nome = it }, "Nome", obrigatorio = true, icone = Icons.Filled.Person)
            CampoTexto(email, { email = it }, "E-mail", obrigatorio = true, teclado = KeyboardType.Email, icone = Icons.Filled.Email)
            CampoSenha(senha, { senha = it }, "Senha* (mínimo 6 caracteres)")
            CampoSenha(confirmacao, { confirmacao = it }, "Confirmar senha*")

            TituloSecao("Perfil")
            if (modoAdmin && primeiraConta == false) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Perfil.entries.forEach { p ->
                        FilterChip(
                            selected = perfil == p,
                            onClick = { perfil = p },
                            label = { Text(p.rotulo) },
                            leadingIcon = {
                                Icon(
                                    if (p == Perfil.ADMIN) Icons.Filled.AdminPanelSettings else Icons.Filled.Work,
                                    contentDescription = null,
                                )
                            },
                        )
                    }
                }
                Text(
                    if (perfil == Perfil.ADMIN) {
                        "Altera preços e gerencia usuários."
                    } else {
                        "Gera orçamentos e contratos."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    when (primeiraConta) {
                        true -> "Primeira conta do sistema: perfil Administrador."
                        false -> "Perfil: Funcionário. Um Administrador pode alterar depois."
                        null -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            CartaoErro(erro)
            BotaoPrincipal(onClick = { salvar() }, enabled = !salvando, modifier = Modifier.fillMaxWidth()) {
                if (salvando) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                } else {
                    TextoBotao(if (modoAdmin) "Cadastrar usuário" else "Criar conta e entrar")
                }
            }
            if (!modoAdmin) {
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

    semServidor?.let { mensagem ->
        AlertDialog(
            onDismissRequest = { semServidor = null },
            title = { Text("Sem conexão com o servidor") },
            text = {
                Text(
                    "$mensagem\n\nSe continuar, a conta fica só neste celular: ela e os dados " +
                        "não aparecem nos outros aparelhos até o servidor ser configurado."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    semServidor = null
                    mostrarServidor = true
                }) { Text("Configurar servidor") }
            },
            dismissButton = {
                TextButton(onClick = {
                    semServidor = null
                    criarSoNoAparelho = true
                    salvar()
                }) { Text("Criar só neste celular") }
            },
        )
    }

    if (mostrarServidor) {
        DialogoServidor(
            container = container,
            aoFechar = {
                mostrarServidor = false
                escopo.launch { verificarPrimeiraConta() }
            },
        )
    }
}
