package com.vladdev.freedomchat.ui.chats
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vladdev.freedomchat.R

@Composable
fun UserAvatar(name: String, size: Dp) {
    val initials = name.take(2).uppercase()
    val avatarColor = MaterialTheme.colorScheme.primaryContainer

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(avatarColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = (size.value * 0.33f).sp
            )
        )
    }
}


@Composable
fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_error),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
fun StatusIcon(status: String, size: Dp = 16.dp) {
    when (status) {
        "verified" -> Icon(
            painterResource(R.drawable.verified), null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(size)
        )
        "service" -> Icon(
            painterResource(R.drawable.service), null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(size)
        )
        "admin" -> Icon(
            painterResource(R.drawable.admin), null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(size)
        )
        // "standard" — ничего не показываем
    }
}

fun stripMarkdown(text: String): String =
    text
        .replace(Regex("""\*\*(.+?)\*\*"""), "$1")
        .replace(Regex("""_(.+?)_"""),        "$1")
        .replace(Regex("""~~(.+?)~~"""),       "$1")
        .replace(Regex("""\|\|(.+?)\|\|"""),  "●●●")  // спойлер — заменяем точками

@Composable
fun FormattedPreviewText(
    text: String,
    style: TextStyle,
    color: Color,
    maxLines: Int = 1,
    modifier: Modifier = Modifier
) {
    val annotated = remember(text, color) {
        buildAnnotatedString {
            val spoilerColor = color.copy(alpha = 0f)
            val spoilerBg    = color.copy(alpha = 0.75f)

            val patterns = listOf(
                "BOLD"    to Regex("""\*\*(.+?)\*\*"""),
                "ITALIC"  to Regex("""_(.+?)_"""),
                "STRIKE"  to Regex("""~~(.+?)~~"""),
                "SPOILER" to Regex("""\|\|(.+?)\|\|""")
            )

            data class Token(val range: IntRange, val type: String, val inner: String)

            val tokens = patterns
                .flatMap { (type, rx) -> rx.findAll(text).map { Token(it.range, type, it.groupValues[1]) } }
                .sortedBy { it.range.first }
                .fold(emptyList<Token>()) { acc, t ->
                    if (acc.isNotEmpty() && t.range.first <= acc.last().range.last) acc else acc + t
                }

            var cursor = 0
            tokens.forEach { token ->
                if (token.range.first > cursor) append(text.substring(cursor, token.range.first))
                when (token.type) {
                    "BOLD"    -> withStyle(SpanStyle(fontWeight = FontWeight.Bold))              { append(token.inner) }
                    "ITALIC"  -> withStyle(SpanStyle(fontStyle = FontStyle.Italic))              { append(token.inner) }
                    "STRIKE"  -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(token.inner) }
                    "SPOILER" -> withStyle(SpanStyle(color = spoilerColor, background = spoilerBg)) { append(token.inner) }
                }
                cursor = token.range.last + 1
            }
            if (cursor < text.length) append(text.substring(cursor))
        }
    }

    Text(
        text     = annotated,
        style    = style,
        color    = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}