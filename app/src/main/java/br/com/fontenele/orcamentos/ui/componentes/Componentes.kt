@file:OptIn(ExperimentalMaterial3Api::class)

package br.com.fontenele.orcamentos.ui.componentes

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.fontenele.orcamentos.R

// ------------------------------------------------------------------ Marca

/** Logo completo da Fontenele Publicações (símbolo + "FONTENELE PUBLICAÇÕES"). */
@Composable
fun LogoFontenele(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.logo_fontenele),
        contentDescription = "Fontenele Publicações",
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

/** Só o símbolo em losango (vetor). */
@Composable
fun SimboloFontenele(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_simbolo_fontenele),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

// ------------------------------------------------------------------ Barra superior

/** Barra branca com o título em negrito e uma linha fina embaixo. */
@Composable
fun BarraSuperior(
    titulo: String,
    aoVoltar: (() -> Unit)? = null,
    mostrarSimbolo: Boolean = false,
    acoes: @Composable () -> Unit = {},
) {
    Column {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (mostrarSimbolo) {
                        SimboloFontenele(Modifier.size(30.dp))
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        titulo.uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            navigationIcon = {
                if (aoVoltar != null) {
                    IconButton(onClick = aoVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            },
            actions = { acoes() },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.primary,
                navigationIconContentColor = MaterialTheme.colorScheme.primary,
                actionIconContentColor = MaterialTheme.colorScheme.primary,
            ),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

// ------------------------------------------------------------------ Botões (retos, como no site)

private val PaddingBotao = PaddingValues(horizontal = 18.dp, vertical = 12.dp)

/**
 * Botão do app: quadrado branco com borda e texto azul-marinho.
 * Todos os botões têm o mesmo visual, para não ficar um de cada cor lado a lado.
 */
@Composable
fun BotaoContorno(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 50.dp),
        enabled = enabled,
        shape = RectangleShape,
        border = BorderStroke(1.5.dp, if (enabled) cor else MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent, contentColor = cor),
        contentPadding = PaddingBotao,
        content = content,
    )
}

/** Ação principal da tela. Mesmo visual do [BotaoContorno] (branco com borda azul-marinho). */
@Composable
fun BotaoPrincipal(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable RowScope.() -> Unit,
) = BotaoContorno(onClick = onClick, modifier = modifier, enabled = enabled, cor = cor, content = content)

/** Texto de botão em maiúsculas, como no site. */
@Composable
fun TextoBotao(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        maxLines = 2,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

// ------------------------------------------------------------------ Campos

/** Campo de texto padrão; o asterisco indica campo obrigatório, como no sistema web. */
@Composable
fun CampoTexto(
    valor: String,
    aoMudar: (String) -> Unit,
    rotulo: String,
    modifier: Modifier = Modifier,
    obrigatorio: Boolean = false,
    teclado: KeyboardType = KeyboardType.Text,
    ajuda: String? = null,
    linhas: Int = 1,
    habilitado: Boolean = true,
    icone: ImageVector? = null,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = aoMudar,
        label = { Text(if (obrigatorio) "$rotulo*" else rotulo) },
        singleLine = linhas == 1,
        minLines = linhas,
        maxLines = if (linhas == 1) 1 else linhas + 3,
        enabled = habilitado,
        leadingIcon = if (icone != null) {
            { Icon(icone, contentDescription = null) }
        } else {
            null
        },
        supportingText = if (ajuda != null) {
            { Text(ajuda) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = teclado,
            imeAction = if (linhas == 1) ImeAction.Next else ImeAction.Default,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun CampoSenha(
    valor: String,
    aoMudar: (String) -> Unit,
    rotulo: String,
    modifier: Modifier = Modifier,
) {
    var visivel by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = valor,
        onValueChange = aoMudar,
        label = { Text(rotulo) },
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = { visivel = !visivel }) {
                Icon(
                    if (visivel) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visivel) "Esconder senha" else "Mostrar senha",
                )
            }
        },
        visualTransformation = if (visivel) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Lista suspensa (equivalente aos <select> do sistema web). */
@Composable
fun CampoSelecao(
    rotulo: String,
    opcoes: List<Pair<String, String>>,
    selecionado: String?,
    aoSelecionar: (String) -> Unit,
    modifier: Modifier = Modifier,
    obrigatorio: Boolean = false,
) {
    var aberto by remember { mutableStateOf(false) }
    val textoSelecionado = opcoes.firstOrNull { it.first == selecionado }?.second ?: "Selecione"
    ExposedDropdownMenuBox(
        expanded = aberto,
        onExpandedChange = { aberto = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = textoSelecionado,
            onValueChange = {},
            readOnly = true,
            label = { Text(if (obrigatorio) "$rotulo*" else rotulo) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = aberto) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            opcoes.forEach { (valor, texto) ->
                DropdownMenuItem(
                    text = { Text(texto) },
                    onClick = {
                        aoSelecionar(valor)
                        aberto = false
                    },
                )
            }
        }
    }
}

// ------------------------------------------------------------------ Textos e estados

/** Título de seção: texto curto em negrito, alinhado à esquerda. */
@Composable
fun TituloSecao(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto,
        style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

// ------------------------------------------------------------------ Listas em caixa

/**
 * Desenha a caixa de uma lista item por item (funciona dentro da LazyColumn):
 * as laterais, a linha de cima (que vira o divisor entre os itens) e, no último item, a linha de baixo.
 */
@Composable
fun Modifier.itemDeCaixa(ultimo: Boolean): Modifier {
    val cor = MaterialTheme.colorScheme.outlineVariant
    // desenha por cima do conteúdo, para as linhas não sumirem atrás de fundos (ex.: ListItem)
    return this.drawWithContent {
        drawContent()
        val espessura = 1.dp.toPx()
        val meio = espessura / 2
        drawLine(cor, Offset(0f, meio), Offset(size.width, meio), espessura)
        drawLine(cor, Offset(meio, 0f), Offset(meio, size.height), espessura)
        drawLine(cor, Offset(size.width - meio, 0f), Offset(size.width - meio, size.height), espessura)
        if (ultimo) drawLine(cor, Offset(0f, size.height - meio), Offset(size.width, size.height - meio), espessura)
    }
}

/** Caixa com borda para blocos curtos (ex.: valores calculados); use [DivisorCaixa] entre as linhas. */
@Composable
fun CaixaLista(modifier: Modifier = Modifier, conteudo: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = conteudo,
    )
}

@Composable
fun DivisorCaixa() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** Linha de uma lista em caixa: título, detalhe e valor à direita. [ultimo] fecha a caixa embaixo. */
@Composable
fun ItemLista(
    titulo: String,
    ultimo: Boolean,
    detalhe: String? = null,
    valor: String? = null,
    modifier: Modifier = Modifier,
    valorDetalhe: String? = null,
    aoClicar: (() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .itemDeCaixa(ultimo)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (aoClicar != null) Modifier.clickable(onClick = aoClicar) else Modifier)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    titulo,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (detalhe != null) {
                    Text(
                        detalhe,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (valor != null) {
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        valor,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (valorDetalhe != null) {
                        Text(
                            valorDetalhe,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Item de menu com seta, como a lista "Mapa" do rodapé do site. */
@Composable
fun ItemMenu(texto: String, aoClicar: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = aoClicar)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(texto, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun CartaoErro(mensagem: String?, modifier: Modifier = Modifier) {
    if (mensagem.isNullOrBlank()) return
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ErrorOutline, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(mensagem, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun Etiqueta(texto: String, destaque: Boolean, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (destaque) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (destaque) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    ) {
        Text(
            texto,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun MensagemVazia(icone: ImageVector, texto: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun TelaCarregando() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun AcessoNegado(aoVoltar: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Acesso restrito",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Esta tela é só para o Administrador.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        BotaoContorno(onClick = aoVoltar) { TextoBotao("Voltar") }
    }
}
