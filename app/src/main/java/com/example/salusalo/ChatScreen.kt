package com.example.salusalo

import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.salusalo.model.ChatMessage
import com.example.salusalo.model.ConversationSummary
import com.example.salusalo.model.ReportTarget
import com.example.salusalo.model.parseTimestamp
import com.example.salusalo.viewmodel.MessageViewModel
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A line in the open chat: a day heading, or a message. */
private sealed interface ChatEntry {
    val key: String

    class Day(val label: String) : ChatEntry {
        override val key = "day-$label"
    }

    class Message(val message: ChatMessage) : ChatEntry {
        override val key = message.id
    }
}

/** Messages with a "Today" / "Yesterday" / date heading before each new day. */
private fun chatEntries(messages: List<ChatMessage>): List<ChatEntry> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    var lastDay: LocalDate? = null
    return buildList {
        messages.forEach { message ->
            val day = parseTimestamp(message.createdAt)?.atZone(zone)?.toLocalDate()
            if (day != null && day != lastDay) {
                lastDay = day
                add(
                    ChatEntry.Day(
                        when (day) {
                            today -> "Today"
                            today.minusDays(1) -> "Yesterday"
                            else -> DateTimeFormatter.ofPattern(
                                if (day.year == today.year) "EEE, MMM d" else "MMM d, yyyy",
                                Locale.ENGLISH
                            ).format(day)
                        }
                    )
                )
            }
            add(ChatEntry.Message(message))
        }
    }
}

/**
 * The open conversation, shown full screen over whatever opened it. New
 * messages arrive live through MessageViewModel's Realtime subscription.
 */
@Composable
fun ChatScreen(
    messageViewModel: MessageViewModel,
    myProfileId: String?,
    onClose: () -> Unit
) {
    val conversation by messageViewModel.active.collectAsStateWithLifecycle()
    val messages by messageViewModel.messages.collectAsStateWithLifecycle()
    val opening by messageViewModel.opening.collectAsStateWithLifecycle()
    val sending by messageViewModel.sending.collectAsStateWithLifecycle()
    val draft = messageViewModel.draft
    // Reporting the other person in this chat (for example a recipient who didn't show up).
    var reporting by rememberSaveable(conversation?.id) { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val entries = remember(messages) { chatEntries(messages) }
    LaunchedEffect(messages.size) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.lastIndex)
    }

    // Edge to edge, so the message box stays above the keyboard (imePadding).
    Dialog(
        onDismissRequest = onClose,
        properties = EdgeToEdgeDialog
    ) {
        DarkSystemBarIcons()
        // Keep the newest message in view while the keyboard opens.
        val keyboardHeight = WindowInsets.ime.getBottom(LocalDensity.current)
        LaunchedEffect(keyboardHeight) {
            if (keyboardHeight > 0 && entries.isNotEmpty()) listState.scrollToItem(entries.lastIndex)
        }
        Surface(modifier = Modifier.fillMaxSize(), color = SaluColors.Cream) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Surface(color = SaluColors.Cream) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onClose) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = "Back",
                                    tint = SaluColors.DarkGreen
                                )
                            }
                            conversation?.let {
                                ListingThumbnail(it, Modifier.size(44.dp))
                                Spacer(Modifier.width(12.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    conversation?.otherName ?: "Messages",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = SaluColors.DarkGreen,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.semantics { heading() }
                                )
                                conversation?.let {
                                    Text(
                                        (if (it.isDonor) "Asking about your listing: " else "About: ") + it.foodName,
                                        style = SaluText.Supporting,
                                        color = SaluColors.Gray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            conversation?.let {
                                Box {
                                    IconButton(onClick = { menuOpen = true }) {
                                        Icon(
                                            Icons.Outlined.MoreVert,
                                            contentDescription = "More options",
                                            tint = SaluColors.DarkGreen
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = menuOpen,
                                        onDismissRequest = { menuOpen = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Report ${it.otherName}", color = SaluColors.Error) },
                                            leadingIcon = {
                                                Icon(Icons.Outlined.Flag, contentDescription = null, tint = SaluColors.Error)
                                            },
                                            onClick = {
                                                menuOpen = false
                                                reporting = true
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = SaluColors.Line)
                    }
                }

                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .foodDoodleWallpaper()
                ) {
                    when {
                        opening -> CircularProgressIndicator(
                            Modifier.align(Alignment.Center),
                            color = SaluColors.Green
                        )
                        conversation == null -> EmptyState(
                            icon = Icons.Outlined.ErrorOutline,
                            title = "This conversation could not be opened.",
                            message = "Go back and try again.",
                            tint = SaluColors.Error,
                            iconBackground = SaluColors.ErrorSoft,
                            modifier = Modifier.align(Alignment.Center)
                        )
                        messages.isEmpty() -> EmptyState(
                            icon = Icons.Outlined.ChatBubbleOutline,
                            title = "No messages yet",
                            message = "Say hello and ask about pickup, allergens or storage.",
                            modifier = Modifier.align(Alignment.Center)
                        )
                        else -> LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(entries, key = { it.key }) { entry ->
                                when (entry) {
                                    is ChatEntry.Day -> DayHeading(entry.label)
                                    is ChatEntry.Message -> MessageBubble(
                                        entry.message,
                                        mine = entry.message.senderId == myProfileId
                                    )
                                }
                            }
                        }
                    }
                }

                val chat = conversation
                if (reporting && chat != null) {
                    ReportPage(
                        target = ReportTarget.CHAT,
                        targetId = chat.id,
                        heading = "Report ${chat.otherName}",
                        prompt = "Why are you reporting ${chat.otherName}?",
                        onClose = { reporting = false }
                    )
                }

                if (conversation != null) {
                    Surface(color = Color.White) {
                        Column {
                            HorizontalDivider(color = SaluColors.Line)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = draft,
                                    onValueChange = { if (it.length <= 2000) messageViewModel.updateDraft(it) },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text("Type a message…", style = SaluText.Body) },
                                    textStyle = SaluText.Body.copy(fontSize = 15.sp),
                                    shape = RoundedCornerShape(24.dp),
                                    maxLines = 4,
                                    colors = saluFieldColors(container = SaluColors.Cream)
                                )
                                FilledIconButton(
                                    onClick = { messageViewModel.send(draft) },
                                    enabled = draft.isNotBlank() && !sending,
                                    modifier = Modifier
                                        .padding(bottom = 4.dp)
                                        .size(48.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = SaluColors.Green,
                                        contentColor = Color.White,
                                        disabledContainerColor = SaluColors.PaleGreen,
                                        disabledContentColor = SaluColors.Faint
                                    )
                                ) {
                                    if (sending) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Send message"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeading(label: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = SaluText.Meta,
            color = SaluColors.Gray,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(SaluColors.PaleGreen)
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .semantics { heading() }
        )
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, mine: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (mine) SaluColors.Green else Color.White,
            shape = RoundedCornerShape(
                topStart = 18.dp, topEnd = 18.dp,
                bottomStart = if (mine) 18.dp else 4.dp,
                bottomEnd = if (mine) 4.dp else 18.dp
            ),
            border = if (mine) null else BorderStroke(1.dp, SaluColors.Line),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 9.dp, bottom = 7.dp)) {
                Text(message.body, style = SaluText.Body.copy(fontSize = 15.sp, lineHeight = 21.sp), color = if (mine) Color.White else SaluColors.DarkGreen)
                Text(
                    formatBubbleTime(message.createdAt),
                    style = SaluText.Meta.copy(fontWeight = FontWeight.Normal),
                    color = if (mine) Color.White.copy(alpha = 0.8f) else SaluColors.Gray,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 2.dp)
                )
            }
        }
    }
}

