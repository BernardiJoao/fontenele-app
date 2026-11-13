@file:OptIn(ExperimentalMaterial3Api::class)

package br.com.fontenele.orcamentos.ui.produtos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.fontenele.orcamentos.AppContainer
import br.com.fontenele.orcamentos.data.local.ProdutoEntity
import br.com.fontenele.orcamentos.data.local.UsuarioEntity
import br.com.fontenele.orcamentos.domain.ParametrosCalculo
import br.com.fontenele.orcamentos.domain.Proporcoes
import br.com.fontenele.orcamentos.domain.Cobranca
import br.com.fontenele.orcamentos.domain.Formatos
import br.com.fontenele.orcamentos.domain.Perfil
import br.com.fontenele.orcamentos.domain.Resultado
import br.com.fontenele.orcamentos.ui.componentes.BotaoContorno
import br.com.fontenele.orcamentos.ui.componentes.TextoBotao
import br.com.fontenele.orcamentos.ui.componentes.BarraSuperior
import br.com.fontenele.orcamentos.ui.componentes.CampoSelecao
import br.com.fontenele.orcamentos.ui.componentes.CampoTexto
import br.com.fontenele.orcamentos.ui.componentes.CartaoErro
import br.com.fontenele.orcamentos.ui.componentes.ItemLista
import br.com.fontenele.orcamentos.ui.componentes.DivisorCaixa
import br.com.fontenele.orcamentos.ui.componentes.CaixaLista
import br.com.fontenele.orcamentos.ui.componentes.MensagemVazia
import br.com.fontenele.orcamentos.ui.componentes.TituloSecao
import kotlinx.coroutines.launch

/**
 * UC03 – Cadastrar produto/serviço e UC04 – Editar preço/valores (edição somente pelo Administrador).
 * O preço do livro vem da tabela de valores da editora; aqui o Admin ajusta os parâmetros da tabela
 * (fator de correção, formatos, desconto à vista, colorido) e cadastra serviços cobrados à parte.
 */
