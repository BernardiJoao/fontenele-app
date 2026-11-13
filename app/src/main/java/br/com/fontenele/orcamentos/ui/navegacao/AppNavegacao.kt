package br.com.fontenele.orcamentos.ui.navegacao

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import br.com.fontenele.orcamentos.AppContainer
import br.com.fontenele.orcamentos.data.local.UsuarioEntity
import br.com.fontenele.orcamentos.domain.Perfil
import br.com.fontenele.orcamentos.domain.TipoDocumento
import br.com.fontenele.orcamentos.ui.clientes.ClienteDetalheTela
import br.com.fontenele.orcamentos.ui.clientes.ClienteFormTela
import br.com.fontenele.orcamentos.ui.clientes.ClientesTela
import br.com.fontenele.orcamentos.ui.componentes.AcessoNegado
import br.com.fontenele.orcamentos.ui.componentes.TelaCarregando
import br.com.fontenele.orcamentos.ui.config.ConfigTela
import br.com.fontenele.orcamentos.ui.contrato.ContratoTela
import br.com.fontenele.orcamentos.ui.documentos.DocumentoTela
import br.com.fontenele.orcamentos.ui.documentos.DocumentosTela
import br.com.fontenele.orcamentos.ui.home.HomeTela
import br.com.fontenele.orcamentos.ui.login.CadastroTela
import br.com.fontenele.orcamentos.ui.login.LoginTela
import br.com.fontenele.orcamentos.ui.orcamento.OrcamentoTela
import br.com.fontenele.orcamentos.ui.produtos.ProdutosTela
import br.com.fontenele.orcamentos.ui.usuarios.UsuariosTela

object Rotas {
    const val LOGIN = "login"
    const val CADASTRO = "cadastro?admin={admin}"
    const val HOME = "home"
    const val PRODUTOS = "produtos"
    const val ORCAMENTO = "orcamento?id={id}&clienteId={clienteId}"
    const val CONTRATO = "contrato?id={id}&orcamentoId={orcamentoId}&clienteId={clienteId}&aba={aba}"
    const val CLIENTES = "clientes"
    const val CLIENTE = "cliente/{id}"
    const val CLIENTE_FORM = "clienteForm?id={id}"
    const val DOCUMENTOS = "documentos"
    const val DOCUMENTO = "documento/{tipo}/{id}"
    const val USUARIOS = "usuarios"
    const val CONFIG = "config"

    fun cadastro(admin: Boolean) = "cadastro?admin=$admin"
    fun cliente(id: String) = "cliente/$id"
    fun clienteForm(id: String? = null) = if (id == null) "clienteForm" else "clienteForm?id=$id"
    fun documento(tipo: TipoDocumento, id: String) = "documento/${tipo.name}/$id"

    fun orcamento(id: String? = null, clienteId: String? = null): String =
        comParametros("orcamento", "id" to id, "clienteId" to clienteId)

    fun contrato(id: String? = null, orcamentoId: String? = null, clienteId: String? = null, aba: Int = 0): String =
        comParametros("contrato", "id" to id, "orcamentoId" to orcamentoId, "clienteId" to clienteId, "aba" to aba.toString())

    private fun comParametros(base: String, vararg parametros: Pair<String, String?>): String {
        val partes = parametros.filter { it.second != null }.map { "${it.first}=${it.second}" }
        return if (partes.isEmpty()) base else base + "?" + partes.joinToString("&")
    }
}

private fun argTextoOpcional(nome: String) = navArgument(nome) {
    type = NavType.StringType
    nullable = true
    defaultValue = null
}

