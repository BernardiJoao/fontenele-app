@file:OptIn(ExperimentalMaterial3Api::class)

package br.com.fontenele.orcamentos.ui.contrato

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.fontenele.orcamentos.AppContainer
import br.com.fontenele.orcamentos.data.local.ContratoEntity
import br.com.fontenele.orcamentos.data.local.OrcamentoEntity
import br.com.fontenele.orcamentos.data.local.UsuarioEntity
import br.com.fontenele.orcamentos.domain.Formatos
import br.com.fontenele.orcamentos.domain.LinhaTiragem
import br.com.fontenele.orcamentos.ui.clientes.DialogoEscolherCliente
import br.com.fontenele.orcamentos.ui.componentes.BotaoPrincipal
import br.com.fontenele.orcamentos.ui.componentes.BotaoContorno
import br.com.fontenele.orcamentos.ui.componentes.TextoBotao
import br.com.fontenele.orcamentos.ui.componentes.BarraSuperior
import br.com.fontenele.orcamentos.ui.componentes.CampoTexto
import br.com.fontenele.orcamentos.ui.componentes.ItemLista
import br.com.fontenele.orcamentos.ui.componentes.itemDeCaixa
import br.com.fontenele.orcamentos.ui.componentes.CartaoErro
import br.com.fontenele.orcamentos.ui.componentes.MensagemVazia
import br.com.fontenele.orcamentos.ui.componentes.TituloSecao
import kotlinx.coroutines.launch

/**
 * UC07 (gerar PDF de contrato), UC12 (customizar), UC16 (inserir/alterar informações) e UC18 (fechamento).
 * Equivale à página "contrato.html" do sistema web, com as duas partes em abas.
 */
@Composable
fun ContratoTela(
    container: AppContainer,
    usuario: UsuarioEntity,
    contratoId: String?,
    orcamentoId: String?,
    clienteId: String?,
    abaInicial: Int,
    aoVoltar: () -> Unit,
    aoGerado: (String) -> Unit,
) {
    val vm: ContratoViewModel = viewModel {
        ContratoViewModel(container.editora, container.pdf, contratoId, orcamentoId, clienteId)
    }
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var aba by rememberSaveable { mutableIntStateOf(if (contratoId != null) 0 else abaInicial) }

    fun avisar(msg: String) {
        escopo.launch { snackbar.showSnackbar(msg) }
    }

    val titulo = vm.numeroExistente?.let { "Alterar contrato nº ${Formatos.numeroDocumento(it)}" } ?: "Contrato"

    Scaffold(
        topBar = { BarraSuperior(titulo, aoVoltar = aoVoltar) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (contratoId == null) {
                TabRow(selectedTabIndex = aba) {
                    Tab(selected = aba == 0, onClick = { aba = 0 }, text = { Text("PDF de contrato") })
                    Tab(selected = aba == 1, onClick = { aba = 1 }, text = { Text("Fechamento") })
                }
            }
            if (aba == 0) {
                AbaContrato(
                    vm = vm,
                    container = container,
                    usuario = usuario,
                    avisar = { msg -> avisar(msg) },
                    aoGerado = aoGerado,
                )
            } else {
                AbaFechamento(vm = vm, usuario = usuario, avisar = { msg -> avisar(msg) })
            }
        }
    }
}

