package br.com.fontenele.orcamentos.domain

/** Perfis de usuário (atores A1 e A2 da proposta). */
enum class Perfil(val rotulo: String) {
    ADMIN("Administrador"),
    FUNCIONARIO("Funcionário");

    companion object {
        fun de(valor: String?): Perfil = entries.firstOrNull { it.name == valor } ?: FUNCIONARIO
    }
}

/** Impressão do miolo, igual ao <select id="impressão"> do sistema web. */
enum class Impressao(val rotulo: String) {
    PB("Preto e branco"),
    COLORIDO("Colorido");

    companion object {
        fun de(valor: String?): Impressao? = entries.firstOrNull { it.name == valor }
    }
}

/** Forma de cobrança de um produto/serviço cadastrado. */
enum class Cobranca(val rotulo: String, val unidade: String) {
    FIXO("Valor fixo", "serviço"),
    POR_PAGINA("Por página", "pág."),
    POR_EXEMPLAR("Por exemplar", "exempl."),
    POR_PAGINA_EXEMPLAR("Por página de cada exemplar", "pág.×exempl.");

    companion object {
        fun de(valor: String?): Cobranca = entries.firstOrNull { it.name == valor } ?: FIXO
    }
}

/** Formatos (proporções) do sistema web, com os fatores da planilha da editora (coluna J, linhas 6 a 12). */
object Proporcoes {
    val formatos = listOf("10x15", "12x19", "14x21", "16x23", "17x24", "18x26", "21x28")

    /** 14x21 é o "padrão do cálculo" (fator 1). O Administrador pode alterar estes fatores no app. */
    val fatoresPadrao: Map<String, Double> = mapOf(
        "10x15" to 0.95,
        "12x19" to 0.98,
        "14x21" to 1.0,
        "16x23" to 1.1,
        "17x24" to 1.2,
        "18x26" to 1.3,
        "21x28" to 1.5,
    )
}

enum class StatusContrato(val rotulo: String) {
    ABERTO("Em aberto"),
    FECHADO("Fechado");

    companion object {
        fun de(valor: String?): StatusContrato = entries.firstOrNull { it.name == valor } ?: ABERTO
    }
}

enum class TipoDocumento(val rotulo: String) {
    ORCAMENTO("Orçamento"),
    CONTRATO("Contrato");

    companion object {
        fun de(valor: String?): TipoDocumento = entries.firstOrNull { it.name == valor } ?: ORCAMENTO
    }
}

/** Tipos de evento gravados no histórico do cliente (UC02). */
enum class TipoHistorico(val rotulo: String) {
    CADASTRO("Cliente cadastrado"),
    ATUALIZACAO("Dados atualizados"),
    ORCAMENTO("Orçamento pedido"),
    ORCAMENTO_EDITADO("Orçamento alterado"),
    CONTRATO("Contrato gerado"),
    CONTRATO_EDITADO("Contrato alterado"),
    FECHAMENTO("Contrato fechado"),
    PRONTO("Pedido pronto"),
    EMAIL("Envio por e-mail");

    companion object {
        fun de(valor: String?): TipoHistorico = entries.firstOrNull { it.name == valor } ?: ATUALIZACAO
    }
}

/** Resultado simples para operações que podem falhar com uma mensagem para o usuário. */
sealed interface Resultado<out T> {
    data class Ok<T>(val valor: T) : Resultado<T>
    data class Erro(val mensagem: String) : Resultado<Nothing>
}
