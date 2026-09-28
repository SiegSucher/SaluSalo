package com.example.salusalo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

private val PhotoTile = Color(0xFFF2F6E6)
private val PhotoTileText = Color(0xFF666873)

/**
 * A listing photo from its 1-hour signed URL, with the same fallbacks
 * everywhere and never a sample picture:
 * - no photo stored ([hasPhoto] false): a plain tile;
 * - a stored photo that can't be shown (signing failed, the file is missing
 *   from Storage, or it failed to load): "Image unavailable" (an icon with
 *   that description on tiles too small for the text).
 */
@Composable
internal fun ListingPhoto(
    imageUrl: String?,
    hasPhoto: Boolean,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    textSize: TextUnit = 10.sp
) {
    var failed by remember(imageUrl) { mutableStateOf(false) }
    if (imageUrl != null && !failed) {
        AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
            onError = { failed = true }
        )
    } else {
        BoxWithConstraints(modifier.background(PhotoTile), contentAlignment = Alignment.Center) {
            if (hasPhoto) {
                if (maxWidth < 60.dp) {
                    Icon(
                        Icons.Outlined.BrokenImage,
                        contentDescription = "Image unavailable",
                        tint = PhotoTileText,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Text(
                        "Image unavailable",
                        modifier = Modifier.padding(4.dp),
                        fontSize = textSize,
                        color = PhotoTileText,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
