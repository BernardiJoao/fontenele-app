@file:OptIn(ExperimentalMaterial3Api::class)

package br.com.fontenele.orcamentos.ui.orcamento

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.fontenele.orcamentos.AppContainer
import br.com.fontenele.orcamentos.data.local.UsuarioEntity
import br.com.fontenele.orcamentos.domain.Cobranca
import br.com.fontenele.orcamentos.domain.Formatos
import br.com.fontenele.orcamentos.domain.Impressao
import br.com.fontenele.orcamentos.domain.Proporcoes
import br.com.fontenele.orcamentos.domain.ResultadoOrcamento
import br.com.fontenele.orcamentos.domain.TabelaValores
import br.com.fontenele.orcamentos.ui.clientes.DialogoEscolherCliente
import br.com.fontenele.orcamentos.ui.componentes.BotaoPrincipal
import br.com.fontenele.orcamentos.ui.componentes.BotaoContorno
import br.com.fontenele.orcamentos.ui.componentes.TextoBotao
import br.com.fontenele.orcamentos.ui.componentes.BarraSuperior
import br.com.fontenele.orcamentos.ui.componentes.CampoSelecao
import br.com.fontenele.orcamentos.ui.componentes.CampoTexto
import br.com.fontenele.orcamentos.ui.componentes.CartaoErro
import br.com.fontenele.orcamentos.ui.componentes.TituloSecao
import br.com.fontenele.orcamentos.ui.componentes.DivisorCaixa
import br.com.fontenele.orcamentos.ui.componentes.CaixaLista

/**
 * UC05 (calcular), UC06 (gerar PDF), UC11 (customizar) e UC15 (inserir/alterar informações do orçamento).
 * Equivale à página "orçamento.html" do sistema web.
 */
