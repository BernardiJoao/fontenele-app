@file:OptIn(ExperimentalMaterial3Api::class)

package br.com.fontenele.orcamentos.ui.clientes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.fontenele.orcamentos.AppContainer
import br.com.fontenele.orcamentos.data.local.ClienteEntity
import br.com.fontenele.orcamentos.ui.componentes.BarraSuperior
import br.com.fontenele.orcamentos.ui.componentes.ItemLista
import br.com.fontenele.orcamentos.ui.componentes.itemDeCaixa
import br.com.fontenele.orcamentos.ui.componentes.MensagemVazia

private fun filtrar(clientes: List<ClienteEntity>, busca: String): List<ClienteEntity> =
    if (busca.isBlank()) clientes
    else clientes.filter { it.nome.contains(busca.trim(), ignoreCase = true) || it.email.contains(busca.trim(), ignoreCase = true) }

/** UC14 – Consultar clientes cadastrados (nome, e-mail, orçamentos pedidos e contratos fechados). */
@Composable
fun ClientesTela(
    container: AppContainer,
    aoVoltar: () -> Unit,
    aoAbrir: (String) -> Unit,
    aoNovo: () -> Unit,
) {
    val clientes by remember { container.editora.observarClientes() }.collectAsStateWithLifecycle(initialValue = null)
    var busca by rememberSaveable { mutableStateOf("") }
    val lista = clientes
    val filtrados = remember(lista, busca) { filtrar(lista.orEmpty(), busca) }

    Scaffold(
        topBar = { BarraSuperior("Clientes", aoVoltar = aoVoltar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                onClick = aoNovo,
                icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                text = { Text("Novo cliente") },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = busca,
                onValueChange = { busca = it },
                placeholder = { Text("Buscar por nome ou e-mail") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            ) {
                if (lista != null && lista.isEmpty()) {
                    // UC14 fluxo 3.A – lista vazia
                    item { MensagemVazia(Icons.Filled.People, "Nenhum cliente cadastrado.") }
                } else if (lista != null && filtrados.isEmpty()) {
                    item { MensagemVazia(Icons.Filled.Search, "Nenhum cliente encontrado para \"$busca\".") }
                }
                itemsIndexed(filtrados, key = { _, c -> c.id }) { i, cliente ->
                    LinhaCliente(cliente, ultimo = i == filtrados.lastIndex) { aoAbrir(cliente.id) }
                }
            }
        }
    }
}

@Composable
private fun LinhaCliente(cliente: ClienteEntity, ultimo: Boolean, aoClicar: () -> Unit) {
    val pedidos = cliente.orcamentosPedidos
    val fechados = cliente.contratosFechados
    ItemLista(
        titulo = cliente.nome,
        ultimo = ultimo,
        detalhe = "${cliente.email}\n" +
            "$pedidos ${if (pedidos == 1) "orçamento" else "orçamentos"} · " +
            "$fechados ${if (fechados == 1) "contrato fechado" else "contratos fechados"}",
        aoClicar = aoClicar,
    )
}

/** Diálogo para escolher um cliente já cadastrado (usado no orçamento e no contrato). */
@Composable
fun DialogoEscolherCliente(
    clientes: List<ClienteEntity>,
    aoEscolher: (ClienteEntity) -> Unit,
    aoFechar: () -> Unit,
) {
    var busca by remember { mutableStateOf("") }
    val filtrados = remember(clientes, busca) { filtrar(clientes, busca) }
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("Escolher cliente") },
        text = {
            Column {
                OutlinedTextField(
                    value = busca,
                    onValueChange = { busca = it },
                    placeholder = { Text("Buscar") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (filtrados.isEmpty()) {
                    Text(
                        "Nenhum cliente cadastrado.",
                        modifier = Modifier.padding(top = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(Modifier.heightIn(max = 360.dp).padding(top = 8.dp)) {
                    itemsIndexed(filtrados, key = { _, c -> c.id }) { i, cliente ->
                        ListItem(
                            headlineContent = { Text(cliente.nome) },
                            supportingContent = { Text(cliente.email) },
                            modifier = Modifier
                                .itemDeCaixa(ultimo = i == filtrados.lastIndex)
                                .clickable { aoEscolher(cliente) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = aoFechar) { Text("Fechar") } },
    )
}
