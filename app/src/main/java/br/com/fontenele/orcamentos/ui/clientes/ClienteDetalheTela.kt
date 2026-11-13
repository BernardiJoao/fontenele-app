@file:OptIn(ExperimentalMaterial3Api::class)

package br.com.fontenele.orcamentos.ui.clientes

import androidx.compose.foundation.clickable
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import br.com.fontenele.orcamentos.data.local.ContratoEntity
import br.com.fontenele.orcamentos.data.local.UsuarioEntity
import br.com.fontenele.orcamentos.domain.Resultado
import br.com.fontenele.orcamentos.domain.StatusContrato
import br.com.fontenele.orcamentos.sensores.Camera
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.fontenele.orcamentos.AppContainer
import br.com.fontenele.orcamentos.data.local.ClienteEntity
import br.com.fontenele.orcamentos.data.local.HistoricoEntity
import br.com.fontenele.orcamentos.domain.Formatos
import br.com.fontenele.orcamentos.domain.TipoDocumento
import br.com.fontenele.orcamentos.domain.TipoHistorico
import br.com.fontenele.orcamentos.domain.Validacao
import br.com.fontenele.orcamentos.ui.componentes.BotaoContorno
import br.com.fontenele.orcamentos.ui.componentes.BotaoPrincipal
import br.com.fontenele.orcamentos.ui.componentes.TextoBotao
import br.com.fontenele.orcamentos.ui.componentes.BarraSuperior
import br.com.fontenele.orcamentos.ui.componentes.MensagemVazia
import br.com.fontenele.orcamentos.ui.componentes.TelaCarregando
import br.com.fontenele.orcamentos.ui.componentes.TituloSecao
import br.com.fontenele.orcamentos.ui.componentes.itemDeCaixa
import br.com.fontenele.orcamentos.ui.navegacao.Rotas

/**
 * Detalhe do cliente (UC02): dados, pedidos antigos e histórico.
 * Em cada pedido com contrato fechado dá para tirar a foto de como ele ficou pronto (câmera).
 */
