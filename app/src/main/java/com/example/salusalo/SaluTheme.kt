package com.example.salusalo

import android.graphics.drawable.BitmapDrawable
import android.util.DisplayMetrics
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
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import coil3.compose.LocalPlatformContext

/*
 * Salu-Salo's design tokens and shared components. Every screen takes its
 * colours, type, shapes and motion from here, so the app looks like one app.
 *
 * The colours are Salu-Salo's original palette, exactly as the app used it
 * before the UI refresh: brand green #47782E, dark green #263C29, cream
 * #FCFBF6, pale green #F3F6E8, icon green #E4EDC9, grey #666873 and line
 * #E3E4E7, with the original orange, gold and red accents. They are fixed
 * brand colours; the refresh changes layout, type and components, not these.
 * The only additions are see-through tints of them (the ...Soft values).
 */
internal object SaluColors {
    /** Main actions, selection, links and icons; white text on it. */
    val Green = Color(0xFF47782E)
    /** Titles and body text. */
    val DarkGreen = Color(0xFF263C29)
    /** The app background. */
    val Cream = Color(0xFFFCFBF6)
    /** Soft surfaces: panels, tags, highlighted rows. */
    val PaleGreen = Color(0xFFF3F6E8)
    /** Icon circles, avatars and the selected tab. */
    val IconGreen = Color(0xFFE4EDC9)
    /** Supporting text and icons. */
    val Gray = Color(0xFF666873)
    /** Hairlines: card borders and dividers. */
    val Line = Color(0xFFE3E4E7)
    /** Inactive page dots. */
    val DotGray = Color(0xFFDDDEDA)
    /** Neutral chips and tiles (the original "not verified" grey). */
    val Neutral = Color(0xFFEDEEF0)
    /** Expiring soon. Too light for small text, so it marks icons and tints. */
    val Urgent = Color(0xFFE85B13)
    /** Unread dots and reservations being held. */
    val Orange = Color(0xFFEF7900)
    /** The spark before the recommendations. */
    val Sparkle = Color(0xFFFFA125)
    /** Rating stars and rating text. */
    val StarGold = Color(0xFFFFAD08)
    val RatingAmber = Color(0xFFB26A00)
    /** Errors, destructive actions and unread counts: the original theme's error red. */
    val Error = Color(0xFFB3261E)
    val Surface = Color.White

    val UrgentSoft = Urgent.copy(alpha = 0.10f)
    val OrangeSoft = Orange.copy(alpha = 0.12f)
    val ErrorSoft = Error.copy(alpha = 0.08f)
    /** Disabled text and decorative icons only; too light for information. */
    val Faint = Gray.copy(alpha = 0.6f)
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
    val Overline: TextStyle @Composable get() = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, color = SaluColors.Gray)
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
    // The original theme set primary, background and surface to these same
    // colours; the other roles follow the palette instead of Material's defaults.
    primary = SaluColors.Green,
    onPrimary = Color.White,
    primaryContainer = SaluColors.IconGreen,
    onPrimaryContainer = SaluColors.DarkGreen,
    inversePrimary = SaluColors.IconGreen,
    secondary = SaluColors.Green,
    onSecondary = Color.White,
    secondaryContainer = SaluColors.PaleGreen,
    onSecondaryContainer = SaluColors.DarkGreen,
    tertiary = SaluColors.Orange,
    onTertiary = Color.White,
    tertiaryContainer = SaluColors.PaleGreen,
    onTertiaryContainer = SaluColors.DarkGreen,
    background = SaluColors.Cream,
    onBackground = SaluColors.DarkGreen,
    surface = SaluColors.Surface,
    onSurface = SaluColors.DarkGreen,
    surfaceVariant = SaluColors.PaleGreen,
    onSurfaceVariant = SaluColors.Gray,
    surfaceTint = Color.Transparent,
    inverseSurface = SaluColors.DarkGreen,
    inverseOnSurface = SaluColors.Cream,
    // Material's error colours, which the original theme used unchanged.
    error = SaluColors.Error,
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = SaluColors.Gray,
    outlineVariant = SaluColors.Line,
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = SaluColors.Cream,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = SaluColors.PaleGreen,
    surfaceDim = SaluColors.PaleGreen
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

/** The main action on a screen: brand green with white text, 52 dp tall. */
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
            containerColor = SaluColors.Green,
            contentColor = Color.White,
            disabledContainerColor = if (loading) SaluColors.Green.copy(alpha = 0.75f) else SaluColors.PaleGreen,
            disabledContentColor = if (loading) Color.White else SaluColors.Gray
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        interactionSource = interaction
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = Color.White,
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

