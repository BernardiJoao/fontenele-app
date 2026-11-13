@file:OptIn(ExperimentalMaterial3Api::class)

package br.com.fontenele.orcamentos.ui.documentos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.fontenele.orcamentos.AppContainer
import br.com.fontenele.orcamentos.domain.Formatos
import br.com.fontenele.orcamentos.domain.Impressao
import br.com.fontenele.orcamentos.domain.StatusContrato
import br.com.fontenele.orcamentos.domain.TipoDocumento
import br.com.fontenele.orcamentos.ui.componentes.BarraSuperior
import br.com.fontenele.orcamentos.ui.componentes.MensagemVazia
import br.com.fontenele.orcamentos.ui.componentes.itemDeCaixa
import br.com.fontenele.orcamentos.ui.navegacao.Rotas

/** Lista de orçamentos e contratos gerados (abre o PDF de cada um). */
@Composable
fun DocumentosTela(container: AppContainer, aoVoltar: () -> Unit, navegar: (String) -> Unit) {
    val orcamentos by remember { container.editora.observarOrcamentos() }.collectAsStateWithLifecycle(emptyList())
    val contratos by remember { container.editora.observarContratos() }.collectAsStateWithLifecycle(emptyList())
    var aba by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(topBar = { BarraSuperior("Documentos", aoVoltar = aoVoltar) }) { padding ->
        Column(Modifier.padding(padding)) {
            TabRow(selectedTabIndex = aba) {
                Tab(selected = aba == 0, onClick = { aba = 0 }, text = { Text("Orçamentos (${orcamentos.size})") })
                Tab(selected = aba == 1, onClick = { aba = 1 }, text = { Text("Contratos (${contratos.size})") })
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            ) {
                if (aba == 0) {
                    if (orcamentos.isEmpty()) item { MensagemVazia(Icons.Filled.PictureAsPdf, "Nenhum orçamento gerado.") }
                    itemsIndexed(orcamentos, key = { _, o -> o.id }) { i, o ->
                        CartaoDocumento(
                            ultimo = i == orcamentos.lastIndex,
                            titulo = "Orçamento nº ${Formatos.numeroDocumento(o.numero)}",
                            cliente = o.clienteNome,
                            detalhe = "${o.paginas} págs. · ${Impressao.de(o.impressao)?.rotulo ?: ""} · ${o.proporcao} · " +
                                "${Formatos.tiragensDoTexto(o.tiragens).size} tiragens",
                            valor = Formatos.moeda(o.total),
                            data = Formatos.data(o.atualizadoEm),
                            // o valor mostrado é o da menor tiragem
                            etiqueta = Formatos.tiragensDoTexto(o.tiragens).minOrNull()?.let { "${Formatos.numero(it.toDouble(), 0)} ex." },
                            fechado = false,
                        ) { navegar(Rotas.documento(TipoDocumento.ORCAMENTO, o.id)) }
                    }
                } else {
                    if (contratos.isEmpty()) item { MensagemVazia(Icons.Filled.PictureAsPdf, "Nenhum contrato gerado.") }
                    itemsIndexed(contratos, key = { _, c -> c.id }) { i, c ->
                        val status = StatusContrato.de(c.status)
                        CartaoDocumento(
                            ultimo = i == contratos.lastIndex,
                            titulo = "Contrato nº ${Formatos.numeroDocumento(c.numero)}",
                            cliente = c.nome,
                            detalhe = "${c.cidade}/${c.estado} · prazo ${c.prazoEntregaDias} dias",
                            valor = Formatos.moeda(c.valor),
                            data = Formatos.data(c.atualizadoEm),
                            etiqueta = status.rotulo,
                            fechado = status == StatusContrato.FECHADO,
                        ) { navegar(Rotas.documento(TipoDocumento.CONTRATO, c.id)) }
                    }
                }
            }
        }
    }
}

/** Linha de documento dentro da caixa: cliente, número e data; valor e situação à direita. */
@Composable
private fun CartaoDocumento(
    ultimo: Boolean,
    titulo: String,
    cliente: String,
    detalhe: String,
    valor: String,
    data: String,
    etiqueta: String?,
    fechado: Boolean,
    aoClicar: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .itemDeCaixa(ultimo)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = aoClicar)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(cliente, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "$titulo · $data",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(detalhe, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(valor, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                if (etiqueta != null) {
                    Text(
                        etiqueta,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (fechado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (fechado) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
