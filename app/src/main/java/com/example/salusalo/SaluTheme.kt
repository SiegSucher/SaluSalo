package com.example.salusalo

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HighlightOff
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.rounded.RiceBowl
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

/*
 * Salu-Salo's design tokens and shared components. Every screen takes its
 * colours, type, shapes and motion from here, so the app looks like one app.
 *
 * Brand colours: yellow #F8C163 (primary actions and selection), background
 * #FFF8ED, cream #F6EBD7 (fields, chips, skeletons), green #338C28 (free,
 * available, verified) and pink #D62F6B (unread, saved, urgent). Small green
 * and pink text uses the darker "ink" shades, which pass 4.5:1 on white and on
 * the soft tints; text on yellow is always Ink.
 */
internal object SaluColors {
    val Yellow = Color(0xFFF8C163)
    val YellowSoft = Color(0xFFFDEFD2)
    /** Text and icons on [YellowSoft] or white where yellow itself would be too light. */
    val AmberInk = Color(0xFF875A00)
    val Background = Color(0xFFFFF8ED)
    val Cream = Color(0xFFF6EBD7)
    val CreamDeep = Color(0xFFEADBC2)
    val Surface = Color.White
    /** Hairlines: card borders and dividers (decorative). */
    val Line = Color(0xFFEFE3D1)
    /** Borders that identify a control, such as an unfocused text field (3:1 on white). */
    val Outline = Color(0xFF9E8F7B)
    val Green = Color(0xFF338C28)
    val GreenInk = Color(0xFF256B1D)
    val GreenSoft = Color(0xFFE3F1DC)
    val Pink = Color(0xFFD62F6B)
    val PinkInk = Color(0xFFA8244F)
    val PinkSoft = Color(0xFFFBE3EB)
    val Ink = Color(0xFF2B2622)
    val InkMuted = Color(0xFF6B625A)
    /** Disabled text and decorative icons only; too light for information. */
    val InkFaint = Color(0xFFA0968C)
}

/** Motion: short and calm. Durations in milliseconds. */
internal object SaluMotion {
    const val SHORT = 150
    const val MEDIUM = 220
    const val LONG = 320
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

internal val SaluTypography = Typography(
    displaySmall = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp)
)

/** Named text roles, so screens say what a text is rather than how big it is. */
internal object SaluText {
    val ScreenTitle: TextStyle @Composable get() = MaterialTheme.typography.headlineSmall
    val SectionTitle: TextStyle @Composable get() = MaterialTheme.typography.titleMedium
    val FoodTitle: TextStyle @Composable get() = MaterialTheme.typography.titleSmall
    val Body: TextStyle @Composable get() = MaterialTheme.typography.bodyMedium
    val Supporting: TextStyle @Composable get() = MaterialTheme.typography.bodySmall
    val Meta: TextStyle @Composable get() = MaterialTheme.typography.labelMedium
    val Status: TextStyle @Composable get() = MaterialTheme.typography.labelSmall
    val Price: TextStyle @Composable get() = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
    val Overline: TextStyle @Composable get() = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, color = SaluColors.InkMuted)
}

internal val SaluShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

/** Corner radii: moderate, never a pill unless it is a status, filter or tag. */
internal object SaluRadius {
    val Card = RoundedCornerShape(18.dp)
    val Control = RoundedCornerShape(14.dp)
    val Photo = RoundedCornerShape(14.dp)
    val Small = RoundedCornerShape(10.dp)
    val Pill = RoundedCornerShape(50)
}

