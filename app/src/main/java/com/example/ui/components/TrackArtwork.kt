package com.example.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.AudioTrack
import com.example.data.model.TrackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TrackArtwork(
    track: AudioTrack?,
    loadEmbeddedArt: suspend (AudioTrack) -> ByteArray?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp)
) {
    val embeddedBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = track?.id) {
        value = if (track != null && track.source == TrackSource.LOCAL) {
            val bytes = loadEmbeddedArt(track)
            if (bytes != null) {
                withContext(Dispatchers.Default) {
                    runCatching {
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }.getOrNull()
                }
            } else {
                null
            }
        } else {
            null
        }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
    ) {
        when {
            track?.source == TrackSource.YOUTUBE && !track.thumbnailUrl.isNullOrBlank() -> {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = "${track.title} artwork",
                    contentScale = ContentScale.Crop,
                    error = painterResource(id = R.drawable.img_default_album_art),
                    placeholder = painterResource(id = R.drawable.img_default_album_art),
                    modifier = Modifier.fillMaxSize()
                )
            }
            embeddedBitmap != null -> {
                Image(
                    bitmap = embeddedBitmap!!,
                    contentDescription = "${track?.title ?: "Track"} album art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                Image(
                    painter = painterResource(id = R.drawable.img_default_album_art),
                    contentDescription = "Default album artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
