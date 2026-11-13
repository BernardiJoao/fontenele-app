package br.com.fontenele.orcamentos.domain

object Validacao {
    private val regexEmail = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    const val SENHA_MINIMA = 6

    fun soDigitos(texto: String): String = texto.filter { it.isDigit() }

    fun emailValido(email: String): Boolean = regexEmail.matches(email.trim())

    fun cepValido(cep: String): Boolean = soDigitos(cep).length == 8

    fun telefoneValido(telefone: String): Boolean = soDigitos(telefone).length in 10..11

    /** Validação dos dígitos verificadores do CPF. */
    fun cpfValido(cpf: String): Boolean {
        val d = soDigitos(cpf)
        if (d.length != 11 || d.all { it == d[0] }) return false
        val numeros = d.map { it - '0' }
        val dv1 = calcularDigito(numeros.subList(0, 9), 10)
        val dv2 = calcularDigito(numeros.subList(0, 10), 11)
        return numeros[9] == dv1 && numeros[10] == dv2
    }

    private fun calcularDigito(numeros: List<Int>, pesoInicial: Int): Int {
        val soma = numeros.mapIndexed { i, n -> n * (pesoInicial - i) }.sum()
        val resto = soma % 11
        return if (resto < 2) 0 else 11 - resto
    }

    /** Aceita "1.234,56", "1234,56", "1234.56" e "R$ 1.234,56". Retorna null se não for número. */
    fun lerDecimal(texto: String): Double? {
        var t = texto.trim().replace("R$", "").replace("%", "").replace(" ", "").replace(" ", "")
        if (t.isEmpty()) return null
        if (t.contains(',')) t = t.replace(".", "").replace(',', '.')
        return t.toDoubleOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() }
    }

    fun lerInteiro(texto: String): Int? = texto.trim().toIntOrNull()

    fun formatarCpf(cpf: String): String {
        val d = soDigitos(cpf)
        return if (d.length == 11) "${d.substring(0, 3)}.${d.substring(3, 6)}.${d.substring(6, 9)}-${d.substring(9)}" else cpf
    }

    fun formatarCep(cep: String): String {
        val d = soDigitos(cep)
        return if (d.length == 8) "${d.substring(0, 5)}-${d.substring(5)}" else cep
    }

    fun formatarTelefone(telefone: String): String {
        val d = soDigitos(telefone)
        return when (d.length) {
            11 -> "(${d.substring(0, 2)}) ${d.substring(2, 7)}-${d.substring(7)}"
            10 -> "(${d.substring(0, 2)}) ${d.substring(2, 6)}-${d.substring(6)}"
            else -> telefone
        }
    }
}