/** A secondary action: white with a soft green outline and green text. */
@Composable
internal fun SaluSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    icon: ImageVector? = null,
    contentColor: Color = SaluColors.Green
) {
    val interaction = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = 48.dp)
            .pressScale(interaction),
        shape = SaluRadius.Control,
        border = BorderStroke(1.dp, if (enabled) contentColor.copy(alpha = 0.35f) else SaluColors.Line),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor = contentColor,
            disabledContainerColor = Color.White,
            disabledContentColor = SaluColors.Faint
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
    iconTint: Color = SaluColors.Green,
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
                color = SaluColors.DarkGreen,
                modifier = Modifier.semantics { heading() }
            )
            if (subtitle != null) {
                Text(subtitle, style = SaluText.Supporting, color = SaluColors.Gray)
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

/**
 * Statuses shown as a pill with an icon and a word, so colour is never the only
 * signal. [icon] may be brighter than [content]: an accent too light for small
 * text (orange) marks the icon and the tint, and the word stays dark.
 */
internal enum class SaluStatus(
    val label: String,
    val icon: ImageVector,
    val container: Color,
    val content: Color,
    val iconTint: Color = content
) {
    Available("Available", Icons.Outlined.CheckCircle, SaluColors.PaleGreen, SaluColors.Green),
    Reserved("Reserved", Icons.Outlined.Bookmark, SaluColors.OrangeSoft, SaluColors.DarkGreen, SaluColors.Orange),
    Claimed("Claimed", Icons.Outlined.TaskAlt, SaluColors.Green, Color.White),
    Cancelled("Cancelled", Icons.Outlined.HighlightOff, SaluColors.ErrorSoft, SaluColors.Error),
    Expired("Expired", Icons.Outlined.Schedule, SaluColors.Neutral, SaluColors.Gray),
    Removed("Removed", Icons.Outlined.Block, SaluColors.Neutral, SaluColors.Gray),
    ExpiresSoon("Expires soon", Icons.Outlined.HourglassBottom, SaluColors.UrgentSoft, SaluColors.DarkGreen, SaluColors.Urgent);

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
        Icon(status.icon, contentDescription = null, tint = status.iconTint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = SaluText.Status, color = status.content, maxLines = 1)
    }
}

/** "Free" as a green tag on pale green (the original price tag); a price as bold text with an optional unit. */
@Composable
internal fun PriceTag(price: String, free: Boolean, modifier: Modifier = Modifier, unit: String? = null) {
    if (free) {
        Text(
            "Free",
            style = SaluText.Meta.copy(fontWeight = FontWeight.Bold),
            color = SaluColors.Green,
            modifier = modifier
                .clip(SaluRadius.Small)
                .background(SaluColors.PaleGreen)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        )
    } else {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = SaluColors.DarkGreen)) { append(price) }
                if (unit != null) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = SaluColors.Gray, fontSize = 12.sp)) {
                        append(" / $unit")
                    }
                }
            },
            style = SaluText.Body,
            modifier = modifier
        )
    }
}

/** An icon on a soft circle (green on icon green, as the original), for list rows, empty states and alerts. */
@Composable
internal fun IconBubble(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = SaluColors.Green,
    background: Color = SaluColors.IconGreen,
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
    tint: Color = SaluColors.Green,
    iconBackground: Color = SaluColors.IconGreen
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconBubble(icon, tint = tint, background = iconBackground, size = 56.dp, iconSize = 28.dp)
        Spacer(Modifier.height(14.dp))
        Text(title, style = SaluText.FoodTitle, color = SaluColors.DarkGreen, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = SaluText.Supporting, color = SaluColors.Gray, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            SaluSecondaryButton(text = actionLabel, onClick = onAction, modifier = Modifier)
        }
    }
}

/** A one-line note with an icon, for hints, warnings and errors under a control. */
internal enum class NoticeTone(val container: Color, val content: Color, val iconTint: Color = content) {
    Info(SaluColors.PaleGreen, SaluColors.DarkGreen, SaluColors.Green),
    Warning(SaluColors.UrgentSoft, SaluColors.DarkGreen, SaluColors.Urgent),
    Error(SaluColors.ErrorSoft, SaluColors.Error),
    Success(SaluColors.PaleGreen, SaluColors.Green)
}

@Composable
internal fun InlineNotice(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: NoticeTone = NoticeTone.Info,
    // An optional small button at the end, for example "Try again".
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(SaluRadius.Small)
            .background(tone.container)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, contentDescription = null, tint = tone.iconTint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = SaluText.Supporting, color = tone.content, modifier = Modifier.weight(1f))
        if (actionLabel != null) {
            TextButton(
                onClick = onAction,
                modifier = Modifier.align(Alignment.CenterVertically).heightIn(min = 48.dp)
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge, color = tone.content)
            }
        }
    }
}

