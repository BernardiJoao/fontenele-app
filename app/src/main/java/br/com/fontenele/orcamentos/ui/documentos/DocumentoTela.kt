@file:OptIn(ExperimentalMaterial3Api::class)

package br.com.fontenele.orcamentos.ui.documentos

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.fontenele.orcamentos.AppContainer
import br.com.fontenele.orcamentos.data.local.ContratoEntity
import br.com.fontenele.orcamentos.data.local.OrcamentoEntity
import br.com.fontenele.orcamentos.data.local.UsuarioEntity
import br.com.fontenele.orcamentos.domain.Formatos
import br.com.fontenele.orcamentos.domain.Resultado
import br.com.fontenele.orcamentos.domain.StatusContrato
import br.com.fontenele.orcamentos.domain.TipoDocumento
import br.com.fontenele.orcamentos.domain.Validacao
import br.com.fontenele.orcamentos.pdf.Arquivos
import br.com.fontenele.orcamentos.ui.componentes.BotaoContorno
import br.com.fontenele.orcamentos.ui.componentes.BotaoPrincipal
import br.com.fontenele.orcamentos.ui.componentes.TextoBotao
import br.com.fontenele.orcamentos.ui.componentes.BarraSuperior
import br.com.fontenele.orcamentos.ui.componentes.CartaoErro
import br.com.fontenele.orcamentos.ui.componentes.Etiqueta
import br.com.fontenele.orcamentos.ui.navegacao.Rotas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Tela do documento gerado:
 *  - pré-visualização do PDF (UC06/UC07);
 *  - UC08 Baixar PDF (copia para Downloads/Fontenele);
 *  - UC09/UC10 Enviar por e-mail (com registro de sucesso/falha);
 *  - UC18 Registrar fechamento (contratos em aberto).
 */