private val SaluColorScheme = lightColorScheme(
    // Interactive accents (text buttons, focus, switches, progress) use the
    // darker green so small text stays readable; filled primary buttons are
    // yellow through SaluPrimaryButton.
    primary = SaluColors.GreenInk,
    onPrimary = Color.White,
    primaryContainer = SaluColors.YellowSoft,
    onPrimaryContainer = SaluColors.Ink,
    inversePrimary = SaluColors.Yellow,
    secondary = SaluColors.Green,
    onSecondary = Color.White,
    secondaryContainer = SaluColors.YellowSoft,
    onSecondaryContainer = SaluColors.Ink,
    tertiary = SaluColors.Pink,
    onTertiary = Color.White,
    tertiaryContainer = SaluColors.PinkSoft,
    onTertiaryContainer = SaluColors.PinkInk,
    background = SaluColors.Background,
    onBackground = SaluColors.Ink,
    surface = SaluColors.Surface,
    onSurface = SaluColors.Ink,
    surfaceVariant = SaluColors.Cream,
    onSurfaceVariant = SaluColors.InkMuted,
    surfaceTint = Color.Transparent,
    inverseSurface = SaluColors.Ink,
    inverseOnSurface = SaluColors.Background,
    error = SaluColors.PinkInk,
    onError = Color.White,
    errorContainer = SaluColors.PinkSoft,
    onErrorContainer = SaluColors.PinkInk,
    outline = SaluColors.Outline,
    outlineVariant = SaluColors.Line,
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFCF7),
    surfaceContainer = Color(0xFFFFFAF2),
    surfaceContainerHigh = Color(0xFFFFFCF7),
    surfaceContainerHighest = SaluColors.Cream,
    surfaceDim = SaluColors.Cream
)

@Composable
internal fun SaluTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SaluColorScheme,
        typography = SaluTypography,
        shapes = SaluShapes,
        content = content
    )
}

/* ---------- Shared pieces ---------- */

/** A slight shrink while pressed: quick feedback without bouncing. */
@Composable
internal fun Modifier.pressScale(interactionSource: MutableInteractionSource, pressedScale: Float = 0.97f): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(SaluMotion.SHORT),
        label = "pressScale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** The main action on a screen: yellow, Ink text, 52 dp tall. */
@Composable
internal fun SaluPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null
) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .heightIn(min = 52.dp)
            .pressScale(interaction),
        shape = SaluRadius.Control,
        colors = ButtonDefaults.buttonColors(
            containerColor = SaluColors.Yellow,
            contentColor = SaluColors.Ink,
            disabledContainerColor = if (loading) SaluColors.Yellow.copy(alpha = 0.7f) else SaluColors.Cream,
            disabledContentColor = if (loading) SaluColors.Ink else SaluColors.InkMuted
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        interactionSource = interaction
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = SaluColors.Ink,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(10.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

/** A secondary action: white with an outline, Ink text. */
@Composable
internal fun SaluSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    icon: ImageVector? = null,
    contentColor: Color = SaluColors.Ink
) {
    val interaction = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = 48.dp)
            .pressScale(interaction),
        shape = SaluRadius.Control,
        border = BorderStroke(1.dp, if (enabled) SaluColors.Outline.copy(alpha = 0.55f) else SaluColors.Line),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor = contentColor,
            disabledContainerColor = Color.White,
            disabledContentColor = SaluColors.InkFaint
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        interactionSource = interaction
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

/** A white card with a hairline border; clickable when [onClick] is set. */
@Composable
internal fun SaluCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = SaluColors.Surface,
    shape: Shape = SaluRadius.Card,
    border: Boolean = true,
    content: @Composable () -> Unit
) {
    val stroke = if (border) BorderStroke(1.dp, SaluColors.Line) else null
    if (onClick != null) {
        val interaction = remember { MutableInteractionSource() }
        Surface(
            onClick = onClick,
            modifier = modifier.pressScale(interaction, pressedScale = 0.985f),
            shape = shape,
            color = color,
            border = stroke,
            interactionSource = interaction,
            content = content
        )
    } else {
        Surface(modifier = modifier, shape = shape, color = color, border = stroke, content = content)
    }
}

/** A section heading with an optional line under it and an optional action on the right. */
@Composable
internal fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = SaluColors.AmberInk,
    action: (@Composable () -> Unit)? = null
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = SaluText.SectionTitle,
                color = SaluColors.Ink,
                modifier = Modifier.semantics { heading() }
            )
            if (subtitle != null) {
                Text(subtitle, style = SaluText.Supporting, color = SaluColors.InkMuted)
            }
        }
        action?.invoke()
    }
}