@Composable
fun ClienteDetalheTela(
    container: AppContainer,
    usuario: UsuarioEntity,
    clienteId: String,
    aoVoltar: () -> Unit,
    navegar: (String) -> Unit,
) {
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val cliente by remember(clienteId) { container.editora.observarCliente(clienteId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val pedidos by remember(clienteId) { container.editora.observarPedidosDoCliente(clienteId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val historico by remember(clienteId) { container.editora.observarHistorico(clienteId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    fun avisar(msg: String) {
        escopo.launch { snackbar.showSnackbar(msg) }
    }

    // Câmera: guarda qual pedido está sendo fotografado (sobrevive se o Android recriar a tela)
    var fotografando by rememberSaveable { mutableStateOf<String?>(null) }
    var fotoAmpliada by remember { mutableStateOf<File?>(null) }
    val tirarFoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val contratoId = fotografando ?: return@rememberLauncherForActivityResult
        fotografando = null
        val temporario = Camera.arquivoTemporario(context, contratoId)
        if (!ok || !Camera.fotoValida(temporario)) {
            temporario.delete()
            return@rememberLauncherForActivityResult
        }
        escopo.launch {
            val anterior = pedidos.firstOrNull { it.id == contratoId }?.fotoPronto
            val nome = withContext(Dispatchers.IO) { Camera.guardar(context, temporario, contratoId, anterior) }
            when (val r = container.editora.registrarFotoPedidoPronto(contratoId, nome, usuario)) {
                is Resultado.Ok -> avisar("Foto salva. Pedido marcado como pronto.")
                is Resultado.Erro -> avisar(r.mensagem)
            }
        }
    }

    fun abrirCamera(pedido: ContratoEntity) {
        if (!Camera.disponivel(context)) {
            avisar("Este aparelho não tem câmera.")
            return
        }
        val temporario = Camera.arquivoTemporario(context, pedido.id)
        fotografando = pedido.id
        try {
            tirarFoto.launch(Camera.uriPara(context, temporario))
        } catch (e: ActivityNotFoundException) {
            fotografando = null
            avisar("Nenhum app de câmera encontrado no aparelho.")
        }
    }

    val c = cliente
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            BarraSuperior(c?.nome ?: "Cliente", aoVoltar = aoVoltar) {
                IconButton(onClick = { navegar(Rotas.clienteForm(clienteId)) }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Editar cliente")
                }
            }
        },
    ) { padding ->
        if (c == null) {
            TelaCarregando()
        } else LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        ) {
            item { CartaoDados(c) }
            item {
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BotaoPrincipal(
                        onClick = { navegar(Rotas.orcamento(clienteId = c.id)) },
                        modifier = Modifier.weight(1f),
                    ) { TextoBotao("Novo orçamento") }
                    BotaoContorno(
                        onClick = { navegar(Rotas.contrato(clienteId = c.id)) },
                        modifier = Modifier.weight(1f),
                    ) { TextoBotao("Novo contrato") }
                }
            }
            item { TituloSecao("Pedidos") }
            if (pedidos.isEmpty()) {
                item { MensagemVazia(Icons.Filled.Description, "Nenhum pedido ainda.") }
            }
            itemsIndexed(pedidos, key = { _, p -> "p" + p.id }) { i, p ->
                ItemPedido(
                    pedido = p,
                    ultimo = i == pedidos.lastIndex,
                    foto = p.fotoPronto?.let { Camera.arquivo(context, it) },
                    aoTirarFoto = { abrirCamera(p) },
                    aoAmpliar = { fotoAmpliada = it },
                    aoVerContrato = { navegar(Rotas.documento(TipoDocumento.CONTRATO, p.id)) },
                )
            }
            item { TituloSecao("Histórico") }
            if (historico.isEmpty()) {
                item { MensagemVazia(Icons.Filled.History, "Nenhum registro no histórico.") }
            }
            itemsIndexed(historico, key = { _, h -> h.id }) { i, h ->
                ItemHistorico(h, ultimo = i == historico.lastIndex) { rota -> navegar(rota) }
            }
        }
    }

    fotoAmpliada?.let { arquivo ->
        Dialog(onDismissRequest = { fotoAmpliada = null }) {
            FotoDoPedido(arquivo, ladoMaximo = 1600, modifier = Modifier.fillMaxWidth().clickable { fotoAmpliada = null })
        }
    }
}

/** Um pedido (contrato) do cliente: número, valor, situação e a foto de quando ficou pronto. */
@Composable
private fun ItemPedido(
    pedido: ContratoEntity,
    ultimo: Boolean,
    foto: File?,
    aoTirarFoto: () -> Unit,
    aoAmpliar: (File) -> Unit,
    aoVerContrato: () -> Unit,
) {
    val fechado = pedido.status == StatusContrato.FECHADO.name
    val situacao = when {
        pedido.prontoEm != null -> "Pronto em ${Formatos.data(pedido.prontoEm)}"
        fechado -> "Fechado em ${pedido.fechadoEm?.let { Formatos.data(it) } ?: "—"} · em produção"
        else -> "Contrato em aberto"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .itemDeCaixa(ultimo)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("Pedido nº ${Formatos.numeroDocumento(pedido.numero)}", style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(
                        pedido.tiragem?.let { "${Formatos.numero(it.toDouble(), 0)} ex." },
                        Formatos.data(pedido.criadoEm),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    situacao,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (pedido.prontoEm != null) FontWeight.Bold else FontWeight.Normal,
                )
            }
            Text(Formatos.moeda(pedido.valor), style = MaterialTheme.typography.titleMedium)
        }
        if (foto != null) {
            if (Camera.fotoValida(foto)) {
                FotoDoPedido(
                    foto,
                    ladoMaximo = 900,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clickable { aoAmpliar(foto) },
                    recortar = true,
                )
            } else {
                Text(
                    "A foto deste pedido foi tirada em outro aparelho.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (fechado && pedido.prontoEm == null) {
            // Pedido em produção: o botão principal ocupa a linha toda
            BotaoPrincipal(onClick = aoTirarFoto, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                TextoBotao("Pedido pronto: tirar foto")
            }
            TextButton(onClick = aoVerContrato) { Text("Ver contrato") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (fechado) {
                    BotaoContorno(onClick = aoTirarFoto, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        TextoBotao("Tirar outra foto")
                    }
                } else {
                    Text(
                        "Registre o fechamento do contrato para marcar o pedido como pronto.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
                TextButton(onClick = aoVerContrato) { Text("Ver contrato") }
            }
        }
    }
}

/** Mostra a foto salva na pasta interna, carregada fora da thread principal. */
@Composable
private fun FotoDoPedido(arquivo: File, ladoMaximo: Int, modifier: Modifier = Modifier, recortar: Boolean = false) {
    val imagem by produceState<ImageBitmap?>(initialValue = null, arquivo.path) {
        value = withContext(Dispatchers.IO) { Camera.carregar(arquivo, ladoMaximo)?.asImageBitmap() }
    }
    val i = imagem
    if (i == null) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceContainer))
    } else {
        Image(
            bitmap = i,
            contentDescription = "Foto do pedido pronto",
            contentScale = if (recortar) ContentScale.Crop else ContentScale.Fit,
            modifier = modifier,
        )
    }
}

@Composable
private fun CartaoDados(c: ClienteEntity) {
    Column(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Linha("E-mail", c.email)
            Linha("CPF", if (c.cpf.isBlank()) "" else Validacao.formatarCpf(c.cpf))
            Linha("Telefone", if (c.telefone.isBlank()) "" else Validacao.formatarTelefone(c.telefone))
            val endereco = listOf(
                listOf(c.rua, c.numero).filter { it.isNotBlank() }.joinToString(", "),
                listOf(c.cidade, c.estado).filter { it.isNotBlank() }.joinToString("/"),
                if (c.cep.isBlank()) "" else "CEP ${Validacao.formatarCep(c.cep)}",
            ).filter { it.isNotBlank() }.joinToString(" – ")
            Linha("Endereço", endereco)
            Linha("Cliente desde", Formatos.data(c.criadoEm))
            Linha(
                "Pedidos",
                "${c.orcamentosPedidos} ${if (c.orcamentosPedidos == 1) "orçamento" else "orçamentos"} · " +
                    "${c.contratosFechados} ${if (c.contratosFechados == 1) "contrato fechado" else "contratos fechados"}",
            )
        }
    }
}

