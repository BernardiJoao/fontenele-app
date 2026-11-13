@file:OptIn(ExperimentalMaterial3Api::class)

package br.com.fontenele.orcamentos.ui.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.fontenele.orcamentos.AppContainer
import br.com.fontenele.orcamentos.data.local.UsuarioEntity
import br.com.fontenele.orcamentos.domain.Formatos
import br.com.fontenele.orcamentos.domain.Resultado
import br.com.fontenele.orcamentos.data.repo.SyncRepository
import br.com.fontenele.orcamentos.data.repo.EstadoSync
import br.com.fontenele.orcamentos.domain.TipoDocumento
import br.com.fontenele.orcamentos.pdf.Arquivos
import br.com.fontenele.orcamentos.sensores.Biometria
import br.com.fontenele.orcamentos.sensores.acharActivity
import br.com.fontenele.orcamentos.domain.Perfil
import br.com.fontenele.orcamentos.ui.componentes.BotaoPrincipal
import br.com.fontenele.orcamentos.ui.componentes.BotaoContorno
import br.com.fontenele.orcamentos.ui.componentes.TextoBotao
import br.com.fontenele.orcamentos.ui.componentes.BarraSuperior
import br.com.fontenele.orcamentos.ui.componentes.CampoTexto
import br.com.fontenele.orcamentos.ui.componentes.TituloSecao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Configurações: conta, sensores, servidor (armazenamento externo), backup e registros de e-mail. */
@Composable
fun ConfigTela(
    container: AppContainer,
    usuario: UsuarioEntity,
    aoVoltar: () -> Unit,
    aoSair: () -> Unit,
) {
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val logs by remember { container.editora.observarLogsEmail() }.collectAsStateWithLifecycle(emptyList())

    val temBiometria = remember { Biometria.disponivel(context) }
    var biometriaAtiva by remember { mutableStateOf(container.auth.biometriaAtivadaPara(usuario.id)) }
    var url by remember { mutableStateOf(container.prefs.urlServidor) }
    var ultimaSync by remember { mutableLongStateOf(container.prefs.ultimaSincronizacao) }
    var ocupado by remember { mutableStateOf(false) }

    fun avisar(msg: String) {
        escopo.launch { snackbar.showSnackbar(msg) }
    }

    fun alternarBiometria(ativar: Boolean) {
        if (!ativar) {
            container.auth.definirBiometria(usuario.id, false)
            biometriaAtiva = false
            avisar("Login por digital desativado.")
            return
        }
        val activity = context.acharActivity() ?: return
        Biometria.autenticar(
            activity = activity,
            titulo = "Ativar login por digital",
            subtitulo = "Confirme sua digital para ${usuario.email}",
            aoSucesso = {
                container.auth.definirBiometria(usuario.id, true)
                biometriaAtiva = true
                avisar("Pronto! Na próxima vez você pode entrar com a digital.")
            },
            aoErro = { avisar(it) },
        )
    }

    fun sincronizar() {
        container.prefs.urlServidor = url
        ocupado = true
        escopo.launch {
            when (val r = container.sincronizacao.sincronizarAgora()) {
                is Resultado.Ok -> {
                    ultimaSync = container.prefs.ultimaSincronizacao
                    avisar(r.valor)
                }
                is Resultado.Erro -> avisar(r.mensagem)
            }
            ocupado = false
        }
    }

    fun testar() {
        container.prefs.urlServidor = url
        ocupado = true
        escopo.launch {
            when (val r = container.sync.testarConexao(url)) {
                is Resultado.Ok -> avisar(r.valor)
                is Resultado.Erro -> avisar(r.mensagem)
            }
            ocupado = false
        }
    }

    fun exportarBackup() {
        escopo.launch {
            val json = container.sync.backupJson()
            val nome = "fontenele_backup_${Formatos.carimboArquivo(System.currentTimeMillis())}.json"
            val r = withContext(Dispatchers.IO) {
                Arquivos.salvarTextoEmDownloads(context, nome, "application/json", json)
            }
            when (r) {
                is Resultado.Ok -> avisar(r.valor)
                is Resultado.Erro -> avisar(r.mensagem)
            }
        }
    }

    Scaffold(
        topBar = { BarraSuperior("Configurações", aoVoltar = aoVoltar) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(usuario.nome, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${usuario.email} · ${Perfil.de(usuario.perfil).rotulo}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { TituloSecao("Sensores") }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Filled.Fingerprint, contentDescription = null) },
                    headlineContent = { Text("Entrar com digital") },
                    supportingContent = {
                        Text(
                            if (temBiometria) "Usa o leitor biométrico no lugar da senha"
                            else "Sem leitor biométrico ou nenhuma digital cadastrada no aparelho"
                        )
                    },
                    trailingContent = {
                        Switch(checked = biometriaAtiva, enabled = temBiometria, onCheckedChange = { alternarBiometria(it) })
                    },
                )
            }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                    headlineContent = { Text("Câmera") },
                    supportingContent = { Text("Foto do pedido pronto, no cadastro do cliente") },
                )
            }

            item { TituloSecao("Servidor") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampoTexto(
                        valor = url,
                        aoMudar = { url = it },
                        rotulo = "Endereço do backend Spring Boot",
                        teclado = KeyboardType.Uri,
                        icone = Icons.Filled.Dns,
                        ajuda = "Emulador: http://10.0.2.2:8080 · Celular: http://IP-do-computador:8080",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BotaoContorno(onClick = { testar() }, enabled = !ocupado, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.NetworkCheck, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            TextoBotao("Testar")
                        }
                        BotaoPrincipal(onClick = { sincronizar() }, enabled = !ocupado, modifier = Modifier.weight(1f)) {
                            if (ocupado) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                            } else {
                                Icon(Icons.Filled.Sync, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                TextoBotao("Sincronizar")
                            }
                        }
                    }
                    Text(
                        if (ultimaSync > 0) "Última sincronização: ${Formatos.dataHora(ultimaSync)}" else "Ainda não sincronizado",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BotaoContorno(onClick = { exportarBackup() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.FileDownload, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        TextoBotao("Exportar backup (JSON) para Downloads")
                    }
                }
            }

            item { TituloSecao("E-mails enviados") }
            if (logs.isEmpty()) {
                item {
                    Text(
                        "Nenhum envio registrado ainda.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(logs, key = { it.id }) { log ->
                ListItem(
                    leadingContent = {
                        Icon(
                            if (log.sucesso) Icons.Filled.CheckCircle else Icons.Filled.Error,
                            contentDescription = if (log.sucesso) "Sucesso" else "Falha",
                            tint = if (log.sucesso) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                        )
                    },
                    headlineContent = {
                        Text("${TipoDocumento.de(log.documentoTipo).rotulo} nº ${Formatos.numeroDocumento(log.numeroDocumento)} → ${log.destinatario}")
                    },
                    supportingContent = { Text("${log.mensagem}\n${Formatos.dataHora(log.data)} · ${log.usuarioNome}") },
                )
            }

            item {
                BotaoPrincipal(
                    onClick = aoSair,
                    cor = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    TextoBotao("Sair da conta")
                }
            }
        }
    }
}

