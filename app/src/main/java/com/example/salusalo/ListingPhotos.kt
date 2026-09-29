package com.example.salusalo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.PlatformContext
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade

private val PhotoTile = SaluColors.PaleGreen
private val PhotoTileText = SaluColors.Gray

/**
 * An image request for a 1-hour signed Storage URL, cached by the object's
 * path instead of the whole URL. Every signing gives the URL a new token, so
 * caching by URL downloaded each photo again after every reload. A path never
 * gets other bytes: listing photos can't be replaced (a retried post re-uploads
 * only while its listing doesn't exist yet), and each profile photo gets a new
 * path. Coil still decodes to the size shown; the file downloaded is the stored
 * one (up to 1600 px), once per phone while it stays in the disk cache.
 */
internal fun signedImageRequest(context: PlatformContext, url: String): ImageRequest {
    val key = url.substringBefore('?')
    return ImageRequest.Builder(context)
        .data(url)
        .memoryCacheKey(key)
        .diskCacheKey(key)
        .crossfade(true)
        .build()
}

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
    textSize: TextUnit = 10.sp,
    // For example to fetch fresh signed URLs: they expire after 1 hour.
    onLoadError: () -> Unit = {}
) {
    var failed by remember(imageUrl) { mutableStateOf(false) }
    if (imageUrl != null && !failed) {
        // Cream while it loads, then a short fade-in instead of a sudden pop.
        val context = LocalPlatformContext.current
        val request = remember(imageUrl, context) { signedImageRequest(context, imageUrl) }
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier.background(PhotoTile),
            onError = {
                failed = true
                onLoadError()
            }
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

/**
 * A listing's photos full screen on a dark background, shown whole (Fit).
 * Swipe between them; [onClose] gets the photo being viewed, so the details
 * header can show the same one. [imageUrls] and [hasPhotos] are per photo,
 * with the same fallbacks as [ListingPhoto]. No pinch zoom.
 */
@Composable
internal fun FullScreenPhotos(
    title: String,
    imageUrls: List<String?>,
    hasPhotos: List<Boolean>,
    startPage: Int,
    onClose: (page: Int) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = startPage, pageCount = { imageUrls.size })
    Dialog(
        onDismissRequest = { onClose(pagerState.currentPage) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index ->
                ListingPhoto(
                    imageUrl = imageUrls[index],
                    hasPhoto = hasPhotos.getOrElse(index) { false },
                    contentDescription = if (imageUrls.size > 1) "$title, photo ${index + 1}" else title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    textSize = 14.sp
                )
            }

            FilledIconButton(
                onClick = { onClose(pagerState.currentPage) },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .safeDrawingPadding()
                    .padding(16.dp)
                    .size(42.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.White,
                    contentColor = SaluColors.DarkGreen
                )
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Close photos")
            }

            if (imageUrls.size > 1) {
                Text(
                    "${pagerState.currentPage + 1} / ${imageUrls.size}",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .safeDrawingPadding()
                        .padding(top = 24.dp, end = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    color = Color.White,
                    fontSize = 13.sp
                )
            }
        }
    }
}
