@file:OptIn(ExperimentalMaterial3Api::class)

package br.com.fontenele.orcamentos.ui.usuarios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.fontenele.orcamentos.AppContainer
import br.com.fontenele.orcamentos.data.local.UsuarioEntity
import br.com.fontenele.orcamentos.domain.Perfil
import br.com.fontenele.orcamentos.domain.Resultado
import br.com.fontenele.orcamentos.ui.componentes.BarraSuperior
import br.com.fontenele.orcamentos.ui.componentes.itemDeCaixa
import br.com.fontenele.orcamentos.ui.navegacao.Rotas
import kotlinx.coroutines.launch

/** Gestão de usuários e perfis (somente Administrador). */
@Composable
fun UsuariosTela(
    container: AppContainer,
    usuario: UsuarioEntity,
    aoVoltar: () -> Unit,
    navegar: (String) -> Unit,
) {
    val usuarios by remember { container.auth.observarUsuarios() }.collectAsStateWithLifecycle(emptyList())
    val escopo = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    fun executar(acao: suspend () -> Resultado<UsuarioEntity>, sucesso: String) {
        escopo.launch {
            when (val r = acao()) {
                is Resultado.Ok -> snackbar.showSnackbar(sucesso)
                is Resultado.Erro -> snackbar.showSnackbar(r.mensagem)
            }
        }
    }

    Scaffold(
        topBar = { BarraSuperior("Usuários", aoVoltar = aoVoltar) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                onClick = { navegar(Rotas.cadastro(admin = true)) },
                icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                text = { Text("Novo usuário") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        ) {
            itemsIndexed(usuarios, key = { _, u -> u.id }) { i, u ->
                CartaoUsuario(
                    ultimo = i == usuarios.lastIndex,
                    u = u,
                    ehVoce = u.id == usuario.id,
                    aoAlternarPerfil = {
                        val novo = if (u.perfil == Perfil.ADMIN.name) Perfil.FUNCIONARIO else Perfil.ADMIN
                        executar({ container.auth.alterarPerfil(u, novo) }, "${u.nome} agora é ${novo.rotulo}.")
                    },
                    aoAlternarAtivo = {
                        executar(
                            { container.auth.alterarAtivo(u, !u.ativo) },
                            if (u.ativo) "${u.nome} foi desativado." else "${u.nome} foi reativado.",
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun CartaoUsuario(
    ultimo: Boolean,
    u: UsuarioEntity,
    ehVoce: Boolean,
    aoAlternarPerfil: () -> Unit,
    aoAlternarAtivo: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .itemDeCaixa(ultimo)
    ) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (ehVoce) "${u.nome} (você)" else u.nome,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(u.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    Perfil.de(u.perfil).rotulo + if (u.ativo) "" else " · desativado",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (u.perfil == Perfil.ADMIN.name) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (u.perfil == Perfil.ADMIN.name) FontWeight.Bold else FontWeight.Normal,
                )
            }
            if (!ehVoce) {
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Opções")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = {
                                Text(if (u.perfil == Perfil.ADMIN.name) "Tornar Funcionário" else "Tornar Administrador")
                            },
                            onClick = {
                                menu = false
                                aoAlternarPerfil()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (u.ativo) "Desativar acesso" else "Reativar acesso") },
                            onClick = {
                                menu = false
                                aoAlternarAtivo()
                            },
                        )
                    }
                }
            }
        }
    }
}