@Composable
private fun Linha(rotulo: String, valor: String) {
    Column {
        Text(rotulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun iconeDo(tipo: TipoHistorico): ImageVector = when (tipo) {
    TipoHistorico.CADASTRO -> Icons.Filled.PersonAdd
    TipoHistorico.ATUALIZACAO -> Icons.Filled.Update
    TipoHistorico.ORCAMENTO, TipoHistorico.ORCAMENTO_EDITADO -> Icons.Filled.Receipt
    TipoHistorico.CONTRATO, TipoHistorico.CONTRATO_EDITADO -> Icons.Filled.Description
    TipoHistorico.FECHAMENTO -> Icons.Filled.Verified
    TipoHistorico.PRONTO -> Icons.Filled.PhotoCamera
    TipoHistorico.EMAIL -> Icons.Filled.Email
}

@Composable
private fun ItemHistorico(h: HistoricoEntity, ultimo: Boolean, abrir: (String) -> Unit) {
    val tipo = TipoHistorico.de(h.tipo)
    val rota = h.referenciaId?.let { ref ->
        when (tipo) {
            TipoHistorico.ORCAMENTO, TipoHistorico.ORCAMENTO_EDITADO -> Rotas.documento(TipoDocumento.ORCAMENTO, ref)
            TipoHistorico.CONTRATO, TipoHistorico.CONTRATO_EDITADO, TipoHistorico.FECHAMENTO, TipoHistorico.PRONTO ->
                Rotas.documento(TipoDocumento.CONTRATO, ref)
            else -> null
        }
    }
    val modificador = Modifier
        .itemDeCaixa(ultimo)
        .then(if (rota != null) Modifier.clickable { abrir(rota) } else Modifier)
    ListItem(
        modifier = modificador,
        leadingContent = { Icon(iconeDo(tipo), contentDescription = null, tint = MaterialTheme.colorScheme.outline) },
        overlineContent = { Text("${tipo.rotulo} · ${Formatos.dataHora(h.data)}") },
        headlineContent = { Text(h.descricao) },
        supportingContent = {
            Text(
                listOfNotNull(h.valor?.let { Formatos.moeda(it) }, "por ${h.usuarioNome}").joinToString(" · ")
            )
        },
    )
}