/** A small uppercase label above a group of rows. */
@Composable
internal fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = SaluText.Overline,
        modifier = modifier
            .padding(start = 4.dp, top = 4.dp)
            .semantics { heading() }
    )
}

/** Statuses shown as a pill with an icon and a word, so colour is never the only signal. */
internal enum class SaluStatus(
    val label: String,
    val icon: ImageVector,
    val container: Color,
    val content: Color
) {
    Available("Available", Icons.Outlined.CheckCircle, SaluColors.GreenSoft, SaluColors.GreenInk),
    Reserved("Reserved", Icons.Outlined.Bookmark, SaluColors.YellowSoft, SaluColors.AmberInk),
    Claimed("Claimed", Icons.Outlined.TaskAlt, SaluColors.Ink, Color.White),
    Cancelled("Cancelled", Icons.Outlined.HighlightOff, SaluColors.PinkSoft, SaluColors.PinkInk),
    Expired("Expired", Icons.Outlined.Schedule, SaluColors.Cream, SaluColors.InkMuted),
    Removed("Removed", Icons.Outlined.Block, SaluColors.Cream, SaluColors.InkMuted),
    ExpiresSoon("Expires soon", Icons.Outlined.HourglassBottom, SaluColors.PinkSoft, SaluColors.PinkInk);

    companion object {
        /** The status words the Activity records use ("Available", "Reserved", …); null for anything else. */
        fun fromLabel(label: String): SaluStatus? = entries.firstOrNull { it.label.equals(label, ignoreCase = true) }
    }
}

@Composable
internal fun StatusBadge(status: SaluStatus, modifier: Modifier = Modifier, label: String = status.label) {
    Row(
        modifier = modifier
            .clip(SaluRadius.Pill)
            .background(status.container)
            .padding(start = 7.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(status.icon, contentDescription = null, tint = status.content, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = SaluText.Status, color = status.content, maxLines = 1)
    }
}

/** "Free" as a green tag; a price as bold Ink text with an optional unit ("per pack"). */
@Composable
internal fun PriceTag(price: String, free: Boolean, modifier: Modifier = Modifier, unit: String? = null) {
    if (free) {
        Text(
            "Free",
            style = SaluText.Meta.copy(fontWeight = FontWeight.Bold),
            color = SaluColors.GreenInk,
            modifier = modifier
                .clip(SaluRadius.Small)
                .background(SaluColors.GreenSoft)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        )
    } else {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = SaluColors.Ink)) { append(price) }
                if (unit != null) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = SaluColors.InkMuted, fontSize = 12.sp)) {
                        append(" / $unit")
                    }
                }
            },
            style = SaluText.Body,
            modifier = modifier
        )
    }
}

/** An icon on a soft circle, for list rows, empty states and alerts. */
@Composable
internal fun IconBubble(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = SaluColors.AmberInk,
    background: Color = SaluColors.YellowSoft,
    size: Dp = 40.dp,
    iconSize: Dp = size * 0.5f
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/**
 * A helpful empty state: what is missing, why, and what to do next. [actionLabel]
 * shows a button when [onAction] is set.
 */
@Composable
internal fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    tint: Color = SaluColors.AmberInk,
    iconBackground: Color = SaluColors.YellowSoft
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconBubble(icon, tint = tint, background = iconBackground, size = 56.dp, iconSize = 28.dp)
        Spacer(Modifier.height(14.dp))
        Text(title, style = SaluText.FoodTitle, color = SaluColors.Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = SaluText.Supporting, color = SaluColors.InkMuted, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            SaluSecondaryButton(text = actionLabel, onClick = onAction, modifier = Modifier)
        }
    }
}

/** A one-line note with an icon, for hints, warnings and errors under a control. */
internal enum class NoticeTone(val container: Color, val content: Color) {
    Info(SaluColors.Cream, SaluColors.Ink),
    Warning(SaluColors.YellowSoft, SaluColors.AmberInk),
    Error(SaluColors.PinkSoft, SaluColors.PinkInk),
    Success(SaluColors.GreenSoft, SaluColors.GreenInk)
}