@Composable
fun ProdutosTela(container: AppContainer, usuario: UsuarioEntity, aoVoltar: () -> Unit) {
    val produtos by remember { container.editora.observarProdutos() }.collectAsStateWithLifecycle(emptyList())
    val parametros by remember { container.editora.observarParametros() }.collectAsStateWithLifecycle(ParametrosCalculo())
    var editandoParametros by remember { mutableStateOf(false) }
    val admin = usuario.perfil == Perfil.ADMIN.name
    val escopo = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var editando by remember { mutableStateOf<ProdutoEntity?>(null) }
    var criando by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { BarraSuperior("Preços", aoVoltar = aoVoltar) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (admin) {
                ExtendedFloatingActionButton(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    onClick = { criando = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Novo serviço") },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        ) {
            if (!admin) {
                item {
                    Text(
                        "Somente consulta. Só o Administrador altera os preços.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            item(key = "cabecalho-tabela") { TituloSecao("Cálculo") }
            item(key = "parametros") {
                CartaoParametros(parametros, editavel = admin) { editandoParametros = true }
            }
            item(key = "cabecalho-servicos") { TituloSecao("Serviços adicionais") }
            if (produtos.isEmpty()) {
                item(key = "vazio") {
                    MensagemVazia(
                        Icons.Filled.LocalOffer,
                        "Nenhum serviço adicional cadastrado.",
                    )
                }
            }
            itemsIndexed(produtos, key = { _, p -> p.id }) { i, produto ->
                CartaoProduto(produto, editavel = admin, ultimo = i == produtos.lastIndex) { editando = produto }
            }
        }
    }

    if (criando || editando != null) {
        DialogoProduto(
            produto = editando,
            aoFechar = {
                criando = false
                editando = null
            },
            aoSalvar = { nome, descricao, preco, cobranca, ativo ->
                container.editora.salvarProduto(editando?.id, nome, descricao, preco, cobranca, ativo, usuario)
            },
            aoSalvo = { msg ->
                criando = false
                editando = null
                escopo.launch { snackbar.showSnackbar(msg) }
            },
        )
    }

    if (editandoParametros) {
        DialogoParametros(
            atual = parametros,
            aoFechar = { editandoParametros = false },
            aoSalvar = { coef, desconto, colorido, formatos ->
                container.editora.salvarParametros(usuario, coef, desconto, colorido, formatos)
            },
            aoSalvo = {
                editandoParametros = false
                escopo.launch { snackbar.showSnackbar("Valores do cálculo atualizados com sucesso!") }
            },
        )
    }
}

@Composable
private fun CartaoParametros(p: ParametrosCalculo, editavel: Boolean, aoEditar: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        CaixaLista {
            LinhaParametro("Coeficiente de inflação (padrão)", "×" + Formatos.numero(p.coeficientePadrao, 4))
            DivisorCaixa()
            LinhaParametro("Desconto à vista", Formatos.numero(p.descontoAVistaPercentual) + "%")
            DivisorCaixa()
            LinhaParametro(
                "Fator impressão colorida",
                if (p.coloridoDefinido) "×" + Formatos.numero(p.fatorColorido, 4) else "não definido",
            )
            DivisorCaixa()
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Fatores de formato", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Proporcoes.formatos.chunked(2).forEach { par ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        par.forEach { formato ->
                            Text(
                                "$formato  ×${Formatos.numero(p.fatoresFormato[formato] ?: 1.0, 4)}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (par.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        if (editavel) {
            BotaoContorno(onClick = aoEditar, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                TextoBotao("Editar valores")
            }
        }
    }
}

@Composable
private fun LinhaParametro(rotulo: String, valor: String) {
    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(rotulo, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(valor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** UC04 – o Administrador altera os valores usados no cálculo (equivalente às células J3, J6:J12 e Q15 da planilha). */
@Composable
private fun DialogoParametros(
    atual: ParametrosCalculo,
    aoFechar: () -> Unit,
    aoSalvar: suspend (String, String, String, Map<String, String>) -> Resultado<ParametrosCalculo>,
    aoSalvo: () -> Unit,
) {
    val escopo = rememberCoroutineScope()
    var coeficiente by remember { mutableStateOf(Formatos.paraCampo(atual.coeficientePadrao)) }
    var desconto by remember { mutableStateOf(Formatos.paraCampo(atual.descontoAVistaPercentual)) }
    var colorido by remember { mutableStateOf(if (atual.coloridoDefinido) Formatos.paraCampo(atual.fatorColorido) else "") }
    var formatos by remember {
        mutableStateOf(Proporcoes.formatos.associateWith { Formatos.paraCampo(atual.fatoresFormato[it] ?: 1.0) })
    }
    var erro by remember { mutableStateOf<String?>(null) }
    var salvando by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!salvando) aoFechar() },
        title = { Text("Valores do cálculo") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CampoTexto(coeficiente, { coeficiente = it }, "Coeficiente de inflação padrão", obrigatorio = true,
                    teclado = KeyboardType.Decimal, ajuda = "Planilha: fator de correção (J3)")
                CampoTexto(desconto, { desconto = it }, "Desconto à vista (%)", obrigatorio = true, teclado = KeyboardType.Decimal)
                CampoTexto(colorido, { colorido = it }, "Fator impressão colorida", teclado = KeyboardType.Decimal,
                    ajuda = "Em branco = colorido não disponível")
                Text("Fatores de formato", style = MaterialTheme.typography.labelLarge)
                Proporcoes.formatos.chunked(2).forEach { par ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        par.forEach { formato ->
                            CampoTexto(
                                valor = formatos[formato].orEmpty(),
                                aoMudar = { novo -> formatos = formatos + (formato to novo) },
                                rotulo = formato,
                                obrigatorio = true,
                                teclado = KeyboardType.Decimal,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (par.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                CartaoErro(erro)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !salvando,
                onClick = {
                    salvando = true
                    escopo.launch {
                        when (val r = aoSalvar(coeficiente, desconto, colorido, formatos)) {
                            is Resultado.Ok -> aoSalvo()
                            is Resultado.Erro -> erro = r.mensagem
                        }
                        salvando = false
                    }
                },
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(enabled = !salvando, onClick = aoFechar) { Text("Cancelar") } },
    )
}

@Composable
private fun CartaoProduto(produto: ProdutoEntity, editavel: Boolean, ultimo: Boolean, aoClicar: () -> Unit) {
    val cobranca = Cobranca.de(produto.cobranca).rotulo
    ItemLista(
        titulo = produto.nome,
        ultimo = ultimo,
        detalhe = listOf(produto.descricao, cobranca + if (produto.ativo) "" else " · inativo")
            .filter { it.isNotBlank() }
            .joinToString("\n"),
        valor = Formatos.moedaUnitaria(produto.preco),
        aoClicar = if (editavel) aoClicar else null,
    )
}

@Composable
private fun DialogoProduto(
    produto: ProdutoEntity?,
    aoFechar: () -> Unit,
    aoSalvar: suspend (String, String, String, Cobranca, Boolean) -> Resultado<ProdutoEntity>,
    aoSalvo: (String) -> Unit,
) {
    val escopo = rememberCoroutineScope()
    var nome by remember { mutableStateOf(produto?.nome ?: "") }
    var descricao by remember { mutableStateOf(produto?.descricao ?: "") }
    var preco by remember { mutableStateOf(produto?.let { Formatos.paraCampo(it.preco) } ?: "") }
    var cobranca by remember { mutableStateOf(produto?.let { Cobranca.de(it.cobranca) } ?: Cobranca.FIXO) }
    var ativo by remember { mutableStateOf(produto?.ativo ?: true) }
    var erro by remember { mutableStateOf<String?>(null) }
    var salvando by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!salvando) aoFechar() },
        title = { Text(if (produto == null) "Novo serviço adicional" else "Editar serviço") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CampoTexto(nome, { nome = it }, "Nome", obrigatorio = true)
                CampoTexto(descricao, { descricao = it }, "Descrição", obrigatorio = true, linhas = 2)
                CampoTexto(preco, { preco = it }, "Preço (R$)", obrigatorio = true, teclado = KeyboardType.Decimal)
                CampoSelecao(
                    rotulo = "Forma de cobrança",
                    opcoes = Cobranca.entries.map { it.name to it.rotulo },
                    selecionado = cobranca.name,
                    aoSelecionar = { cobranca = Cobranca.de(it) },
                    obrigatorio = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ativo (aparece nos orçamentos)", modifier = Modifier.weight(1f))
                    Switch(checked = ativo, onCheckedChange = { ativo = it })
                }
                CartaoErro(erro)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !salvando,
                onClick = {
                    salvando = true
                    escopo.launch {
                        when (val r = aoSalvar(nome, descricao, preco, cobranca, ativo)) {
                            is Resultado.Ok -> aoSalvo(
                                if (produto == null) "Serviço cadastrado com sucesso!" else "Preço atualizado com sucesso!"
                            )
                            is Resultado.Erro -> erro = r.mensagem
                        }
                        salvando = false
                    }
                },
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(enabled = !salvando, onClick = aoFechar) { Text("Cancelar") } },
    )
}
