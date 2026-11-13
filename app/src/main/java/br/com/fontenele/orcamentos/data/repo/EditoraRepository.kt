package br.com.fontenele.orcamentos.data.repo

import androidx.room.withTransaction
import br.com.fontenele.orcamentos.data.local.AppDatabase
import br.com.fontenele.orcamentos.data.local.ClienteEntity
import br.com.fontenele.orcamentos.data.local.ContratoEntity
import br.com.fontenele.orcamentos.data.local.EmailLogEntity
import br.com.fontenele.orcamentos.data.local.HistoricoEntity
import br.com.fontenele.orcamentos.data.local.OrcamentoEntity
import br.com.fontenele.orcamentos.data.local.ParametroEntity
import br.com.fontenele.orcamentos.data.local.ProdutoEntity
import br.com.fontenele.orcamentos.data.local.UsuarioEntity
import br.com.fontenele.orcamentos.domain.Cobranca
import br.com.fontenele.orcamentos.domain.EntradaOrcamento
import br.com.fontenele.orcamentos.domain.Formatos
import br.com.fontenele.orcamentos.domain.ParametrosCalculo
import br.com.fontenele.orcamentos.domain.Perfil
import br.com.fontenele.orcamentos.domain.ProdutoPreco
import br.com.fontenele.orcamentos.domain.Proporcoes
import br.com.fontenele.orcamentos.domain.Resultado
import br.com.fontenele.orcamentos.domain.ResultadoOrcamento
import br.com.fontenele.orcamentos.domain.TabelaValores
import br.com.fontenele.orcamentos.domain.StatusContrato
import br.com.fontenele.orcamentos.domain.TipoDocumento
import br.com.fontenele.orcamentos.domain.TipoHistorico
import br.com.fontenele.orcamentos.domain.Validacao
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class DadosCliente(
    val nome: String,
    val email: String,
    val cpf: String = "",
    val telefone: String = "",
    val cep: String = "",
    val estado: String = "",
    val cidade: String = "",
    val rua: String = "",
    val numero: String = "",
)

data class DadosOrcamento(
    val clienteNome: String,
    val clienteEmail: String,
    val entrada: EntradaOrcamento,
    val validadeDias: Int,
    val observacoes: String,
)

data class DadosContrato(
    val orcamentoId: String?,
    val nome: String,
    val cpf: String,
    val email: String,
    val telefone: String,
    val cep: String,
    val estado: String,
    val cidade: String,
    val rua: String,
    val numero: String,
    val tiragem: Int?,
    val valorTexto: String,
    val prazoTexto: String,
    val formaPagamento: String,
    val clausulasAdicionais: String,
)