@Composable
fun AppNavegacao(container: AppContainer) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val usuario by container.auth.usuario.collectAsStateWithLifecycle()
    // rememberSaveable: ao girar a tela o destino inicial (e a pilha de telas) é mantido
    var destinoInicial by rememberSaveable { mutableStateOf<String?>(null) }
    var sessaoVerificada by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val logado = container.auth.usuario.value ?: container.auth.restaurarSessao()
        if (destinoInicial == null) destinoInicial = if (logado != null) Rotas.HOME else Rotas.LOGIN
        sessaoVerificada = true
    }

    val inicio = destinoInicial
    if (!sessaoVerificada || inicio == null) {
        TelaCarregando()
        return
    }

    val voltar: () -> Unit = { nav.popBackStack() }
    val navegar: (String) -> Unit = { rota -> nav.navigate(rota) }
    val avisar: (String) -> Unit = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
    val irParaHomeLimpando: () -> Unit = {
        nav.navigate(Rotas.HOME) {
            popUpTo(nav.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    NavHost(navController = nav, startDestination = inicio) {

        // UC01 – Login
        composable(Rotas.LOGIN) {
            LoginTela(
                container = container,
                aoEntrar = irParaHomeLimpando,
                aoCriarConta = { nav.navigate(Rotas.cadastro(admin = false)) },
            )
        }

        // Registro de usuários com separação de perfil
        composable(
            Rotas.CADASTRO,
            arguments = listOf(navArgument("admin") { type = NavType.BoolType; defaultValue = false }),
        ) { entrada ->
            val modoAdmin = entrada.arguments?.getBoolean("admin") ?: false
            if (modoAdmin) {
                Protegida(usuario, nav, somenteAdmin = true) {
                    CadastroTela(
                        container = container,
                        modoAdmin = true,
                        aoConcluir = { msg -> avisar(msg); voltar() },
                        aoVoltar = voltar,
                    )
                }
            } else {
                CadastroTela(
                    container = container,
                    modoAdmin = false,
                    aoConcluir = { msg -> avisar(msg); irParaHomeLimpando() },
                    aoVoltar = voltar,
                )
            }
        }

        composable(Rotas.HOME) {
            Protegida(usuario, nav) { u ->
                HomeTela(container = container, usuario = u, navegar = navegar, aoSair = { sairDaConta(container) })
            }
        }

        // UC03 / UC04 – Preços, parâmetros da tabela e serviços (edição só para o Administrador)
        composable(Rotas.PRODUTOS) {
            Protegida(usuario, nav) { u -> ProdutosTela(container, u, aoVoltar = voltar) }
        }

        // UC05, UC06, UC11, UC15 – Orçamento
        composable(
            Rotas.ORCAMENTO,
            arguments = listOf(argTextoOpcional("id"), argTextoOpcional("clienteId")),
        ) { entrada ->
            val id = entrada.arguments?.getString("id")
            val clienteId = entrada.arguments?.getString("clienteId")
            Protegida(usuario, nav) { u ->
                OrcamentoTela(
                    container = container,
                    usuario = u,
                    orcamentoId = id,
                    clienteId = clienteId,
                    aoVoltar = voltar,
                    aoGerado = { novoId ->
                        nav.navigate(Rotas.documento(TipoDocumento.ORCAMENTO, novoId)) { popUpTo(Rotas.HOME) }
                    },
                )
            }
        }

        // UC07, UC12, UC16, UC18 – Contrato e fechamento
        composable(
            Rotas.CONTRATO,
            arguments = listOf(
                argTextoOpcional("id"),
                argTextoOpcional("orcamentoId"),
                argTextoOpcional("clienteId"),
                navArgument("aba") { type = NavType.IntType; defaultValue = 0 },
            ),
        ) { entrada ->
            val args = entrada.arguments
            Protegida(usuario, nav) { u ->
                ContratoTela(
                    container = container,
                    usuario = u,
                    contratoId = args?.getString("id"),
                    orcamentoId = args?.getString("orcamentoId"),
                    clienteId = args?.getString("clienteId"),
                    abaInicial = args?.getInt("aba") ?: 0,
                    aoVoltar = voltar,
                    aoGerado = { novoId ->
                        nav.navigate(Rotas.documento(TipoDocumento.CONTRATO, novoId)) { popUpTo(Rotas.HOME) }
                    },
                )
            }
        }

        // UC13, UC14 – Clientes
        composable(Rotas.CLIENTES) {
            Protegida(usuario, nav) {
                ClientesTela(
                    container = container,
                    aoVoltar = voltar,
                    aoAbrir = { id -> nav.navigate(Rotas.cliente(id)) },
                    aoNovo = { nav.navigate(Rotas.clienteForm()) },
                )
            }
        }

        // UC02 – Histórico do cliente
        composable(Rotas.CLIENTE, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entrada ->
            val id = entrada.arguments?.getString("id").orEmpty()
            Protegida(usuario, nav) { u ->
                ClienteDetalheTela(container = container, usuario = u, clienteId = id, aoVoltar = voltar, navegar = navegar)
            }
        }

        composable(Rotas.CLIENTE_FORM, arguments = listOf(argTextoOpcional("id"))) { entrada ->
            val id = entrada.arguments?.getString("id")
            Protegida(usuario, nav) { u ->
                ClienteFormTela(
                    container = container,
                    usuario = u,
                    clienteId = id,
                    aoVoltar = voltar,
                    aoSalvo = { msg -> avisar(msg); voltar() },
                )
            }
        }

        composable(Rotas.DOCUMENTOS) {
            Protegida(usuario, nav) {
                DocumentosTela(container = container, aoVoltar = voltar, navegar = navegar)
            }
        }

        // UC06/UC07 (pré-visualização), UC08 (baixar), UC09/UC10 (e-mail), UC18 (fechar)
        composable(
            Rotas.DOCUMENTO,
            arguments = listOf(
                navArgument("tipo") { type = NavType.StringType },
                navArgument("id") { type = NavType.StringType },
            ),
        ) { entrada ->
            val tipo = TipoDocumento.de(entrada.arguments?.getString("tipo"))
            val id = entrada.arguments?.getString("id").orEmpty()
            Protegida(usuario, nav) { u ->
                DocumentoTela(container = container, usuario = u, tipo = tipo, id = id, aoVoltar = voltar, navegar = navegar)
            }
        }

        composable(Rotas.USUARIOS) {
            Protegida(usuario, nav, somenteAdmin = true) { u ->
                UsuariosTela(container = container, usuario = u, aoVoltar = voltar, navegar = navegar)
            }
        }

        composable(Rotas.CONFIG) {
            Protegida(usuario, nav) { u ->
                ConfigTela(container = container, usuario = u, aoVoltar = voltar, aoSair = { sairDaConta(container) })
            }
        }
    }
}

/**
 * Proteção das telas: sem login, volta para a tela de login (UC06 fluxo 3 / UC07 fluxo 2.A);
 * telas exclusivas do Administrador mostram "acesso restrito" para Funcionários.
 */
@Composable
private fun Protegida(
    usuario: UsuarioEntity?,
    nav: NavHostController,
    somenteAdmin: Boolean = false,
    conteudo: @Composable (UsuarioEntity) -> Unit,
) {
    if (usuario == null) {
        LaunchedEffect(Unit) {
            nav.navigate(Rotas.LOGIN) {
                popUpTo(nav.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
        TelaCarregando()
        return
    }
    if (somenteAdmin && usuario.perfil != Perfil.ADMIN.name) {
        AcessoNegado(aoVoltar = { nav.popBackStack() })
        return
    }
    conteudo(usuario)
}

/** Sair: encerra a sessão no celular e invalida o token dele no servidor. */
private fun sairDaConta(container: AppContainer) {
    container.sincronizacao.sairDoServidor()
    container.auth.sair()
}
