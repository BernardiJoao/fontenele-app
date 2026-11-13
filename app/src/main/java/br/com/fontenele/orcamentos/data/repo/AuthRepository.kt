package br.com.fontenele.orcamentos.data.repo

import br.com.fontenele.orcamentos.data.local.UsuarioDao
import br.com.fontenele.orcamentos.data.local.UsuarioEntity
import br.com.fontenele.orcamentos.data.prefs.Preferencias
import br.com.fontenele.orcamentos.domain.Perfil
import br.com.fontenele.orcamentos.domain.Resultado
import br.com.fontenele.orcamentos.domain.Seguranca
import br.com.fontenele.orcamentos.domain.Validacao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Registro de usuários com separação de perfil, login (UC01) e sessão. */
class AuthRepository(
    private val dao: UsuarioDao,
    private val prefs: Preferencias,
) {
    private val _usuario = MutableStateFlow<UsuarioEntity?>(null)

    /** Usuário logado no momento (null = ninguém logado). */
    val usuario: StateFlow<UsuarioEntity?> = _usuario.asStateFlow()

    fun observarUsuarios(): Flow<List<UsuarioEntity>> = dao.observarTodos()

    suspend fun existeUsuario(): Boolean = dao.contar() > 0

    /** Restaura a sessão salva quando o usuário marcou "Manter conectado". */
    suspend fun restaurarSessao(): UsuarioEntity? {
        val id = prefs.usuarioLogadoId ?: return null
        val u = dao.porId(id)
        return if (u != null && u.ativo) {
            _usuario.value = u
            u
        } else {
            prefs.usuarioLogadoId = null
            null
        }
    }

    /** UC01 – Fazer login, com as mensagens do fluxo de eventos da proposta. */
    suspend fun login(email: String, senha: String, manterConectado: Boolean): Resultado<UsuarioEntity> {
        if (email.isBlank() || senha.isBlank()) {
            return Resultado.Erro("É preciso preencher todos os campos para prosseguir")
        }
        val u = dao.porEmail(email.trim()) ?: return Resultado.Erro("Este e-mail não existe!")
        // O hash PBKDF2 é custoso de propósito: roda fora da thread principal
        val senhaCorreta = withContext(Dispatchers.Default) { Seguranca.confere(senha, u.salt, u.senhaHash) }
        if (!senhaCorreta) return Resultado.Erro("Senha incorreta!")
        if (!u.ativo) return Resultado.Erro("Este usuário foi desativado. Procure o Administrador.")
        iniciarSessao(u, manterConectado)
        return Resultado.Ok(u)
    }

    /** Login pelo leitor de digital: só funciona para quem ativou a biometria neste aparelho. */
    suspend fun loginBiometrico(): Resultado<UsuarioEntity> {
        val id = prefs.usuarioBiometriaId ?: return Resultado.Erro("A biometria não está ativada neste aparelho.")
        val u = dao.porId(id)
        if (u == null || !u.ativo) {
            prefs.usuarioBiometriaId = null
            return Resultado.Erro("Usuário da biometria não encontrado. Entre com e-mail e senha.")
        }
        iniciarSessao(u, manterConectado = true)
        return Resultado.Ok(u)
    }

    fun biometriaAtivadaParaAlguem(): Boolean = prefs.usuarioBiometriaId != null

    fun biometriaAtivadaPara(usuarioId: String): Boolean = prefs.usuarioBiometriaId == usuarioId

    fun definirBiometria(usuarioId: String, ativar: Boolean) {
        prefs.usuarioBiometriaId = if (ativar) usuarioId else null
    }

    /** Login feito pelo servidor (conta criada em outro celular): guarda a conta aqui e já entra. */
    suspend fun entrarComContaDoServidor(u: UsuarioEntity, manterConectado: Boolean): Resultado<UsuarioEntity> {
        if (!u.ativo) return Resultado.Erro("Este usuário foi desativado. Procure o Administrador.")
        dao.salvar(u)
        dao.apagarOutrosComEmail(u.email, u.id)
        iniciarSessao(u, manterConectado)
        return Resultado.Ok(u)
    }

    /** Confere os campos do cadastro antes de mandar para o servidor (null = tudo certo). */
    fun validarCadastro(nome: String, email: String, senha: String, confirmacao: String): String? = when {
        nome.isBlank() || email.isBlank() || senha.isBlank() || confirmacao.isBlank() -> "Preencha todos os campos."
        !Validacao.emailValido(email) -> "Informe um e-mail válido."
        senha.length < Validacao.SENHA_MINIMA -> "A senha deve ter pelo menos ${Validacao.SENHA_MINIMA} caracteres."
        senha != confirmacao -> "As senhas não conferem."
        else -> null
    }

    private fun iniciarSessao(u: UsuarioEntity, manterConectado: Boolean) {
        _usuario.value = u
        prefs.usuarioLogadoId = if (manterConectado) u.id else null
    }

    fun sair() {
        _usuario.value = null
        prefs.usuarioLogadoId = null
    }

    /**
     * Registro de usuário.
     *  - Cadastro público: normalmente é feito no servidor (SyncRepository.cadastrarNoServidor), que decide o perfil.
     *    Este caminho local só é usado quando não há servidor ("criar só neste celular").
     *  - Cadastro feito pelo Administrador (tela Usuários): ele escolhe o perfil.
     */
    suspend fun cadastrar(
        nome: String,
        email: String,
        senha: String,
        confirmacao: String,
        perfilEscolhido: Perfil?,
    ): Resultado<UsuarioEntity> {
        validarCadastro(nome, email, senha, confirmacao)?.let { return Resultado.Erro(it) }
        if (dao.porEmail(email.trim()) != null) return Resultado.Erro("Este e-mail já está cadastrado.")

        val perfil = when {
            dao.contar() == 0 -> Perfil.ADMIN
            perfilEscolhido != null && _usuario.value?.perfil == Perfil.ADMIN.name -> perfilEscolhido
            else -> Perfil.FUNCIONARIO
        }
        val salt = Seguranca.novoSalt()
        val novo = UsuarioEntity(
            nome = nome.trim(),
            email = email.trim().lowercase(),
            senhaHash = withContext(Dispatchers.Default) { Seguranca.hash(senha, salt) },
            salt = salt,
            perfil = perfil.name,
        )
        dao.salvar(novo)
        return Resultado.Ok(novo)
    }

    /** Cadastro público já entra logado (objetivo dos atores: "registrar-se e manter logado"). */
    suspend fun cadastrarEEntrar(nome: String, email: String, senha: String, confirmacao: String): Resultado<UsuarioEntity> {
        val r = cadastrar(nome, email, senha, confirmacao, perfilEscolhido = null)
        if (r is Resultado.Ok) iniciarSessao(r.valor, manterConectado = true)
        return r
    }

    /** Somente o Administrador altera perfis; sempre deve sobrar pelo menos um Administrador ativo. */
    suspend fun alterarPerfil(alvo: UsuarioEntity, novoPerfil: Perfil): Resultado<UsuarioEntity> {
        if (_usuario.value?.perfil != Perfil.ADMIN.name) return Resultado.Erro("Acesso restrito ao Administrador.")
        if (alvo.perfil == Perfil.ADMIN.name && novoPerfil != Perfil.ADMIN && alvo.ativo && dao.contarAdminsAtivos() <= 1) {
            return Resultado.Erro("É preciso manter pelo menos um Administrador.")
        }
        val atualizado = alvo.copy(perfil = novoPerfil.name, atualizadoEm = System.currentTimeMillis())
        dao.salvar(atualizado)
        return Resultado.Ok(atualizado)
    }

    suspend fun alterarAtivo(alvo: UsuarioEntity, ativo: Boolean): Resultado<UsuarioEntity> {
        if (_usuario.value?.perfil != Perfil.ADMIN.name) return Resultado.Erro("Acesso restrito ao Administrador.")
        if (alvo.id == _usuario.value?.id) return Resultado.Erro("Você não pode desativar a própria conta.")
        if (!ativo && alvo.perfil == Perfil.ADMIN.name && dao.contarAdminsAtivos() <= 1) {
            return Resultado.Erro("É preciso manter pelo menos um Administrador ativo.")
        }
        val atualizado = alvo.copy(ativo = ativo, atualizadoEm = System.currentTimeMillis())
        dao.salvar(atualizado)
        return Resultado.Ok(atualizado)
    }

    /**
     * Recarrega o usuário logado depois de uma sincronização (perfil pode ter mudado em outro aparelho).
     * Se a conta deste aparelho foi trocada pela conta do servidor com o mesmo e-mail, continua logado nela.
     */
    suspend fun recarregarUsuarioLogado() {
        val atual = _usuario.value ?: return
        val u = dao.porId(atual.id) ?: dao.porEmail(atual.email)
        if (u == null || !u.ativo) {
            sair()
            return
        }
        if (u.id != atual.id) {
            if (prefs.usuarioLogadoId == atual.id) prefs.usuarioLogadoId = u.id
            if (prefs.usuarioBiometriaId == atual.id) prefs.usuarioBiometriaId = u.id
        }
        _usuario.value = u
    }
}
