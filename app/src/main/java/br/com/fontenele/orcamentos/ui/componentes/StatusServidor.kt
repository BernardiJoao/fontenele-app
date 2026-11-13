package br.com.fontenele.orcamentos.ui.componentes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.fontenele.orcamentos.data.repo.EstadoSync
import br.com.fontenele.orcamentos.domain.Formatos

/**
 * Linha com a situação do servidor (banco unificado): conectado, sem conexão ou conectando.
 * [acao] é o botão da direita (ex.: "Alterar" no login, "Sincronizar" no início).
 * [logado] muda o texto de "precisa entrar": antes do login é o normal; depois, é um aviso.
 */
@Composable
fun StatusServidor(
    estado: EstadoSync,
    logado: Boolean,
    endereco: String,
    acao: String,
    aoClicarAcao: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val esquema = MaterialTheme.colorScheme
    val (icone, texto, cor) = when (estado) {
        is EstadoSync.Conectado -> Triple(Icons.Filled.CloudDone, "Servidor conectado · sincronizado às ${Formatos.hora(estado.quando)}", esquema.primary)
        is EstadoSync.SemConexao -> Triple(Icons.Filled.CloudOff, "Sem conexão com o servidor", esquema.error)
        EstadoSync.Nunca -> Triple(Icons.Filled.Cloud, "Conectando ao servidor...", esquema.onSurfaceVariant)
        // Antes do login isso é normal (o servidor respondeu); depois, o token venceu ou falta
        EstadoSync.PrecisaEntrar ->
            if (logado) Triple(Icons.Filled.CloudOff, "Entre de novo com sua senha para sincronizar", esquema.error)
            else Triple(Icons.Filled.CloudDone, "Servidor conectado", esquema.primary)
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icone, contentDescription = null, tint = cor, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(texto, style = MaterialTheme.typography.bodySmall, color = cor)
            Text(endereco, style = MaterialTheme.typography.bodySmall, color = esquema.onSurfaceVariant)
        }
        TextButton(onClick = aoClicarAcao) { Text(acao) }
    }
}
