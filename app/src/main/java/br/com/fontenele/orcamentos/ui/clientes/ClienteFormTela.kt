@file:OptIn(ExperimentalMaterial3Api::class)

package br.com.fontenele.orcamentos.ui.clientes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.fontenele.orcamentos.AppContainer
import br.com.fontenele.orcamentos.data.local.UsuarioEntity
import br.com.fontenele.orcamentos.data.repo.DadosCliente
import br.com.fontenele.orcamentos.domain.Resultado
import br.com.fontenele.orcamentos.domain.Validacao
import br.com.fontenele.orcamentos.ui.componentes.BotaoPrincipal
import br.com.fontenele.orcamentos.ui.componentes.TextoBotao
import br.com.fontenele.orcamentos.ui.componentes.BarraSuperior
import br.com.fontenele.orcamentos.ui.componentes.CampoTexto
import br.com.fontenele.orcamentos.ui.componentes.CartaoErro
import br.com.fontenele.orcamentos.ui.componentes.TituloSecao
import kotlinx.coroutines.launch

/** UC13 – Cadastrar novo cliente (e editar os dados de um cliente existente). */
@Composable
fun ClienteFormTela(
    container: AppContainer,
    usuario: UsuarioEntity,
    clienteId: String?,
    aoVoltar: () -> Unit,
    aoSalvo: (String) -> Unit,
) {
    val escopo = rememberCoroutineScope()
    var nome by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var cpf by rememberSaveable { mutableStateOf("") }
    var telefone by rememberSaveable { mutableStateOf("") }
    var cep by rememberSaveable { mutableStateOf("") }
    var estado by rememberSaveable { mutableStateOf("") }
    var cidade by rememberSaveable { mutableStateOf("") }
    var rua by rememberSaveable { mutableStateOf("") }
    var numero by rememberSaveable { mutableStateOf("") }
    var carregado by rememberSaveable { mutableStateOf(clienteId == null) }
    var erro by remember { mutableStateOf<String?>(null) }
    var salvando by remember { mutableStateOf(false) }

    LaunchedEffect(clienteId) {
        if (clienteId != null && !carregado) {
            container.editora.cliente(clienteId)?.let { c ->
                nome = c.nome
                email = c.email
                cpf = if (c.cpf.isBlank()) "" else Validacao.formatarCpf(c.cpf)
                telefone = if (c.telefone.isBlank()) "" else Validacao.formatarTelefone(c.telefone)
                cep = if (c.cep.isBlank()) "" else Validacao.formatarCep(c.cep)
                estado = c.estado
                cidade = c.cidade
                rua = c.rua
                numero = c.numero
            }
            carregado = true
        }
    }

    fun salvar() {
        if (salvando) return
        salvando = true
        erro = null
        escopo.launch {
            val dados = DadosCliente(nome, email, cpf, telefone, cep, estado, cidade, rua, numero)
            when (val r = container.editora.salvarCliente(clienteId, dados, usuario)) {
                is Resultado.Ok -> aoSalvo(
                    if (clienteId == null) "Cliente cadastrado com sucesso!" else "Dados do cliente atualizados."
                )
                is Resultado.Erro -> erro = r.mensagem
            }
            salvando = false
        }
    }

    Scaffold(
        topBar = { BarraSuperior(if (clienteId == null) "Novo cliente" else "Editar cliente", aoVoltar = aoVoltar) },
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
            TituloSecao("Dados do cliente")
            CampoTexto(nome, { nome = it }, "Nome", obrigatorio = true)
            CampoTexto(email, { email = it }, "E-mail", obrigatorio = true, teclado = KeyboardType.Email)
            CampoTexto(cpf, { cpf = it }, "CPF", teclado = KeyboardType.Number)
            CampoTexto(telefone, { telefone = it }, "Telefone (com DDD)", teclado = KeyboardType.Phone)
            TituloSecao("Endereço (opcional)")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CampoTexto(cep, { cep = it }, "CEP", teclado = KeyboardType.Number, modifier = Modifier.weight(1f))
                CampoTexto(estado, { estado = it.take(2).uppercase() }, "UF", modifier = Modifier.weight(0.6f))
            }
            CampoTexto(cidade, { cidade = it }, "Cidade")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CampoTexto(rua, { rua = it }, "Rua", modifier = Modifier.weight(1f))
                CampoTexto(numero, { numero = it }, "Número", modifier = Modifier.weight(0.6f))
            }
            CartaoErro(erro)
            BotaoPrincipal(onClick = { salvar() }, enabled = !salvando && carregado, modifier = Modifier.fillMaxWidth()) {
                TextoBotao(if (clienteId == null) "Cadastrar cliente" else "Salvar alterações")
            }
        }
    }
}
