@file:OptIn(ExperimentalMaterial3Api::class)

package br.com.fontenele.orcamentos.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.fontenele.orcamentos.AppContainer
import br.com.fontenele.orcamentos.data.local.UsuarioEntity
import br.com.fontenele.orcamentos.data.repo.EstadoSync
import br.com.fontenele.orcamentos.domain.Formatos
import br.com.fontenele.orcamentos.domain.Perfil
import br.com.fontenele.orcamentos.domain.TipoDocumento
import br.com.fontenele.orcamentos.ui.componentes.BarraSuperior
import br.com.fontenele.orcamentos.ui.componentes.BotaoContorno
import br.com.fontenele.orcamentos.ui.componentes.BotaoPrincipal
import br.com.fontenele.orcamentos.ui.componentes.ItemLista
import br.com.fontenele.orcamentos.ui.componentes.ItemMenu
import br.com.fontenele.orcamentos.ui.componentes.MensagemVazia
import br.com.fontenele.orcamentos.ui.componentes.TextoBotao
import kotlinx.coroutines.launch
import br.com.fontenele.orcamentos.ui.componentes.StatusServidor
import androidx.compose.runtime.rememberCoroutineScope
import br.com.fontenele.orcamentos.ui.componentes.TituloSecao
import br.com.fontenele.orcamentos.ui.navegacao.Rotas

/**
 * Tela inicial: as duas ações do dia a dia, o que está em andamento e o menu.
 * Sem saudação, indicadores ou grade de ícones.
 */
@Composable
fun HomeTela(
    container: AppContainer,
    usuario: UsuarioEntity,
    navegar: (String) -> Unit,
    aoSair: () -> Unit,
) {
    val editora = container.editora
    val orcamentos by remember { editora.observarOrcamentos() }.collectAsStateWithLifecycle(emptyList())
    val abertos by remember { editora.observarContratosAbertos() }.collectAsStateWithLifecycle(emptyList())
    val admin = usuario.perfil == Perfil.ADMIN.name
    val escopo = rememberCoroutineScope()
    val estadoServidor by container.sincronizacao.estado.collectAsStateWithLifecycle()

    val menu = buildList {
        add("Clientes" to Rotas.CLIENTES)
        add("Documentos" to Rotas.DOCUMENTOS)
        add((if (admin) "Preços" else "Preços (consulta)") to Rotas.PRODUTOS)
        if (admin) add("Usuários" to Rotas.USUARIOS)
        add("Configurações" to Rotas.CONFIG)
    }

    Scaffold(
        topBar = {
            BarraSuperior("Fontenele", mostrarSimbolo = true) {
                IconButton(onClick = aoSair) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sair")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        ) {
            item {
                Text(
                    "${usuario.nome} · ${Perfil.de(usuario.perfil).rotulo}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatusServidor(
                    estado = estadoServidor,
                    logado = true,
                    endereco = container.prefs.urlServidor,
                    // Token vencido/ausente: o jeito de resolver é sair e entrar de novo com a senha
                    acao = if (estadoServidor is EstadoSync.PrecisaEntrar) "Entrar" else "Sincronizar",
                    aoClicarAcao = {
                        if (estadoServidor is EstadoSync.PrecisaEntrar) aoSair()
                        else escopo.launch { container.sincronizacao.sincronizarAgora() }
                    },
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BotaoPrincipal(onClick = { navegar(Rotas.orcamento()) }, modifier = Modifier.weight(1f)) {
                        TextoBotao("Novo orçamento")
                    }
                    BotaoContorno(onClick = { navegar(Rotas.contrato()) }, modifier = Modifier.weight(1f)) {
                        TextoBotao("Novo contrato")
                    }
                }
                Spacer(Modifier.height(8.dp))
                TituloSecao("Orçamentos recentes")
            }
            if (orcamentos.isEmpty()) {
                item { MensagemVazia(Icons.Filled.Receipt, "Nenhum orçamento ainda.") }
            } else {
                val recentes = orcamentos.take(5)
                itemsIndexed(recentes, key = { _, o -> "o" + o.id }) { i, o ->
                    ItemLista(
                        titulo = o.clienteNome,
                        ultimo = i == recentes.lastIndex,
                        detalhe = "Nº ${Formatos.numeroDocumento(o.numero)} · ${Formatos.data(o.criadoEm)}",
                        valor = Formatos.moeda(o.total),
                        // o valor salvo é o da menor tiragem do orçamento
                        valorDetalhe = Formatos.tiragensDoTexto(o.tiragens).minOrNull()?.let { "${Formatos.numero(it.toDouble(), 0)} ex." },
                        aoClicar = { navegar(Rotas.documento(TipoDocumento.ORCAMENTO, o.id)) },
                    )
                }
            }
            item { TituloSecao("Contratos em aberto") }
            if (abertos.isEmpty()) {
                item { MensagemVazia(Icons.Filled.Description, "Nenhum contrato em aberto.") }
            } else {
                val emAberto = abertos.take(5)
                itemsIndexed(emAberto, key = { _, c -> "c" + c.id }) { i, c ->
                    ItemLista(
                        titulo = c.nome,
                        ultimo = i == emAberto.lastIndex,
                        detalhe = "Nº ${Formatos.numeroDocumento(c.numero)} · ${Formatos.data(c.criadoEm)}",
                        valor = Formatos.moeda(c.valor),
                        aoClicar = { navegar(Rotas.documento(TipoDocumento.CONTRATO, c.id)) },
                    )
                }
            }
            item {
                TituloSecao("Menu")
                menu.forEach { (texto, rota) -> ItemMenu(texto, aoClicar = { navegar(rota) }) }
                Spacer(Modifier.fillMaxWidth().height(8.dp))
            }
        }
    }
}