/** Diálogo da tela de login e do cadastro: muda o endereço do servidor e sincroniza na hora. */
@Composable
fun DialogoServidor(container: AppContainer, aoFechar: (String?) -> Unit) {
    val escopo = rememberCoroutineScope()
    var url by remember { mutableStateOf(container.prefs.urlServidor) }
    var ocupado by remember { mutableStateOf(false) }
    var mensagem by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!ocupado) aoFechar(null) },
        icon = { Icon(Icons.Filled.Sync, contentDescription = null) },
        title = { Text("Servidor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Todos os celulares precisam usar o mesmo servidor para compartilhar contas e dados. " +
                        "Servidor na internet: o endereço https://... do Render. " +
                        "Servidor no computador: http://IP-DO-COMPUTADOR:8080 (mesmo Wi-Fi) ou http://10.0.2.2:8080 no emulador.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                CampoTexto(valor = url, aoMudar = { url = it }, rotulo = "Endereço do servidor", teclado = KeyboardType.Uri)
                if (ocupado) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Sincronizando...")
                    }
                }
                mensagem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !ocupado,
                onClick = {
                    // O login vale para um servidor só: trocou o endereço, precisa entrar de novo
                    if (SyncRepository.normalizarUrl(url) != SyncRepository.normalizarUrl(container.prefs.urlServidor)) {
                        container.prefs.tokenServidor = null
                    }
                    container.prefs.urlServidor = url
                    ocupado = true
                    mensagem = null
                    escopo.launch {
                        when (val r = container.sincronizacao.sincronizarAgora()) {
                            is Resultado.Ok -> aoFechar(r.valor)
                            is Resultado.Erro ->
                                if (container.sincronizacao.estado.value is EstadoSync.PrecisaEntrar) {
                                    aoFechar("Servidor encontrado. Entre com seu e-mail e senha para sincronizar.")
                                } else {
                                    mensagem = r.mensagem
                                }
                        }
                        ocupado = false
                    }
                },
            ) { Text("Sincronizar") }
        },
        dismissButton = {
            TextButton(enabled = !ocupado, onClick = { aoFechar(null) }) { Text("Fechar") }
        },
    )
}