@Composable
private fun AbaContrato(
    vm: ContratoViewModel,
    container: AppContainer,
    usuario: UsuarioEntity,
    avisar: (String) -> Unit,
    aoGerado: (String) -> Unit,
) {
    val context = LocalContext.current
    val f = vm.form
    val clientes by vm.clientes.collectAsStateWithLifecycle()
    val orcamentos by vm.orcamentos.collectAsStateWithLifecycle()
    var escolhendoCliente by remember { mutableStateOf(false) }
    var escolhendoOrcamento by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Preencher com os dados de:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotaoContorno(onClick = { escolhendoOrcamento = true }, modifier = Modifier.weight(1f)) {
                TextoBotao("Orçamento")
            }
            BotaoContorno(onClick = { escolhendoCliente = true }, modifier = Modifier.weight(1f)) {
                TextoBotao("Cliente")
            }
        }
        f.orcamentoNumero?.let { numero ->
            val tiragemTexto = f.tiragem?.let { " · ${Formatos.numero(it.toDouble(), 0)} ex." } ?: ""
            AssistChip(
                onClick = { vm.desvincularOrcamento() },
                label = { Text("Orçamento nº ${Formatos.numeroDocumento(numero)}$tiragemTexto") },
                leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Desvincular") },
            )
        }

        TituloSecao("Dados do cliente")
        CampoTexto(f.nome, { vm.atualizar(f.copy(nome = it)) }, "Nome do cliente", obrigatorio = true)
        CampoTexto(f.cpf, { vm.atualizar(f.copy(cpf = it)) }, "CPF do cliente", obrigatorio = true, teclado = KeyboardType.Number)
        CampoTexto(f.email, { vm.atualizar(f.copy(email = it)) }, "E-mail do cliente", obrigatorio = true, teclado = KeyboardType.Email)
        CampoTexto(f.telefone, { vm.atualizar(f.copy(telefone = it)) }, "Telefone do cliente", obrigatorio = true, teclado = KeyboardType.Phone)

        TituloSecao("Endereço")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CampoTexto(f.cep, { vm.atualizar(f.copy(cep = it)) }, "CEP", obrigatorio = true, teclado = KeyboardType.Number, modifier = Modifier.weight(1f))
            CampoTexto(f.estado, { vm.atualizar(f.copy(estado = it.take(2).uppercase())) }, "Estado (UF)", obrigatorio = true, modifier = Modifier.weight(0.8f))
        }
        CampoTexto(f.cidade, { vm.atualizar(f.copy(cidade = it)) }, "Cidade", obrigatorio = true)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CampoTexto(f.rua, { vm.atualizar(f.copy(rua = it)) }, "Rua", obrigatorio = true, modifier = Modifier.weight(1f))
            CampoTexto(f.numero, { vm.atualizar(f.copy(numero = it)) }, "Número", obrigatorio = true, modifier = Modifier.weight(0.6f))
        }

        TituloSecao("Valores e condições")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CampoTexto(f.valor, { vm.atualizar(f.copy(valor = it)) }, "Valor (R$)", obrigatorio = true, teclado = KeyboardType.Decimal, modifier = Modifier.weight(1f))
            CampoTexto(f.prazo, { vm.atualizar(f.copy(prazo = it.filter(Char::isDigit))) }, "Prazo (dias)", obrigatorio = true, teclado = KeyboardType.Number, modifier = Modifier.weight(0.8f))
        }
        CampoTexto(f.formaPagamento, { vm.atualizar(f.copy(formaPagamento = it)) }, "Forma de pagamento", obrigatorio = true, linhas = 2)
        CampoTexto(f.clausulas, { vm.atualizar(f.copy(clausulas = it)) }, "Cláusulas adicionais (opcional)", linhas = 3)

        CartaoErro(vm.erro)
        BotaoPrincipal(
            onClick = {
                vm.gerarPdf(usuario) { id ->
                    aoGerado(id)
                }
            },
            enabled = !vm.processando,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (vm.processando) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
            } else {
                Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                TextoBotao("Gerar PDF do contrato")
            }
        }
    }

    if (escolhendoCliente) {
        DialogoEscolherCliente(
            clientes = clientes,
            aoEscolher = {
                vm.usarCliente(it)
                escolhendoCliente = false
            },
            aoFechar = { escolhendoCliente = false },
        )
    }
    vm.orcamentoParaTiragem?.let { orcamento ->
        DialogoEscolherTiragem(
            orcamento = orcamento,
            linhas = vm.linhasDoOrcamento(orcamento),
            aoEscolher = { linha, aVista -> vm.escolherTiragem(orcamento, linha, aVista) },
            aoFechar = { vm.cancelarEscolhaTiragem() },
        )
    }
    if (escolhendoOrcamento) {
        DialogoEscolherOrcamento(
            orcamentos = orcamentos,
            aoEscolher = {
                vm.escolherOrcamento(it)
                escolhendoOrcamento = false
            },
            aoFechar = { escolhendoOrcamento = false },
        )
    }
}

