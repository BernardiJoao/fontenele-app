package br.com.fontenele.orcamentos.domain

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** As senhas nunca são guardadas em texto puro: usamos PBKDF2 com salt aleatório por usuário. */
object Seguranca {
    private const val ITERACOES = 12_000
    private const val TAMANHO_CHAVE_BITS = 256
    private const val ALGORITMO = "PBKDF2WithHmacSHA256"

    fun novoSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun hash(senha: String, salt: String): String {
        val spec = PBEKeySpec(senha.toCharArray(), Base64.getDecoder().decode(salt), ITERACOES, TAMANHO_CHAVE_BITS)
        val bytes = SecretKeyFactory.getInstance(ALGORITMO).generateSecret(spec).encoded
        spec.clearPassword()
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun confere(senha: String, salt: String, hashEsperado: String): Boolean =
        MessageDigest.isEqual(hash(senha, salt).toByteArray(), hashEsperado.toByteArray())
}
