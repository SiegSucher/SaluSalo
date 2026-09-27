package com.example.salusalo

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.salusalo.model.ChatMessage
import com.example.salusalo.model.ConversationSummary
import com.example.salusalo.model.parseTimestamp
import com.example.salusalo.viewmodel.MessageViewModel
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CGreen = Color(0xFF47782E)
private val CDark = Color(0xFF263C29)
private val CCream = Color(0xFFFCFBF6)
private val CPale = Color(0xFFF3F6E8)
private val CGray = Color(0xFF666873)
private val CLine = Color(0xFFE3E4E7)
private val COrange = Color(0xFFEF7900)

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
    val initialDraft by messageViewModel.draft.collectAsStateWithLifecycle()
    var draft by rememberSaveable(conversation?.id) { mutableStateOf("") }
    LaunchedEffect(conversation?.id, initialDraft) {
        if (conversation != null && draft.isEmpty() && initialDraft.isNotEmpty()) draft = initialDraft
    }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = CCream) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                Surface(color = Color.White, shadowElevation = 2.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onClose) { Text("Back") }
                        conversation?.let {
                            ListingThumbnail(it, Modifier.size(40.dp))
                            Spacer(Modifier.size(10.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                conversation?.otherName ?: "Messages",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = CDark,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            conversation?.let {
                                Text(
                                    (if (it.isDonor) "Asking about your listing: " else "About: ") + it.foodName,
                                    fontSize = 12.sp,
                                    color = CGray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        opening -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = CGreen)
                        conversation == null -> Text(
                            "This conversation could not be opened.",
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            color = CGray
                        )
                        messages.isEmpty() -> Text(
                            "No messages yet. Say hello and ask about pickup, allergens or storage.",
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            color = CGray,
                            fontSize = 13.sp
                        )
                        else -> LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(messages, key = { it.id }) { message ->
                                MessageBubble(message, mine = message.senderId == myProfileId)
                            }
                        }
                    }
                }

                if (conversation != null) {
                    Surface(color = Color.White, shadowElevation = 4.dp) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = draft,
                                onValueChange = { if (it.length <= 2000) draft = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Type a message…") },
                                shape = RoundedCornerShape(18.dp),
                                maxLines = 4
                            )
                            Button(
                                enabled = draft.isNotBlank() && !sending,
                                onClick = { messageViewModel.send(draft) { draft = "" } },
                                colors = ButtonDefaults.buttonColors(containerColor = CGreen)
                            ) {
                                Text(if (sending) "Sending" else "Send", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, mine: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (mine) CGreen else Color.White,
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (mine) 16.dp else 4.dp,
                bottomEnd = if (mine) 4.dp else 16.dp
            ),
            shadowElevation = if (mine) 0.dp else 1.dp,
            modifier = Modifier.widthIn(max = 290.dp)
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(message.body, fontSize = 14.sp, lineHeight = 19.sp, color = if (mine) Color.White else CDark)
                Text(
                    formatChatTime(message.createdAt),
                    fontSize = 10.sp,
                    color = if (mine) Color.White.copy(alpha = 0.8f) else CGray,
                    modifier = Modifier.align(Alignment.End)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CCream)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Messages", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CDark, modifier = Modifier.weight(1f))
        }
        HorizontalDivider(color = CLine)

        when {
            loading && conversations.isEmpty() -> Box(Modifier.fillMaxSize()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = CGreen)
            }
            conversations.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp)) {
                Text(
                    "No conversations yet. Open a listing and tap \"Message Donor\" to ask a question.",
                    modifier = Modifier.align(Alignment.Center),
                    color = CGray,
                    fontSize = 13.sp
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                items(conversations, key = { it.id }) { conversation ->
                    ConversationRow(conversation) { messageViewModel.openConversation(conversation.id) }
                    HorizontalDivider(color = CLine)
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
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ListingThumbnail(conversation, Modifier.size(52.dp))
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conversation.otherName,
                    fontSize = 15.sp,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold,
                    color = CDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    formatChatTime(conversation.lastMessageAt ?: conversation.createdAt),
                    fontSize = 11.sp,
                    color = CGray
                )
            }
            Text(
                (if (conversation.isDonor) "Your listing: " else "") + conversation.foodName,
                fontSize = 12.sp,
                color = CGreen,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conversation.lastMessage?.let { (if (conversation.lastMessageMine == true) "You: " else "") + it }
                        ?: "No messages yet",
                    fontSize = 13.sp,
                    color = if (unread) CDark else CGray,
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (unread) {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clip(CircleShape)
                            .background(COrange)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            if (conversation.unreadCount > 99) "99+" else conversation.unreadCount.toString(),
                            fontSize = 11.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/** The listing photo with the same fallbacks as the feed cards. */
@Composable
private fun ListingThumbnail(conversation: ConversationSummary, modifier: Modifier) {
    val shape = RoundedCornerShape(10.dp)
    var failed by remember(conversation.id, conversation.displayImageUrl) { mutableStateOf(false) }
    if (conversation.displayImageUrl != null && !failed) {
        AsyncImage(
            model = conversation.displayImageUrl,
            contentDescription = conversation.foodName,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(shape),
            onError = { failed = true }
        )
    } else if (conversation.displayImageUrl == null) {
        Image(
            painter = painterResource(R.drawable.food_produce),
            contentDescription = conversation.foodName,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(shape)
        )
    } else {
        Box(modifier.clip(shape).background(CPale))
    }
}

/** "3:05 PM" today, otherwise "Sep 24". */
private fun formatChatTime(value: String?): String {
    val instant = parseTimestamp(value) ?: return ""
    val zone = ZoneId.systemDefault()
    val pattern = if (instant.atZone(zone).toLocalDate() == LocalDate.now(zone)) "h:mm a" else "MMM d"
    return DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH).withZone(zone).format(instant)
}