/** A red count for unread messages and alerts ("99+" above 99), like the original Home badges. */
@Composable
internal fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    // The count is spoken by the control it belongs to ("Alerts, 3 unread").
    Box(
        modifier = modifier
            .clearAndSetSemantics { }
            .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
            .clip(SaluRadius.Pill)
            .background(SaluColors.Error)
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

/** A round profile photo, else the name's initial in green on icon green. The photo URL is a signed URL. */
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
            .background(SaluColors.IconGreen),
        contentAlignment = Alignment.Center
    ) {
        if (photoUrl != null && !failed) {
            val context = LocalPlatformContext.current
            AsyncImage(
                model = remember(photoUrl, context) { signedImageRequest(context, photoUrl) },
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
                color = SaluColors.Green,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Bold
            )
        } else {
            Icon(
                Icons.Outlined.Person,
                contentDescription = contentDescription,
                tint = SaluColors.Green,
                modifier = Modifier.size(size * 0.55f)
            )
        }
    }
}

/**
 * Salu-Salo's logo: the original emblem of the app's launcher icon, shown round
 * like the round launcher icon. It is drawn from the launcher artwork itself
 * (R.mipmap.ic_launcher_foreground), unchanged: the emblem's green square fills
 * the middle 268 of the artwork's 432 px, so the artwork is enlarged until that
 * square fills the circle, which also leaves out its transparent margin.
 */
@Composable
internal fun SaluLogo(size: Dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Image(
        painter = rememberLogoPainter(),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .graphicsLayer {
                scaleX = LOGO_ZOOM
                scaleY = LOGO_ZOOM
            }
    )
}

/** A little more than 432 / 268, so the square's soft edge stays outside the circle. */
private const val LOGO_ZOOM = 432f / 262f

/**
 * The launcher artwork at its sharpest (the xxxhdpi file): the logo is shown
 * larger than a launcher icon, and the phone's own density would blur it.
 */
@Composable
private fun rememberLogoPainter(): Painter {
    val resources = LocalContext.current.resources
    val sharp = remember(resources) {
        val drawable = runCatching {
            resources.getDrawableForDensity(R.mipmap.ic_launcher_foreground, DisplayMetrics.DENSITY_XXXHIGH, null)
        }.getOrNull()
        (drawable as? BitmapDrawable)?.bitmap?.let { BitmapPainter(it.asImageBitmap()) }
    }
    return sharp ?: painterResource(R.mipmap.ic_launcher_foreground)
}

/** The app's name as text, in the original dark green. */
@Composable
internal fun BrandWordmark(modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.displaySmall) {
    Text("Salu-Salo", style = style, color = SaluColors.DarkGreen, modifier = modifier)
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
    Surface(color = SaluColors.Cream, modifier = modifier) {
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
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = backDescription, tint = SaluColors.DarkGreen)
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
                        color = SaluColors.DarkGreen,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() }
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = SaluText.Supporting,
                            color = SaluColors.Gray,
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

/** Page dots for pagers: the current page is a longer green bar, the others the original grey dots. */
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
                    selected -> if (onDark) Color.White else SaluColors.Green
                    onDark -> Color.White.copy(alpha = 0.6f)
                    else -> SaluColors.DotGray
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

/** Text-field colours shared by every form: white fill, grey outline, green focus (as the original fields). */
@Composable
internal fun saluFieldColors(container: Color = Color.White) = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = container,
    unfocusedContainerColor = container,
    disabledContainerColor = SaluColors.PaleGreen,
    focusedBorderColor = SaluColors.Green,
    unfocusedBorderColor = SaluColors.Gray.copy(alpha = 0.45f),
    disabledBorderColor = SaluColors.Line,
    focusedLabelColor = SaluColors.Green,
    unfocusedLabelColor = SaluColors.Gray,
    cursorColor = SaluColors.Green,
    focusedTextColor = SaluColors.DarkGreen,
    unfocusedTextColor = SaluColors.DarkGreen,
    focusedPlaceholderColor = SaluColors.Gray,
    unfocusedPlaceholderColor = SaluColors.Gray,
    focusedLeadingIconColor = SaluColors.Gray,
    unfocusedLeadingIconColor = SaluColors.Gray,
    focusedSupportingTextColor = SaluColors.Gray,
    unfocusedSupportingTextColor = SaluColors.Gray
)

/** Filter chips as the original: cream with a green label and soft green border, green when selected. */
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
            containerColor = SaluColors.Cream,
            labelColor = SaluColors.Green,
            iconColor = SaluColors.Green,
            selectedContainerColor = SaluColors.Green,
            selectedLabelColor = Color.White,
            selectedLeadingIconColor = Color.White
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = SaluColors.Green.copy(alpha = 0.3f),
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
            .background(SaluColors.IconGreen.copy(alpha = 0.55f))
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