@Composable
fun OrcamentoTela(
    container: AppContainer,
    usuario: UsuarioEntity,
    orcamentoId: String?,
    clienteId: String?,
    aoVoltar: () -> Unit,
    aoGerado: (String) -> Unit,
) {
    val vm: OrcamentoViewModel = viewModel {
        OrcamentoViewModel(container.editora, container.pdf, orcamentoId, clienteId)
    }
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val produtos by vm.produtos.collectAsStateWithLifecycle()
    val clientes by vm.clientes.collectAsStateWithLifecycle()
    var escolhendoCliente by remember { mutableStateOf(false) }
    val f = vm.form
    val parametros by vm.parametros.collectAsStateWithLifecycle()
    val adicionais = produtos

    val titulo = vm.numeroExistente?.let { "Alterar orçamento nº ${Formatos.numeroDocumento(it)}" } ?: "PDF de orçamento"

    Scaffold(
        topBar = { BarraSuperior(titulo, aoVoltar = aoVoltar) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TituloSecao("Cliente")
            Row(verticalAlignment = Alignment.CenterVertically) {
                CampoTexto(
                    valor = f.clienteNome,
                    aoMudar = { vm.atualizar(f.copy(clienteNome = it)) },
                    rotulo = "Nome do cliente",
                    obrigatorio = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { escolhendoCliente = true }) {
                    Icon(Icons.Filled.Contacts, contentDescription = "Escolher cliente cadastrado")
                }
            }
            CampoTexto(
                valor = f.clienteEmail,
                aoMudar = { vm.atualizar(f.copy(clienteEmail = it)) },
                rotulo = "E-mail do cliente",
                obrigatorio = true,
                teclado = KeyboardType.Email,
            )

            TituloSecao("Livro")
            Text("Impressão do miolo*", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Impressao.entries.forEach { opcao ->
                    FilterChip(
                        selected = f.impressao == opcao,
                        onClick = { vm.atualizar(f.copy(impressao = opcao)) },
                        label = { Text(opcao.rotulo) },
                    )
                }
            }
            if (f.impressao == Impressao.COLORIDO && !parametros.coloridoDefinido) {
                Text(
                    "A tabela da editora só tem preços de miolo P&B. O Administrador precisa definir o fator colorido.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            CampoTexto(
                valor = f.paginas,
                aoMudar = { vm.atualizar(f.copy(paginas = it.filter(Char::isDigit))) },
                rotulo = "Páginas",
                obrigatorio = true,
                teclado = KeyboardType.Number,
                ajuda = "De ${vm.faixaPaginas}",
            )
            CampoSelecao(
                rotulo = "Proporção (formato em cm)",
                opcoes = Proporcoes.formatos.map { formato ->
                    val fator = parametros.fatoresFormato[formato] ?: 1.0
                    formato to "$formato  (×${Formatos.numero(fator)})"
                },
                selecionado = f.proporcao,
                aoSelecionar = { vm.atualizar(f.copy(proporcao = it)) },
                obrigatorio = true,
            )
            CampoTexto(
                valor = f.coeficiente,
                aoMudar = { vm.atualizar(f.copy(coeficiente = it)) },
                rotulo = "Coeficiente de inflação",
                obrigatorio = true,
                teclado = KeyboardType.Decimal,
                ajuda = "Padrão ${Formatos.numero(parametros.coeficientePadrao)}",
            )
            Text("Tiragens no orçamento*", style = MaterialTheme.typography.labelLarge)
            TabelaValores.TIRAGENS.chunked(3).forEach { grupo ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    grupo.forEach { tiragem ->
                        FilterChip(
                            selected = tiragem in f.tiragens,
                            onClick = { vm.alternarTiragem(tiragem) },
                            label = { Text("${Formatos.numero(tiragem.toDouble(), 0)} ex.") },
                        )
                    }
                }
            }

            if (adicionais.isNotEmpty()) TituloSecao("Serviços adicionais")
            adicionais.forEach { p ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.alternarAdicional(p.id) },
                ) {
                    Checkbox(checked = p.id in f.adicionais, onCheckedChange = { vm.alternarAdicional(p.id) })
                    Column(Modifier.weight(1f)) {
                        Text(p.nome, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${Formatos.moedaUnitaria(p.preco)} · ${Cobranca.de(p.cobranca).rotulo.lowercase()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            TituloSecao("Condições")
            CampoTexto(
                valor = f.validadeDias,
                aoMudar = { vm.atualizar(f.copy(validadeDias = it.filter(Char::isDigit))) },
                rotulo = "Validade do orçamento (dias)",
                obrigatorio = true,
                teclado = KeyboardType.Number,
            )
            CampoTexto(
                valor = f.observacoes,
                aoMudar = { vm.atualizar(f.copy(observacoes = it)) },
                rotulo = "Observações para o cliente",
                linhas = 3,
            )

            CartaoErro(vm.erro)
            vm.resultado?.let { CartaoResultado(it) }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BotaoContorno(onClick = { vm.calcular() }, modifier = Modifier.weight(1f)) {
                    TextoBotao("Calcular")
                }
                BotaoPrincipal(
                    onClick = {
                        vm.gerarPdf(usuario) { id ->
                            aoGerado(id)
                        }
                    },
                    enabled = !vm.processando,
                    modifier = Modifier.weight(1f),
                ) {
                    if (vm.processando) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                    } else {
                        TextoBotao("Gerar PDF")
                    }
                }
            }
        }
    }

    if (escolhendoCliente) {
        DialogoEscolherCliente(
            clientes = clientes,
            aoEscolher = {
                vm.escolherCliente(it)
                escolhendoCliente = false
            },
            aoFechar = { escolhendoCliente = false },
        )
    }
}

/** Mostra os valores calculados para revisão do usuário (UC05 fluxo 7), como o bloco de preços da planilha. */
@Composable
private fun CartaoResultado(r: ResultadoOrcamento) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TituloSecao("Valores calculados")
        Text(
            "Formato ×${Formatos.numero(r.fatorFormato)} · coeficiente ×${Formatos.numero(r.coeficienteInflacao)}" +
                if (r.fatorImpressao != 1.0) " · colorido ×${Formatos.numero(r.fatorImpressao)}" else "",
            style = MaterialTheme.typography.bodySmall,
        )
        // Uma linha por tiragem, dentro de uma caixa com divisórias
        CaixaLista {
            r.linhas.forEachIndexed { i, linha ->
                if (i > 0) DivisorCaixa()
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${Formatos.numero(linha.tiragem.toDouble(), 0)} exemplares",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "${Formatos.moeda(linha.valorUnitario)} cada · à vista ${Formatos.moeda(linha.totalAVista)}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        Formatos.moeda(linha.total),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (r.adicionais.isNotEmpty()) {
                DivisorCaixa()
                LinhaValor("Inclui adicionais", r.adicionais.joinToString(", ") { it.descricao })
            }
        }
        Text(
            "À vista: desconto de ${Formatos.numero(r.descontoAVistaPercentual)}%.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun LinhaValor(rotulo: String, valor: String) {
    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(rotulo, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(valor, style = MaterialTheme.typography.bodyMedium)
    }
}