@Composable
fun DocumentoTela(
    container: AppContainer,
    usuario: UsuarioEntity,
    tipo: TipoDocumento,
    id: String,
    aoVoltar: () -> Unit,
    navegar: (String) -> Unit,
) {
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var orcamento by remember { mutableStateOf<OrcamentoEntity?>(null) }
    var contrato by remember { mutableStateOf<ContratoEntity?>(null) }
    var arquivo by remember { mutableStateOf<File?>(null) }
    var paginas by remember { mutableStateOf<List<ImageBitmap>>(emptyList()) }
    var erro by remember { mutableStateOf<String?>(null) }
    var versao by remember { mutableIntStateOf(0) }
    val larguraPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
        .coerceIn(600, 1400)

    LaunchedEffect(id, versao) {
        try {
            val pdf = container.pdf
            val gerado = when (tipo) {
                TipoDocumento.ORCAMENTO -> {
                    val o = container.editora.orcamento(id) ?: error("Orçamento não encontrado.")
                    orcamento = o
                    pdf.arquivo(tipo, id).takeIf { it.exists() && versao == 0 } ?: pdf.gerarOrcamento(o)
                }
                TipoDocumento.CONTRATO -> {
                    val c = container.editora.contrato(id) ?: error("Contrato não encontrado.")
                    contrato = c
                    pdf.arquivo(tipo, id).takeIf { it.exists() && versao == 0 } ?: pdf.gerarContrato(c)
                }
            }
            arquivo = gerado
            paginas = withContext(Dispatchers.IO) {
                Arquivos.renderizarPaginas(gerado, larguraPx).map { it.asImageBitmap() }
            }
        } catch (e: Exception) {
            erro = e.message ?: "Não foi possível abrir o documento."
        }
    }

    val numero = orcamento?.numero ?: contrato?.numero
    val cliente = orcamento?.clienteNome ?: contrato?.nome ?: ""
    val email = orcamento?.clienteEmail ?: contrato?.email ?: ""
    val clienteId = orcamento?.clienteId ?: contrato?.clienteId ?: ""
    val valor = orcamento?.total ?: contrato?.valor
    val linhasOrcamento = remember(orcamento) { orcamento?.let { container.editora.resultadoDoOrcamento(it)?.linhas }.orEmpty() }
    val titulo = "${tipo.rotulo} nº ${numero?.let { Formatos.numeroDocumento(it) } ?: "…"}"

    fun avisar(msg: String) {
        escopo.launch { snackbar.showSnackbar(msg) }
    }

    // UC08 – Baixar PDF gerado
    fun baixar() {
        val f = arquivo
        if (f == null || !f.exists() || numero == null) {
            avisar("Arquivo não encontrado.")
            return
        }
        escopo.launch {
            val r = withContext(Dispatchers.IO) {
                Arquivos.salvarEmDownloads(context, f, container.pdf.nomeParaDownload(tipo, numero, cliente))
            }
            when (r) {
                is Resultado.Ok -> {
                    avisar(r.valor)
                }
                is Resultado.Erro -> avisar(r.mensagem)
            }
        }
    }

    // UC09 / UC10 – Enviar PDF por e-mail ao cliente e registrar log de sucesso ou falha
    fun enviarEmail() {
        val f = arquivo
        if (numero == null) return
        val (sucesso, mensagem) = when {
            !Validacao.emailValido(email) -> false to "Falha: o cliente não tem e-mail válido cadastrado."
            f == null || !f.exists() -> false to "Falha: PDF não encontrado."
            else -> {
                val assunto = "${tipo.rotulo} nº ${Formatos.numeroDocumento(numero)} – Fontenele Publicações"
                val corpo = buildString {
                    append("Olá, ${cliente.substringBefore(' ')}!\n\n")
                    append("Segue em anexo o ${tipo.rotulo.lowercase()} nº ${Formatos.numeroDocumento(numero)}")
                    val orc = orcamento
                    if (orc != null) {
                        append(" com os valores para ${Formatos.listaTiragens(Formatos.tiragensDoTexto(orc.tiragens))}")
                        append(", válido até ${Formatos.data(orc.criadoEm + orc.validadeDias * 86_400_000L)}")
                    } else if (valor != null) {
                        append(", no valor de ${Formatos.moeda(valor)}")
                    }
                    append(".\n\nQualquer dúvida, estamos à disposição.\n\n")
                    append("Atenciosamente,\n${usuario.nome}\nFontenele Publicações")
                }
                val intent = Arquivos.intentEmail(context, f, email, assunto, corpo)
                if (intent.resolveActivity(context.packageManager) == null) {
                    false to "Falha: nenhum aplicativo de e-mail encontrado no aparelho."
                } else {
                    try {
                        context.startActivity(Intent.createChooser(intent, "Enviar PDF por e-mail"))
                        true to "PDF encaminhado ao app de e-mail para $email."
                    } catch (e: ActivityNotFoundException) {
                        false to "Falha: não foi possível abrir o app de e-mail."
                    }
                }
            }
        }
        escopo.launch {
            container.editora.registrarEnvioEmail(tipo, id, numero, clienteId, email, sucesso, mensagem, usuario.nome)
            avisar(mensagem)
        }
    }

    fun abrirEmOutroApp() {
        val f = arquivo ?: return
        try {
            context.startActivity(Intent.createChooser(Arquivos.intentAbrir(context, f), "Abrir PDF com"))
        } catch (e: ActivityNotFoundException) {
            avisar("Nenhum leitor de PDF instalado. Use a pré-visualização abaixo.")
        }
    }

    // UC18 – fechar o contrato direto pela tela do documento
    fun registrarFechamento() {
        escopo.launch {
            when (val r = container.editora.registrarFechamento(id, usuario)) {
                is Resultado.Ok -> {
                    avisar("Contrato registrado como fechado.")
                    versao++
                }
                is Resultado.Erro -> avisar(r.mensagem)
            }
        }
    }

    val rotaEdicao = when (tipo) {
        TipoDocumento.ORCAMENTO -> Rotas.orcamento(id = id)
        TipoDocumento.CONTRATO -> Rotas.contrato(id = id)
    }

    Scaffold(
        topBar = {
            BarraSuperior(titulo, aoVoltar = aoVoltar) {
                IconButton(onClick = { navegar(rotaEdicao) }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Alterar informações")
                }
                IconButton(onClick = { abrirEmOutroApp() }) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Abrir em outro app")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(cliente, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            contrato?.let {
                                val status = StatusContrato.de(it.status)
                                Etiqueta(status.rotulo, destaque = status == StatusContrato.FECHADO)
                            }
                        }
                        Text(email, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (linhasOrcamento.isNotEmpty()) {
                            linhasOrcamento.forEach { linha ->
                                Row {
                                    Text(
                                        "${Formatos.numero(linha.tiragem.toDouble(), 0)} ex.",
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        "${Formatos.moeda(linha.total)}  ·  à vista ${Formatos.moeda(linha.totalAVista)}",
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        } else if (valor != null) {
                            Text(
                                Formatos.moeda(valor),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BotaoPrincipal(onClick = { baixar() }, enabled = arquivo != null, modifier = Modifier.weight(1f)) {
                            TextoBotao("Baixar PDF")
                        }
                        BotaoContorno(onClick = { enviarEmail() }, enabled = arquivo != null, modifier = Modifier.weight(1f)) {
                            TextoBotao("Enviar e-mail")
                        }
                    }
                    if (tipo == TipoDocumento.ORCAMENTO) {
                        BotaoContorno(
                            onClick = { navegar(Rotas.contrato(orcamentoId = id)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            TextoBotao("Gerar contrato")
                        }
                    }
                    if (contrato?.status == StatusContrato.ABERTO.name) {
                        BotaoContorno(onClick = { registrarFechamento() }, modifier = Modifier.fillMaxWidth()) {
                            TextoBotao("Registrar contrato fechado")
                        }
                    }
                }
            }
            item { CartaoErro(erro) }
            if (paginas.isEmpty() && erro == null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            itemsIndexed(paginas) { indice, pagina ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        bitmap = pagina,
                        contentDescription = "Página ${indice + 1} do PDF",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    )
                    Text(
                        "Página ${indice + 1} de ${paginas.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