/** The inbox: every thread the signed-in user is part of, newest first. */
@Composable
fun MessagesScreen(
    messageViewModel: MessageViewModel,
    onBack: () -> Unit
) {
    val conversations by messageViewModel.conversations.collectAsStateWithLifecycle()
    val loading by messageViewModel.conversationsLoading.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { messageViewModel.loadConversations() }
    val unread = conversations.sumOf { it.unreadCount }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SaluColors.Cream)
    ) {
        SaluTopBar(
            title = "Messages",
            onBack = onBack,
            backDescription = "Back to Home",
            subtitle = if (unread > 0) "$unread unread" else null
        )

        when {
            loading && conversations.isEmpty() -> SkeletonGroup(
                description = "Loading conversations",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                repeat(5) { ListRowSkeleton(thumbnail = 56.dp) }
            }
            conversations.isEmpty() -> EmptyState(
                icon = Icons.Outlined.ChatBubbleOutline,
                title = "No conversations yet",
                message = "Open a listing and tap “Message” to ask the donor about pickup, allergens or storage.",
                modifier = Modifier.padding(top = 48.dp)
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(conversations, key = { it.id }) { conversation ->
                    Column(Modifier.animateItem()) {
                        ConversationRow(conversation) { messageViewModel.openConversation(conversation.id) }
                        HorizontalDivider(
                            color = SaluColors.Line,
                            modifier = Modifier.padding(start = 90.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(conversation: ConversationSummary, onClick: () -> Unit) {
    val unread = conversation.unreadCount > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (unread) SaluColors.PaleGreen else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ListingThumbnail(conversation, Modifier.size(56.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conversation.otherName,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold
                    ),
                    color = SaluColors.DarkGreen,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    formatChatTime(conversation.lastMessageAt ?: conversation.createdAt),
                    style = SaluText.Meta,
                    color = if (unread) SaluColors.Green else SaluColors.Gray
                )
            }
            Text(
                (if (conversation.isDonor) "Your listing: " else "About: ") + conversation.foodName,
                style = SaluText.Meta,
                color = SaluColors.Green,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conversation.lastMessage?.let { (if (conversation.lastMessageMine == true) "You: " else "") + it }
                        ?: "No messages yet",
                    style = SaluText.Supporting.copy(
                        fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (unread) SaluColors.DarkGreen else SaluColors.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (unread) {
                    Spacer(Modifier.width(8.dp))
                    CountBadge(conversation.unreadCount)
                }
            }
        }
    }
}

/** The listing photo with the same fallbacks as the feed cards. */
@Composable
private fun ListingThumbnail(conversation: ConversationSummary, modifier: Modifier) {
    ListingPhoto(
        imageUrl = conversation.displayImageUrl,
        hasPhoto = !conversation.foodImagePath.isNullOrBlank(),
        contentDescription = conversation.foodName,
        modifier = modifier.clip(SaluRadius.Small)
    )
}

/** "3:05 PM" today, otherwise "Sep 24". */
private fun formatChatTime(value: String?): String {
    val instant = parseTimestamp(value) ?: return ""
    val zone = ZoneId.systemDefault()
    val pattern = if (instant.atZone(zone).toLocalDate() == LocalDate.now(zone)) "h:mm a" else "MMM d"
    return DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH).withZone(zone).format(instant)
}

/** "3:05 PM": the day is in the heading above the bubble. */
private fun formatBubbleTime(value: String?): String {
    val instant = parseTimestamp(value) ?: return ""
    return DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH).withZone(ZoneId.systemDefault()).format(instant)
}

/** Decorative vector wallpaper: cached paths, white base, no touch or accessibility nodes. */
private fun Modifier.foodDoodleWallpaper(): Modifier = drawWithCache {
    val outlines = listOf(
        "M12 39 L12 22 C3 20 6 7 17 9 C21 4 29 4 33 9 C44 7 47 20 38 22 L38 39 Z M18 25 L18 33 M25 24 L25 33 M32 25 L32 33",
        "M25 15 C12 5 5 18 10 30 C14 41 20 43 25 39 C31 44 38 38 41 27 C45 13 33 9 25 15 Z M25 15 C24 9 26 5 29 3 M28 9 C33 2 39 4 40 5 C36 12 31 12 28 9",
        "M12 22 C10 24 9 29 7 41 C19 37 29 30 32 22 C32 16 20 13 12 22 Z M29 16 C25 8 28 4 31 5 L33 14 C37 5 41 6 40 10 L36 17 C44 13 46 16 42 19 L34 21 M14 25 L19 28 M11 32 L15 34 M22 21 L26 24",
        "M5 23 L43 23 C41 33 36 39 24 39 C12 39 7 33 5 23 Z M16 42 L32 42 M15 18 C8 13 20 10 14 5 M25 18 C18 13 30 10 24 5 M35 18 C28 13 40 10 34 5",
        "M11 5 L11 17 C11 24 21 24 21 17 L21 5 M16 5 L16 42 M34 23 L34 42 M34 23 C23 23 27 5 34 5 C41 5 45 23 34 23 Z",
        "M7 19 C12 7 36 7 41 19 L24 42 Z M7 19 C15 23 33 23 41 19 M12 14 C20 17 29 17 36 14 M19 27 C19 24 23 24 23 27 C23 30 19 30 19 27 M27 32 C27 29 31 29 31 32 C31 35 27 35 27 32",
        "M12 32 C3 24 10 10 20 13 C23 3 36 7 36 17 C48 21 39 35 29 32 L28 43 L19 43 L19 33 M24 34 L24 24 M24 29 L17 24 M24 26 L30 21",
        "M24 40 C18 33 7 28 7 18 C7 8 18 6 24 15 C30 6 41 8 41 18 C41 28 30 33 24 40 Z"
    ).map { PathParser().parsePathString(it).toPath() }
    val cell = 60.dp.toPx()
    val iconScale = 40.dp.toPx() / 48f
    val line = Stroke(width = 1.3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val ink = Brush.linearGradient(
        colors = listOf(Color(0xFFACD7B5), Color(0xFFC5DA9F), Color(0xFFF0DA98)),
        start = Offset.Zero,
        end = Offset(48f, 48f)
    )
    onDrawBehind {
        drawRect(Color.White)
        clipRect {
            val rows = (size.height / cell).toInt() + 2
            val columns = (size.width / cell).toInt() + 2
            for (row in 0 until rows) {
                for (column in -1 until columns) {
                    val index = Math.floorMod(row * 3 + column, outlines.size)
                    val x = column * cell + (if (row % 2 == 0) 8.dp.toPx() else 38.dp.toPx())
                    val y = row * cell + 8.dp.toPx()
                    withTransform({
                        translate(x, y)
                        scale(iconScale, iconScale, pivot = Offset.Zero)
                        rotate(if (index % 2 == 0) -13f else 12f, pivot = Offset(24f, 24f))
                    }) {
                        drawPath(outlines[index], brush = ink, style = line)
                    }
                }
            }
        }
    }
}