@Composable
internal fun InlineNotice(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: NoticeTone = NoticeTone.Info
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(SaluRadius.Small)
            .background(tone.container)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, contentDescription = null, tint = tone.content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = SaluText.Supporting, color = tone.content, modifier = Modifier.weight(1f))
    }
}

/** A pink count for unread messages and alerts ("99+" above 99). */
@Composable
internal fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    // The count is spoken by the control it belongs to ("Alerts, 3 unread").
    Box(
        modifier = modifier
            .clearAndSetSemantics { }
            .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
            .clip(SaluRadius.Pill)
            .background(SaluColors.Pink)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold),
            maxLines = 1
        )
    }
}

/** A round profile photo, else the name's initial on cream. The photo URL is a signed URL. */
@Composable
internal fun Avatar(
    photoUrl: String?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    var failed by remember(photoUrl) { mutableStateOf(false) }
    val initial = name.trim().take(1).uppercase()
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(SaluColors.YellowSoft),
        contentAlignment = Alignment.Center
    ) {
        if (photoUrl != null && !failed) {
            AsyncImage(
                model = photoUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { failed = true }
            )
        } else if (initial.isNotEmpty()) {
            Text(
                initial,
                modifier = Modifier.clearAndSetSemantics {
                    if (contentDescription != null) this.contentDescription = contentDescription
                },
                color = SaluColors.AmberInk,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Bold
            )
        } else {
            Icon(
                Icons.Outlined.Person,
                contentDescription = contentDescription,
                tint = SaluColors.AmberInk,
                modifier = Modifier.size(size * 0.55f)
            )
        }
    }
}

/** The app's mark: a rice bowl on a yellow tile. */
@Composable
internal fun BrandMark(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(SaluColors.Yellow),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.RiceBowl,
            contentDescription = null,
            tint = SaluColors.Ink,
            modifier = Modifier.size(size * 0.56f)
        )
    }
}

/** "Salu-Salo" with the hyphen in brand green. */
@Composable
internal fun BrandWordmark(modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.displaySmall) {
    Text(
        buildAnnotatedString {
            append("Salu")
            withStyle(SpanStyle(color = SaluColors.Green)) { append("-") }
            append("Salo")
        },
        style = style,
        color = SaluColors.Ink,
        modifier = modifier.semantics { contentDescription = "Salu-Salo" }
    )
}

/**
 * Top bar for full-screen pages: a back arrow, the title and optional actions.
 * The caller pads for the status bar.
 */
@Composable
internal fun SaluTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    backDescription: String = "Back",
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(color = SaluColors.Background, modifier = modifier) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = backDescription, tint = SaluColors.Ink)
                    }
                } else {
                    Spacer(Modifier.width(12.dp))
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 4.dp, end = 8.dp)
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = SaluColors.Ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() }
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = SaluText.Supporting,
                            color = SaluColors.InkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                actions()
            }
            HorizontalDivider(color = SaluColors.Line)
        }
    }
}

/** Page dots for pagers: the current page is a longer yellow bar. */
@Composable
internal fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier, onDark: Boolean = false) {
    Row(
        modifier = modifier.semantics { contentDescription = "Page ${current + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            val selected = index == current
            val width by animateDpAsState(if (selected) 20.dp else 7.dp, tween(SaluMotion.MEDIUM), label = "dotWidth")
            val color by animateColorAsState(
                when {
                    selected -> SaluColors.Yellow
                    onDark -> Color.White.copy(alpha = 0.6f)
                    else -> SaluColors.CreamDeep
                },
                tween(SaluMotion.MEDIUM),
                label = "dotColor"
            )
            Box(
                Modifier
                    .height(7.dp)
                    .width(width)
                    .clip(SaluRadius.Pill)
                    .background(color)
            )
        }
    }
}

