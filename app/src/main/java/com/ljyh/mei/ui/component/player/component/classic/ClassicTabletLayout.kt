package com.ljyh.mei.ui.component.player.component.classic

import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ljyh.mei.constants.PlayerHorizontalPadding
import com.ljyh.mei.constants.PlayerStyle
import com.ljyh.mei.constants.PlayerStyleKey
import com.ljyh.mei.ui.component.player.component.applemusic.AppleMusicDimensions
import com.ljyh.mei.ui.component.player.component.applemusic.playerMetadataAnchorBounds
import com.ljyh.mei.constants.ProgressBarStyle
import com.ljyh.mei.constants.ProgressBarStyleKey
import com.ljyh.mei.constants.TabletAnimationStyle
import com.ljyh.mei.constants.TabletAnimationStyleKey
import com.ljyh.mei.data.network.Resource
import com.ljyh.mei.ui.component.player.OverlayState
import com.ljyh.mei.ui.component.player.component.FluidProgressSlider
import com.ljyh.mei.ui.component.player.component.PlayerControls
import com.ljyh.mei.ui.component.player.component.PlayerActionToolbar
import com.ljyh.mei.ui.component.player.component.PlayerProgressSlider
import com.ljyh.mei.ui.component.player.component.PlayerTableControls
import com.ljyh.mei.ui.component.player.component.classic.component.Cover
import com.ljyh.mei.ui.component.player.component.classic.component.PlayerHeader
import com.ljyh.mei.ui.component.player.component.sheet.PlaylistContent
import com.ljyh.mei.ui.component.player.overlay.PlayerOverlayHandler
import com.ljyh.mei.ui.component.player.state.PlayerStateContainer
import com.ljyh.mei.ui.model.LyricSource
import com.ljyh.mei.utils.rememberEnumPreference

@Composable
fun ClassicTabletLayout(
    stateContainer: PlayerStateContainer,
    overlayHandler: PlayerOverlayHandler
) {
    val context = LocalContext.current
    var headerTextCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var headerRowCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val mediaMetadata by stateContainer.mediaMetadata
    val isPlaying by stateContainer.isPlaying
    val playbackState by stateContainer.playbackState
    val sliderPosition by remember { derivedStateOf { stateContainer.sliderPosition } }
    val duration by remember { derivedStateOf { stateContainer.duration } }
    val isLiked by stateContainer.isLiked
    val playerStyle by rememberEnumPreference(PlayerStyleKey, PlayerStyle.AppleMusic)

    var isShowingPlaylist by remember { mutableStateOf(false) }

    val (progressBarStyle, _) = rememberEnumPreference(
        key = ProgressBarStyleKey,
        defaultValue = ProgressBarStyle.LINEAR
    )

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Retain both panes' original geometry while giving the badge a full-height host.
        val paneWidth = ((maxWidth - 96.dp) / 2).coerceAtLeast(0.dp)
        val paneWidthPx = with(LocalDensity.current) { paneWidth.roundToPx() }
        val lyricVerticalPadding = maxHeight * 0.025f
        val layoutDirection = LocalLayoutDirection.current
        val paneCenterFraction = paneWidth / (paneWidth + 32.dp) / 2f
        val lyricTransformOrigin = TransformOrigin(
            if (layoutDirection == LayoutDirection.Ltr) paneCenterFraction else 1f - paneCenterFraction,
            0.5f,
        )
        val navigationPadding = WindowInsets.navigationBars.asPaddingValues()
        val safePadding = WindowInsets.navigationBars.union(WindowInsets.displayCutout).asPaddingValues()
        val badgeBottomPadding = maxOf(16.dp, navigationPadding.calculateBottomPadding())
        val badgeEndPadding = maxOf(badgeBottomPadding, safePadding.calculateEndPadding(layoutDirection))

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 32.dp)
        ){

            Column(
                modifier = Modifier
                    .width(paneWidth)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                mediaMetadata?.let {
                    Cover(
                        playerConnection = stateContainer.playerConnection,
                        mediaMetadata = it,
                        isPlaying = isPlaying,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .widthIn(max = paneWidth * 0.6f)
                            .aspectRatio(1f)
                    )
                }



                Spacer(Modifier.height(16.dp))
                mediaMetadata?.let {
                    PlayerHeader(
                        mediaMetadata = it,
                        needShadow = playerStyle != PlayerStyle.AppleMusic,
                        appleMusicStyle = playerStyle == PlayerStyle.AppleMusic,
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .padding(horizontal = PlayerHorizontalPadding),
                        onClick = {
                            overlayHandler.showAlbumArtist(
                                it.album,
                                it.artists,
                                it.coverUrl,
                                anchorBounds = playerMetadataAnchorBounds(headerTextCoordinates, headerRowCoordinates),
                            )
                        },
                        onTextPositioned = { headerTextCoordinates = it },
                        onRowPositioned = { headerRowCoordinates = it },
                        onMoreClick = {
                            overlayHandler.showMoreAction()
                        },
                        isLiked = isLiked,
                        onLikeClick = {
                            stateContainer.playerViewModel.like(it.id.toString())
                        }
                    )
                }

                Spacer(Modifier.height(32.dp))


                if (progressBarStyle == ProgressBarStyle.LINEAR) {
                    FluidProgressSlider(
                        position = sliderPosition.toLong(),
                        duration = duration,
                        onPositionChange = { newPosition ->
                            stateContainer.playerConnection.player.seekTo(newPosition)
                        },
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .padding(horizontal = if (playerStyle == PlayerStyle.AppleMusic) AppleMusicDimensions.progressInset else PlayerHorizontalPadding + 8.dp)
                    )
                } else {
                    PlayerProgressSlider(
                        position = sliderPosition.toLong(),
                        duration = duration,
                        isPlaying = isPlaying, // 波浪进度条需要这个参数
                        onPositionChange = { newPosition ->
                            stateContainer.playerConnection.player.seekTo(newPosition)
                        },
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .padding(horizontal = PlayerHorizontalPadding + 8.dp)
                    )
                }




                Spacer(Modifier.height(16.dp))


                PlayerTableControls(
                    playerConnection = stateContainer.playerConnection,
                    canSkipPrevious = stateContainer.canSkipPrevious.value,
                    canSkipNext = stateContainer.canSkipNext.value,
                    isPlaying = isPlaying,
                    playbackState = playbackState,
                    modifier = Modifier.fillMaxWidth(0.7f),
                    onPlaylistClick = { }
                )

            }

            Spacer(Modifier.width(32.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                PlaylistContent(
                    onDismiss = { },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