/** UC18 – Registrar contrato fechado (como o formulário "Fechamento de Contrato" do sistema web). */
@Composable
private fun AbaFechamento(vm: ContratoViewModel, usuario: UsuarioEntity, avisar: (String) -> Unit) {
    val context = LocalContext.current
    val abertos by vm.abertos.collectAsStateWithLifecycle()

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        modifier = Modifier.imePadding(),
    ) {
        // Formulário em um só item, com espaçamento próprio (a lista de baixo fica numa caixa, sem espaços)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Informe o e-mail do cliente cujo contrato foi fechado. O contador de \"contratos fechados\" " +
                        "do cliente será atualizado.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CampoTexto(
                    valor = vm.emailFechamento,
                    aoMudar = { vm.emailFechamento = it },
                    rotulo = "E-mail do cliente",
                    obrigatorio = true,
                    teclado = KeyboardType.Email,
                )
                CartaoErro(vm.erroFechamento)
                BotaoPrincipal(
                    onClick = {
                        vm.registrarFechamento(usuario) { msg ->
                            avisar(msg)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Verified, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    TextoBotao("Registrar fechamento")
                }
            }
        }
        item { TituloSecao("Contratos em aberto") }
        if (abertos.isEmpty()) {
            item { MensagemVazia(Icons.Filled.Verified, "Nenhum contrato em aberto.") }
        }
        itemsIndexed(abertos, key = { _, c -> c.id }) { i, c ->
            CartaoContratoAberto(c, ultimo = i == abertos.lastIndex) { vm.emailFechamento = c.email }
        }
    }
}

@Composable
private fun CartaoContratoAberto(c: ContratoEntity, ultimo: Boolean, aoClicar: () -> Unit) {
    ItemLista(
        titulo = "Nº ${Formatos.numeroDocumento(c.numero)} · ${c.nome}",
        ultimo = ultimo,
        detalhe = c.email,
        valor = Formatos.moeda(c.valor),
        aoClicar = aoClicar,
    )
}

@Composable
private fun DialogoEscolherOrcamento(
    orcamentos: List<OrcamentoEntity>,
    aoEscolher: (OrcamentoEntity) -> Unit,
    aoFechar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("Usar dados de um orçamento") },
        text = {
            if (orcamentos.isEmpty()) {
                Text("Nenhum orçamento gerado ainda.")
            } else {
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    itemsIndexed(orcamentos, key = { _, o -> o.id }) { i, o ->
                        ListItem(
                            headlineContent = { Text("Nº ${Formatos.numeroDocumento(o.numero)} · ${o.clienteNome}") },
                            supportingContent = {
                                Text("${o.paginas} págs. · ${o.proporcao} · ${Formatos.data(o.criadoEm)}")
                            },
                            modifier = Modifier
                                .itemDeCaixa(ultimo = i == orcamentos.lastIndex)
                                .clickable { aoEscolher(o) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = aoFechar) { Text("Fechar") } },
    )
}

/** Escolha da tiragem do orçamento que vai para o contrato (preço normal ou à vista). */
@Composable
private fun DialogoEscolherTiragem(
    orcamento: OrcamentoEntity,
    linhas: List<LinhaTiragem>,
    aoEscolher: (LinhaTiragem, Boolean) -> Unit,
    aoFechar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("Tiragem do orçamento nº ${Formatos.numeroDocumento(orcamento.numero)}") },
        text = {
            if (linhas.isEmpty()) {
                Text("Este orçamento não tem valores salvos. Informe o valor do contrato manualmente.")
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(linhas, key = { it.tiragem }) { linha ->
                        Text(
                            "${Formatos.numero(linha.tiragem.toDouble(), 0)} exemplares",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BotaoContorno(onClick = { aoEscolher(linha, false) }, modifier = Modifier.weight(1f)) {
                                TextoBotao(Formatos.moeda(linha.total))
                            }
                            BotaoContorno(onClick = { aoEscolher(linha, true) }, modifier = Modifier.weight(1f)) {
                                TextoBotao("À vista ${Formatos.moeda(linha.totalAVista)}")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = aoFechar) { Text("Preencher depois") } },
    )
}
