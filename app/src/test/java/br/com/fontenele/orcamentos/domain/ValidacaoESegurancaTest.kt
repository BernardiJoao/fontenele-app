package br.com.fontenele.orcamentos.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidacaoESegurancaTest {

    @Test
    fun validaCpf() {
        assertTrue(Validacao.cpfValido("529.982.247-25"))
        assertFalse(Validacao.cpfValido("529.982.247-24"))
        assertFalse(Validacao.cpfValido("111.111.111-11"))
    }

    @Test
    fun validaEmailCepETelefone() {
        assertTrue(Validacao.emailValido("cliente@editora.com.br"))
        assertFalse(Validacao.emailValido("cliente@"))
        assertTrue(Validacao.cepValido("80230-901"))
        assertTrue(Validacao.telefoneValido("(41) 99999-0000"))
    }

    @Test
    fun leDecimaisNoFormatoBrasileiro() {
        assertEquals(1234.56, Validacao.lerDecimal("R$ 1.234,56")!!, 0.0001)
        assertEquals(0.06, Validacao.lerDecimal("0,06")!!, 0.0001)
        assertEquals(12.5, Validacao.lerDecimal("12.5")!!, 0.0001)
        assertNull(Validacao.lerDecimal("abc"))
    }

    @Test
    fun hashDeSenhaConfereSomenteComASenhaCerta() {
        val salt = Seguranca.novoSalt()
        val hash = Seguranca.hash("segredo123", salt)
        assertTrue(Seguranca.confere("segredo123", salt, hash))
        assertFalse(Seguranca.confere("outra", salt, hash))
    }
}