/** Text-field colours shared by every form: white fill, warm outline, green focus. */
@Composable
internal fun saluFieldColors(container: Color = Color.White) = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = container,
    unfocusedContainerColor = container,
    disabledContainerColor = SaluColors.Cream.copy(alpha = 0.6f),
    focusedBorderColor = SaluColors.GreenInk,
    unfocusedBorderColor = SaluColors.Outline.copy(alpha = 0.6f),
    disabledBorderColor = SaluColors.Line,
    focusedLabelColor = SaluColors.GreenInk,
    unfocusedLabelColor = SaluColors.InkMuted,
    cursorColor = SaluColors.GreenInk,
    focusedTextColor = SaluColors.Ink,
    unfocusedTextColor = SaluColors.Ink,
    focusedPlaceholderColor = SaluColors.InkMuted,
    unfocusedPlaceholderColor = SaluColors.InkMuted,
    focusedLeadingIconColor = SaluColors.InkMuted,
    unfocusedLeadingIconColor = SaluColors.InkMuted,
    focusedSupportingTextColor = SaluColors.InkMuted,
    unfocusedSupportingTextColor = SaluColors.InkMuted
)

/** Filter chips: pills (they are filters), Ink when selected, with an optional icon. */
@Composable
internal fun SaluFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        label = { Text(label, style = SaluText.Meta.copy(fontSize = 13.sp), maxLines = 1) },
        leadingIcon = icon?.let {
            { Icon(it, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        },
        shape = SaluRadius.Pill,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.White,
            labelColor = SaluColors.Ink,
            iconColor = SaluColors.InkMuted,
            selectedContainerColor = SaluColors.Ink,
            selectedLabelColor = Color.White,
            selectedLeadingIconColor = Color.White
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = SaluColors.Line,
            selectedBorderColor = Color.Transparent
        )
    )
}

/* ---------- Skeleton loading ---------- */

private val LocalShimmer = staticCompositionLocalOf<State<Float>?> { null }

/**
 * Hosts skeleton blocks and moves one shimmer across them. [description] is
 * read by screen readers instead of the blocks.
 */
@Composable
internal fun SkeletonGroup(
    description: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val progress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "skeletonProgress"
    )
    CompositionLocalProvider(LocalShimmer provides progress) {
        Column(
            modifier = modifier.clearAndSetSemantics { contentDescription = description },
            content = content
        )
    }
}

/** One grey block of a skeleton, in the shape of the content it stands for. */
@Composable
internal fun SkeletonBlock(modifier: Modifier, shape: Shape = RoundedCornerShape(8.dp)) {
    val progress = LocalShimmer.current
    Box(
        modifier
            .clip(shape)
            .background(SaluColors.Cream)
            .drawBehind {
                val p = progress?.value ?: return@drawBehind
                val width = size.width.coerceAtLeast(1f)
                val x = -width + p * 2f * width
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                        start = Offset(x, 0f),
                        end = Offset(x + width, 0f)
                    )
                )
            }
    )
}

/** Skeleton of a Home food card: photo on the left, three lines on the right. */
@Composable
internal fun FoodCardSkeleton(modifier: Modifier = Modifier) {
    SaluCard(modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp)) {
            SkeletonBlock(Modifier.size(104.dp), SaluRadius.Photo)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SkeletonBlock(Modifier.fillMaxWidth(0.8f).height(16.dp))
                SkeletonBlock(Modifier.fillMaxWidth(0.5f).height(12.dp))
                SkeletonBlock(Modifier.width(56.dp).height(20.dp), SaluRadius.Small)
                SkeletonBlock(Modifier.fillMaxWidth(0.65f).height(12.dp))
            }
        }
    }
}

/** Skeleton of a list row: a square thumbnail and two lines. */
@Composable
internal fun ListRowSkeleton(modifier: Modifier = Modifier, thumbnail: Dp = 56.dp, round: Boolean = false) {
    Row(modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        SkeletonBlock(Modifier.size(thumbnail), if (round) CircleShape else SaluRadius.Small)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(14.dp))
            SkeletonBlock(Modifier.fillMaxWidth(0.85f).height(12.dp))
        }
    }
}

/** Grows and shrinks smoothly when its content changes size (expand/collapse). */
internal fun Modifier.smoothSize(): Modifier = this.animateContentSize(tween(SaluMotion.MEDIUM, easing = SaluMotion.Emphasized))