/** Regras de negócio da editora sobre o banco interno (preços, clientes, orçamentos, contratos e histórico). */
class EditoraRepository(
    private val db: AppDatabase,
    private val gson: Gson,
    private val lerTabelaCsv: () -> String,
) {
    private val clienteDao = db.clienteDao()
    private val produtoDao = db.produtoDao()
    private val orcamentoDao = db.orcamentoDao()
    private val contratoDao = db.contratoDao()
    private val historicoDao = db.historicoDao()
    private val emailLogDao = db.emailLogDao()
    private val parametroDao = db.parametroDao()

    @Volatile
    private var tabelaCache: TabelaValores? = null

    private fun agora() = System.currentTimeMillis()

    // ------------------------------------------------------------------ Tabela de valores da editora

    /** Carrega (uma vez) a tabela de valores que vem da planilha da editora (assets/tabela_valores.csv). */
    suspend fun tabela(): TabelaValores =
        tabelaCache ?: withContext(Dispatchers.IO) { TabelaValores.carregar(lerTabelaCsv()) }.also { tabelaCache = it }

    // ------------------------------------------------------------------ Parâmetros de cálculo (UC04)

    fun observarParametros(): Flow<ParametrosCalculo> =
        parametroDao.observarTodos().map { lista -> ParametrosCalculo.deMapa(lista.associate { it.id to it.valor }) }

    suspend fun parametros(): ParametrosCalculo =
        ParametrosCalculo.deMapa(parametroDao.todos().associate { it.id to it.valor })

    /** O Administrador altera o fator de correção (inflação), o desconto à vista e os fatores de formato/colorido. */
    suspend fun salvarParametros(
        usuario: UsuarioEntity,
        coeficienteTexto: String,
        descontoTexto: String,
        coloridoTexto: String,
        formatosTexto: Map<String, String>,
    ): Resultado<ParametrosCalculo> {
        if (usuario.perfil != Perfil.ADMIN.name) {
            return Resultado.Erro("Somente o Administrador pode alterar os valores do cálculo.")
        }
        val coeficiente = Validacao.lerDecimal(coeficienteTexto)
        if (coeficiente == null || coeficiente <= 0.0 || coeficiente > 10.0) {
            return Resultado.Erro("Fator de correção inválido: informe um número maior que zero (ex.: 1,5).")
        }
        val desconto = Validacao.lerDecimal(descontoTexto)
        if (desconto == null || desconto < 0.0 || desconto > 100.0) {
            return Resultado.Erro("Desconto à vista inválido: informe um percentual de 0 a 100.")
        }
        val colorido = if (coloridoTexto.isBlank()) 0.0 else Validacao.lerDecimal(coloridoTexto)
        if (colorido == null || colorido < 0.0) {
            return Resultado.Erro("Fator da impressão colorida inválido (deixe em branco se não houver).")
        }
        val formatos = LinkedHashMap<String, Double>()
        for (formato in Proporcoes.formatos) {
            val fator = Validacao.lerDecimal(formatosTexto[formato].orEmpty())
            if (fator == null || fator <= 0.0) return Resultado.Erro("Fator inválido para o formato $formato.")
            formatos[formato] = fator
        }
        val momento = agora()
        db.withTransaction {
            parametroDao.salvar(ParametroEntity(ParametrosCalculo.CHAVE_COEFICIENTE, coeficiente, momento))
            parametroDao.salvar(ParametroEntity(ParametrosCalculo.CHAVE_DESCONTO, desconto, momento))
            parametroDao.salvar(ParametroEntity(ParametrosCalculo.CHAVE_COLORIDO, colorido, momento))
            formatos.forEach { (formato, fator) ->
                parametroDao.salvar(ParametroEntity(ParametrosCalculo.PREFIXO_FORMATO + formato, fator, momento))
            }
        }
        return Resultado.Ok(ParametrosCalculo(coeficiente, desconto, colorido, formatos))
    }

    // ------------------------------------------------------------------ Serviços adicionais (UC03 e UC04)

    fun observarProdutos(): Flow<List<ProdutoEntity>> = produtoDao.observarTodos()

    fun observarProdutosAtivos(): Flow<List<ProdutoEntity>> = produtoDao.observarAtivos()

    suspend fun precosAtuais(): List<ProdutoPreco> = produtoDao.ativos().map {
        ProdutoPreco(it.id, it.nome, it.preco, Cobranca.de(it.cobranca))
    }

    suspend fun salvarProduto(
        id: String?,
        nome: String,
        descricao: String,
        precoTexto: String,
        cobranca: Cobranca,
        ativo: Boolean,
        usuario: UsuarioEntity,
    ): Resultado<ProdutoEntity> {
        if (usuario.perfil != Perfil.ADMIN.name) {
            return Resultado.Erro("Somente o Administrador pode cadastrar ou alterar produtos e preços.")
        }
        if (nome.isBlank() || descricao.isBlank() || precoTexto.isBlank()) {
            return Resultado.Erro("Preencha nome, descrição e preço do produto.")
        }
        val preco = Validacao.lerDecimal(precoTexto)
            ?: return Resultado.Erro("Valor inválido: informe um número (ex.: 12,50).")
        if (preco < 0) return Resultado.Erro("Valor inválido: o preço não pode ser negativo.")

        val existente = id?.let { produtoDao.porId(it) }
        val produto = existente?.copy(
            nome = nome.trim(),
            descricao = descricao.trim(),
            preco = preco,
            cobranca = cobranca.name,
            ativo = ativo,
            atualizadoEm = agora(),
        ) ?: ProdutoEntity(
            nome = nome.trim(),
            descricao = descricao.trim(),
            preco = preco,
            cobranca = cobranca.name,
            ativo = ativo,
        )
        produtoDao.salvar(produto)
        return Resultado.Ok(produto)
    }

    // ------------------------------------------------------------------ Clientes (UC02, UC13 e UC14)

    fun observarClientes(): Flow<List<ClienteEntity>> = clienteDao.observarTodos()

    fun observarCliente(id: String): Flow<ClienteEntity?> = clienteDao.observarPorId(id)

    fun observarHistorico(clienteId: String): Flow<List<HistoricoEntity>> = historicoDao.observarPorCliente(clienteId)

    fun observarQuantidadeClientes(): Flow<Int> = clienteDao.observarQuantidade()

    suspend fun cliente(id: String): ClienteEntity? = clienteDao.porId(id)

    suspend fun salvarCliente(id: String?, d: DadosCliente, usuario: UsuarioEntity): Resultado<ClienteEntity> {
        if (d.nome.isBlank() || d.email.isBlank()) return Resultado.Erro("Nome e e-mail do cliente são obrigatórios.")
        if (!Validacao.emailValido(d.email)) return Resultado.Erro("Informe um e-mail válido.")
        if (d.cpf.isNotBlank() && !Validacao.cpfValido(d.cpf)) return Resultado.Erro("CPF inválido.")
        if (d.telefone.isNotBlank() && !Validacao.telefoneValido(d.telefone)) {
            return Resultado.Erro("Telefone inválido: informe DDD + número.")
        }
        if (d.cep.isNotBlank() && !Validacao.cepValido(d.cep)) return Resultado.Erro("CEP inválido: informe 8 dígitos.")

        return db.withTransaction<Resultado<ClienteEntity>> {
            val mesmoEmail = clienteDao.porEmail(d.email.trim())
            if (mesmoEmail != null && mesmoEmail.id != id) {
                return@withTransaction Resultado.Erro("Já existe um cliente com este e-mail.")
            }
            val existente = id?.let { clienteDao.porId(it) }
            val base = existente ?: ClienteEntity(nome = d.nome.trim(), email = d.email.trim().lowercase())
            val cliente = base.copy(
                nome = d.nome.trim(),
                email = d.email.trim().lowercase(),
                cpf = Validacao.soDigitos(d.cpf),
                telefone = Validacao.soDigitos(d.telefone),
                cep = Validacao.soDigitos(d.cep),
                estado = d.estado.trim().uppercase(),
                cidade = d.cidade.trim(),
                rua = d.rua.trim(),
                numero = d.numero.trim(),
                atualizadoEm = agora(),
            )
            clienteDao.salvar(cliente)
            registrarHistorico(
                clienteId = cliente.id,
                tipo = if (existente == null) TipoHistorico.CADASTRO else TipoHistorico.ATUALIZACAO,
                descricao = if (existente == null) "Cliente cadastrado manualmente" else "Dados do cliente atualizados",
                usuarioNome = usuario.nome,
            )
            Resultado.Ok(cliente)
        }
    }

    /**
     * UC13 (fluxo 2.A): procura o cliente pelo e-mail; se não existir, cria automaticamente.
     * Deve ser chamado dentro de uma transação.
     */
    private suspend fun obterOuCriarCliente(
        nome: String,
        email: String,
        origem: String,
        usuarioNome: String,
        atualizar: (ClienteEntity) -> ClienteEntity = { it },
    ): ClienteEntity {
        val existente = clienteDao.porEmail(email.trim())
        if (existente != null) {
            val atualizado = atualizar(existente)
            if (atualizado != existente) clienteDao.salvar(atualizado.copy(atualizadoEm = agora()))
            return atualizado
        }
        val novo = atualizar(ClienteEntity(nome = nome.trim(), email = email.trim().lowercase()))
        clienteDao.salvar(novo)
        registrarHistorico(
            clienteId = novo.id,
            tipo = TipoHistorico.CADASTRO,
            descricao = "Cliente cadastrado automaticamente ao gerar $origem",
            usuarioNome = usuarioNome,
        )
        return novo
    }

    // ------------------------------------------------------------------ Orçamentos (UC05, UC06, UC11, UC15, UC17)

    fun observarOrcamentos(): Flow<List<OrcamentoEntity>> = orcamentoDao.observarTodos()

    fun observarQuantidadeOrcamentos(): Flow<Int> = orcamentoDao.observarQuantidade()

    suspend fun orcamento(id: String): OrcamentoEntity? = orcamentoDao.porId(id)

    /** Linhas por tiragem salvas no orçamento (null se o JSON estiver corrompido). */
    fun resultadoDoOrcamento(o: OrcamentoEntity): ResultadoOrcamento? = runCatching {
        gson.fromJson(o.itensJson, ResultadoOrcamento::class.java)
    }.getOrNull()

    suspend fun salvarOrcamento(
        idExistente: String?,
        d: DadosOrcamento,
        resultado: ResultadoOrcamento,
        usuario: UsuarioEntity,
    ): OrcamentoEntity = db.withTransaction {
        val cliente = obterOuCriarCliente(d.clienteNome, d.clienteEmail, "um orçamento", usuario.nome)
        val existente = idExistente?.let { orcamentoDao.porId(it) }
        val momento = agora()
        val base = OrcamentoEntity(
            numero = existente?.numero ?: orcamentoDao.proximoNumero(),
            clienteId = cliente.id,
            clienteNome = d.clienteNome.trim(),
            clienteEmail = d.clienteEmail.trim().lowercase(),
            impressao = d.entrada.impressao.name,
            paginas = d.entrada.paginas,
            proporcao = d.entrada.proporcao,
            tiragens = resultado.linhas.joinToString(",") { it.tiragem.toString() },
            coeficienteInflacao = d.entrada.coeficienteInflacao,
            descontoAVistaPercentual = resultado.descontoAVistaPercentual,
            adicionaisIds = d.entrada.adicionaisIds.joinToString(","),
            itensJson = gson.toJson(resultado),
            total = resultado.linhas.first().total,
            validadeDias = d.validadeDias,
            observacoes = d.observacoes.trim(),
            usuarioId = existente?.usuarioId ?: usuario.id,
            usuarioNome = existente?.usuarioNome ?: usuario.nome,
            criadoEm = existente?.criadoEm ?: momento,
            atualizadoEm = momento,
        )
        val orcamento = if (existente != null) base.copy(id = existente.id) else base
        orcamentoDao.salvar(orcamento)

        val numero = Formatos.numeroDocumento(orcamento.numero)
        if (existente == null) {
            // UC17 – Registrar orçamento pedido: soma 1 no contador do cliente
            clienteDao.salvar(cliente.copy(orcamentosPedidos = cliente.orcamentosPedidos + 1, atualizadoEm = momento))
            registrarHistorico(
                clienteId = cliente.id,
                tipo = TipoHistorico.ORCAMENTO,
                descricao = "Orçamento nº $numero – ${orcamento.paginas} págs., ${orcamento.proporcao}, " +
                    Formatos.listaTiragens(resultado.linhas.map { it.tiragem }),
                valor = orcamento.total,
                referenciaId = orcamento.id,
                usuarioNome = usuario.nome,
            )
        } else {
            registrarHistorico(
                clienteId = cliente.id,
                tipo = TipoHistorico.ORCAMENTO_EDITADO,
                descricao = "Orçamento nº $numero alterado",
                valor = orcamento.total,
                referenciaId = orcamento.id,
                usuarioNome = usuario.nome,
            )
        }
        orcamento
    }

    // ------------------------------------------------------------------ Contratos (UC07, UC12, UC16, UC18)

    fun observarContratos(): Flow<List<ContratoEntity>> = contratoDao.observarTodos()

    fun observarContratosAbertos(): Flow<List<ContratoEntity>> = contratoDao.observarAbertos()

    fun observarQuantidadeContratosFechados(): Flow<Int> = contratoDao.observarQuantidadeFechados()

    suspend fun contrato(id: String): ContratoEntity? = contratoDao.porId(id)

    private fun validarContrato(d: DadosContrato): Resultado<Pair<Double, Int>> {
        val obrigatorios = listOf(
            d.nome, d.cpf, d.email, d.telefone, d.cep, d.estado, d.cidade, d.rua, d.numero,
            d.valorTexto, d.prazoTexto, d.formaPagamento,
        )
        if (obrigatorios.any { it.isBlank() }) return Resultado.Erro("Preencha todos os campos")
        if (!Validacao.emailValido(d.email)) return Resultado.Erro("Informe um e-mail válido.")
        if (!Validacao.cpfValido(d.cpf)) return Resultado.Erro("CPF inválido.")
        if (!Validacao.telefoneValido(d.telefone)) return Resultado.Erro("Telefone inválido: informe DDD + número.")
        if (!Validacao.cepValido(d.cep)) return Resultado.Erro("CEP inválido: informe 8 dígitos.")
        val valor = Validacao.lerDecimal(d.valorTexto)
        if (valor == null || valor <= 0.0) return Resultado.Erro("Informe um valor de contrato válido.")
        val prazo = Validacao.lerInteiro(d.prazoTexto)
        if (prazo == null || prazo !in 1..3650) return Resultado.Erro("Informe o prazo de entrega em dias (1 a 3650).")
        return Resultado.Ok(valor to prazo)
    }

    suspend fun salvarContrato(
        idExistente: String?,
        d: DadosContrato,
        usuario: UsuarioEntity,
    ): Resultado<ContratoEntity> {
        val validacao = validarContrato(d)
        if (validacao is Resultado.Erro) return validacao
        val (valor, prazo) = (validacao as Resultado.Ok).valor

        val contrato = db.withTransaction {
            // UC16 – os dados informados no contrato atualizam o cadastro do cliente
            val cliente = obterOuCriarCliente(d.nome, d.email, "um contrato", usuario.nome) { c ->
                c.copy(
                    cpf = Validacao.soDigitos(d.cpf),
                    telefone = Validacao.soDigitos(d.telefone),
                    cep = Validacao.soDigitos(d.cep),
                    estado = d.estado.trim().uppercase(),
                    cidade = d.cidade.trim(),
                    rua = d.rua.trim(),
                    numero = d.numero.trim(),
                )
            }
            val existente = idExistente?.let { contratoDao.porId(it) }
            val momento = agora()
            val base = ContratoEntity(
                numero = existente?.numero ?: contratoDao.proximoNumero(),
                clienteId = cliente.id,
                orcamentoId = d.orcamentoId ?: existente?.orcamentoId,
                tiragem = d.tiragem ?: existente?.tiragem,
                nome = d.nome.trim(),
                cpf = Validacao.soDigitos(d.cpf),
                email = d.email.trim().lowercase(),
                telefone = Validacao.soDigitos(d.telefone),
                cep = Validacao.soDigitos(d.cep),
                estado = d.estado.trim().uppercase(),
                cidade = d.cidade.trim(),
                rua = d.rua.trim(),
                numeroEndereco = d.numero.trim(),
                valor = valor,
                prazoEntregaDias = prazo,
                formaPagamento = d.formaPagamento.trim(),
                clausulasAdicionais = d.clausulasAdicionais.trim(),
                status = existente?.status ?: StatusContrato.ABERTO.name,
                fechadoEm = existente?.fechadoEm,
                prontoEm = existente?.prontoEm,
                fotoPronto = existente?.fotoPronto,
                usuarioId = existente?.usuarioId ?: usuario.id,
                usuarioNome = existente?.usuarioNome ?: usuario.nome,
                criadoEm = existente?.criadoEm ?: momento,
                atualizadoEm = momento,
            )
            val salvo = if (existente != null) base.copy(id = existente.id) else base
            contratoDao.salvar(salvo)
            registrarHistorico(
                clienteId = cliente.id,
                tipo = if (existente == null) TipoHistorico.CONTRATO else TipoHistorico.CONTRATO_EDITADO,
                descricao = "Contrato nº ${Formatos.numeroDocumento(salvo.numero)} " +
                    if (existente == null) "gerado" else "alterado",
                valor = salvo.valor,
                referenciaId = salvo.id,
                usuarioNome = usuario.nome,
            )
            salvo
        }
        return Resultado.Ok(contrato)
    }

    /** UC18 – Registrar contrato fechado informando o e-mail do cliente (como na aba "Contratos" do sistema web). */
    suspend fun registrarFechamentoPorEmail(email: String, usuario: UsuarioEntity): Resultado<ContratoEntity> {
        if (email.isBlank()) return Resultado.Erro("Informe o e-mail do cliente com contrato fechado.")
        val cliente = clienteDao.porEmail(email.trim())
            ?: return Resultado.Erro("Nenhum cliente cadastrado com este e-mail.")
        val contrato = contratoDao.ultimoAbertoDoCliente(cliente.id)
            ?: return Resultado.Erro("Este cliente não tem contrato em aberto. Gere o contrato primeiro.")
        return fechar(contrato, usuario)
    }

    suspend fun registrarFechamento(contratoId: String, usuario: UsuarioEntity): Resultado<ContratoEntity> {
        val contrato = contratoDao.porId(contratoId) ?: return Resultado.Erro("Contrato não encontrado.")
        if (contrato.status == StatusContrato.FECHADO.name) return Resultado.Erro("Este contrato já está fechado.")
        return fechar(contrato, usuario)
    }

    private suspend fun fechar(contrato: ContratoEntity, usuario: UsuarioEntity): Resultado<ContratoEntity> =
        db.withTransaction {
            val momento = agora()
            val fechado = contrato.copy(
                status = StatusContrato.FECHADO.name,
                fechadoEm = momento,
                atualizadoEm = momento,
            )
            contratoDao.salvar(fechado)
            val cliente = clienteDao.porId(contrato.clienteId)
            if (cliente != null) {
                clienteDao.salvar(cliente.copy(contratosFechados = cliente.contratosFechados + 1, atualizadoEm = momento))
            }
            registrarHistorico(
                clienteId = contrato.clienteId,
                tipo = TipoHistorico.FECHAMENTO,
                descricao = "Contrato nº ${Formatos.numeroDocumento(contrato.numero)} fechado",
                valor = contrato.valor,
                referenciaId = contrato.id,
                usuarioNome = usuario.nome,
            )
            Resultado.Ok(fechado)
        }

    // ------------------------------------------------------------------ Pedido pronto (foto pela câmera)

    /** Pedidos do cliente (contratos), para a tela de cadastro do cliente. */
    fun observarPedidosDoCliente(clienteId: String): Flow<List<ContratoEntity>> = contratoDao.observarPorCliente(clienteId)

    /**
     * Guarda a foto do pedido pronto. Na primeira foto o pedido passa a "pronto" e entra no histórico;
     * as fotos seguintes só substituem a anterior.
     */
    suspend fun registrarFotoPedidoPronto(
        contratoId: String,
        nomeArquivo: String,
        usuario: UsuarioEntity,
    ): Resultado<ContratoEntity> {
        val contrato = contratoDao.porId(contratoId) ?: return Resultado.Erro("Pedido não encontrado.")
        if (contrato.status != StatusContrato.FECHADO.name) {
            return Resultado.Erro("Registre o fechamento do contrato antes de marcar o pedido como pronto.")
        }
        return db.withTransaction {
            val momento = agora()
            val primeiraFoto = contrato.prontoEm == null
            val atualizado = contrato.copy(
                prontoEm = contrato.prontoEm ?: momento,
                fotoPronto = nomeArquivo,
                atualizadoEm = momento,
            )
            contratoDao.salvar(atualizado)
            registrarHistorico(
                clienteId = contrato.clienteId,
                tipo = TipoHistorico.PRONTO,
                descricao = "Pedido nº ${Formatos.numeroDocumento(contrato.numero)} " +
                    if (primeiraFoto) "pronto (foto registrada)" else "com nova foto",
                valor = null,
                referenciaId = contrato.id,
                usuarioNome = usuario.nome,
            )
            Resultado.Ok(atualizado)
        }
    }

    // ------------------------------------------------------------------ Envio por e-mail (UC09 e UC10)

    fun observarLogsEmail(): Flow<List<EmailLogEntity>> = emailLogDao.observarRecentes()

    suspend fun registrarEnvioEmail(
        tipo: TipoDocumento,
        documentoId: String,
        numero: Int,
        clienteId: String,
        destinatario: String,
        sucesso: Boolean,
        mensagem: String,
        usuarioNome: String,
    ) {
        emailLogDao.salvar(
            EmailLogEntity(
                documentoTipo = tipo.name,
                documentoId = documentoId,
                numeroDocumento = numero,
                destinatario = destinatario,
                sucesso = sucesso,
                mensagem = mensagem,
                usuarioNome = usuarioNome,
            )
        )
        registrarHistorico(
            clienteId = clienteId,
            tipo = TipoHistorico.EMAIL,
            descricao = "${tipo.rotulo} nº ${Formatos.numeroDocumento(numero)}: $mensagem",
            referenciaId = documentoId,
            usuarioNome = usuarioNome,
        )
    }

    // ------------------------------------------------------------------ Histórico (UC02)

    private suspend fun registrarHistorico(
        clienteId: String,
        tipo: TipoHistorico,
        descricao: String,
        valor: Double? = null,
        referenciaId: String? = null,
        usuarioNome: String,
    ) {
        historicoDao.salvar(
            HistoricoEntity(
                clienteId = clienteId,
                tipo = tipo.name,
                descricao = descricao,
                valor = valor,
                referenciaId = referenciaId,
                usuarioNome = usuarioNome,
            )
        )
    }
}
