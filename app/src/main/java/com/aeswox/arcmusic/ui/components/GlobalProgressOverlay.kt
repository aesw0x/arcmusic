package com.aeswox.arcmusic.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import com.aeswox.arcmusic.ui.haptics.AppHaptics
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.aeswox.arcmusic.db.MediaScannerManager
import com.aeswox.arcmusic.sharing.ConnectionRequest
import com.aeswox.arcmusic.sharing.NfcShareService
import com.aeswox.arcmusic.sharing.NearbySharingManager
import com.aeswox.arcmusic.sharing.SharingState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val NFC_TAP_PILL_TIMEOUT_MS = 30_000L
private const val CONNECTION_ERROR_VISIBLE_MS = 2_500L
private const val EXPANDED_AUTO_COLLAPSE_DELAY_MS = 3_000L

@HiltViewModel
class GlobalOverlayViewModel @Inject constructor(
    private val scannerManager: MediaScannerManager,
    private val sharingManager: NearbySharingManager
) : ViewModel() {
    val scanProgress = scannerManager.scanProgress
    val sharingState = sharingManager.sharingState
    val transferProgress = sharingManager.transferProgress
    val completedTransferCount = sharingManager.completedTransferCount
    val totalTransferCount = sharingManager.totalTransferCount
    val currentTransferTitle = sharingManager.currentTransferTitle
    val currentTransferArtworkB64 = sharingManager.currentTransferArtworkB64
    val isReceiving = sharingManager.isReceiving
    val incomingSenderName = sharingManager.incomingSenderName
    val connectionRequest = sharingManager.connectionRequest

    fun acceptConnection(request: ConnectionRequest) = sharingManager.acceptConnection(request.endpointId)
    fun rejectConnection(request: ConnectionRequest) = sharingManager.rejectConnection(request.endpointId)
    fun resetSharing() = sharingManager.reset()
}

@Composable
fun GlobalProgressOverlay(
    modifier: Modifier = Modifier,
    currentRoute: String? = null,
    viewModel: GlobalOverlayViewModel = hiltViewModel()
) {
    val scanProgress by viewModel.scanProgress.collectAsState()
    val sharingState by viewModel.sharingState.collectAsState()
    val transferProgress by viewModel.transferProgress.collectAsState()
    val completedTransferCount by viewModel.completedTransferCount.collectAsState()
    val totalTransferCount by viewModel.totalTransferCount.collectAsState()
    val currentTitle by viewModel.currentTransferTitle.collectAsState()
    val artworkB64 by viewModel.currentTransferArtworkB64.collectAsState()
    val isReceiving by viewModel.isReceiving.collectAsState()
    val senderName by viewModel.incomingSenderName.collectAsState()
    val connectionRequest by viewModel.connectionRequest.collectAsState()
    val pendingConnectionRequest = connectionRequest
    val tapDetectedAt by NfcShareService.tapDetectedAtElapsedRealtime.collectAsState()
    val connectionFailedAt by NfcShareService.connectionFailedAtElapsedRealtime.collectAsState()
    var showConnectionError by remember { mutableStateOf(false) }

    LaunchedEffect(connectionFailedAt) {
        val failedAt = connectionFailedAt ?: return@LaunchedEffect
        showConnectionError = true
        AppHaptics.reject()
        delay(CONNECTION_ERROR_VISIBLE_MS)
        showConnectionError = false
        NfcShareService.clearConnectionFailed(failedAt)
    }

    LaunchedEffect(connectionFailedAt, tapDetectedAt) {
        val detectedAt = tapDetectedAt
        if (connectionFailedAt != null && detectedAt != null) {
            NfcShareService.clearTapDetected(detectedAt)
        }
    }

    val isScanning = scanProgress.isRunning
    val isTransferring = sharingState == SharingState.TRANSFERRING
    val isTransferComplete = sharingState == SharingState.COMPLETED
    val isConnectedPreparing = sharingState == SharingState.CONNECTED
    val connectionStarted = isConnectedPreparing || isTransferring || isTransferComplete
    val isTapDetected = tapDetectedAt?.let {
        android.os.SystemClock.elapsedRealtime() - it < NFC_TAP_PILL_TIMEOUT_MS
    } == true && !connectionStarted
    val hasTransferActivity = isTapDetected || isConnectedPreparing || isTransferring || isTransferComplete
    val showConnectionErrorForUi = showConnectionError && tapDetectedAt == null

    LaunchedEffect(tapDetectedAt, connectionStarted) {
        val detectedAt = tapDetectedAt ?: return@LaunchedEffect
        if (connectionStarted) {
            NfcShareService.clearTapDetected(detectedAt)
        } else {
            val elapsed = android.os.SystemClock.elapsedRealtime() - detectedAt
            delay((NFC_TAP_PILL_TIMEOUT_MS - elapsed).coerceAtLeast(0L))
            NfcShareService.clearTapDetected(detectedAt)
        }
    }

    // ReceiveScreen owns its full-screen card. Other routes use this shared overlay.
    val isExcludedRoute = currentRoute == "media_management" ||
        currentRoute == "receive" ||
        currentRoute?.startsWith("share") == true
    val shouldShow = (isScanning || hasTransferActivity || connectionRequest != null || showConnectionErrorForUi) && !isExcludedRoute
    val canExpandPill = (hasTransferActivity || connectionRequest != null || showConnectionErrorForUi) && !isExcludedRoute
    var isExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(canExpandPill, shouldShow) {
        if (!canExpandPill) {
            if (!shouldShow) {
                delay(220L)
            }
            isExpanded = false
        }
    }

    var lastInteractionTrigger by remember { mutableLongStateOf(0L) }
    var isInteractingWithExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            lastInteractionTrigger = android.os.SystemClock.elapsedRealtime()
        }
    }

    LaunchedEffect(isExpanded, lastInteractionTrigger, isInteractingWithExpanded) {
        if (isExpanded && !isInteractingWithExpanded) {
            delay(EXPANDED_AUTO_COLLAPSE_DELAY_MS)
            isExpanded = false
        }
    }

    LaunchedEffect(isTransferComplete) {
        if (isTransferComplete) {
            AppHaptics.favorite(true)
            delay(3_000L)
            viewModel.resetSharing()
        }
    }

    val pillJellyScale = remember { Animatable(1f) }
    var hasInitializedExpansion by remember { mutableStateOf(false) }
    LaunchedEffect(isExpanded) {
        if (!hasInitializedExpansion) {
            hasInitializedExpansion = true
            return@LaunchedEffect
        }
        AppHaptics.click()

        // A tiny squash-pop on expand/collapse, matching the app's jelly button motion.
        pillJellyScale.animateTo(if (isExpanded) 0.988f else 0.994f, tween(55, easing = FastOutSlowInEasing))
        pillJellyScale.animateTo(
            if (isExpanded) 1.012f else 1.006f,
            spring(dampingRatio = 0.58f, stiffness = 900f)
        )
        pillJellyScale.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 1100f))
    }

    val artwork by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, artworkB64) {
        value = withContext(Dispatchers.Default) {
            artworkB64?.let { encoded ->
                runCatching {
                    val bytes = Base64.decode(encoded, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }.getOrNull()
            }
        }
    }

    AnimatedVisibility(
        visible = shouldShow,
        enter = slideInVertically(
            initialOffsetY = { -it - 100 },
            animationSpec = spring(dampingRatio = 0.9f, stiffness = 520f)
        ) + fadeIn(animationSpec = tween(190, easing = FastOutSlowInEasing)) + scaleIn(
            initialScale = 0.94f,
            animationSpec = spring(dampingRatio = 0.88f, stiffness = 520f)
        ),
        exit = if (isExpanded) {
            fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                scaleOut(targetScale = 0.92f, animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                slideOutVertically(
                    targetOffsetY = { -40 },
                    animationSpec = tween(180, easing = FastOutSlowInEasing)
                )
        } else {
            slideOutVertically(
                targetOffsetY = { -it - 100 },
                animationSpec = tween(210, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(170, easing = FastOutSlowInEasing)) +
                scaleOut(targetScale = 0.97f, animationSpec = tween(170, easing = FastOutSlowInEasing))
        },
        modifier = modifier.fillMaxSize()
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            androidx.compose.animation.AnimatedVisibility(
                visible = isExpanded && shouldShow,
                enter = fadeIn(tween(180, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(180, easing = FastOutSlowInEasing))
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.22f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            AppHaptics.click()
                            if (isTransferComplete) {
                                viewModel.resetSharing()
                            } else {
                                isExpanded = false
                            }
                        }
                )
            }

            val cornerRadius by animateDpAsState(
                targetValue = if (isExpanded) 30.dp else 36.dp,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 520f),
                label = "transfer_pill_corner_radius"
            )
            val availableWidth = (maxWidth - 32.dp).coerceAtLeast(1.dp)
            val availableHeight = (maxHeight - 64.dp).coerceAtLeast(1.dp)
            val pillWidth by animateDpAsState(
                targetValue = (if (isExpanded) 320.dp else 270.dp).coerceAtMost(availableWidth),
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 520f),
                label = "transfer_pill_width"
            )
            val pillHeight by animateDpAsState(
                targetValue = (if (isExpanded) 320.dp else if (pendingConnectionRequest != null) 88.dp else 68.dp).coerceAtMost(availableHeight),
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 520f),
                label = "transfer_pill_height"
            )
            val isDark = MaterialTheme.colorScheme.background.luminance() < 0.05f
            val targetPillColor = when {
                isExpanded -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.98f)
                isDark -> MaterialTheme.colorScheme.surfaceContainerHigh
                else -> Color.White
            }
            val pillColor by animateColorAsState(
                targetValue = targetPillColor,
                animationSpec = tween(240, easing = FastOutSlowInEasing),
                label = "transfer_pill_color"
            )
            val borderColor = if (isDark) {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
            }

            Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    modifier = Modifier
                        .width(pillWidth)
                        .height(pillHeight)
                        .shadow(if (isDark) 12.dp else 20.dp, RoundedCornerShape(cornerRadius))
                        .border(1.dp, borderColor, RoundedCornerShape(cornerRadius))
                        .clip(RoundedCornerShape(cornerRadius))
                        .graphicsLayer {
                            scaleX = pillJellyScale.value
                            scaleY = pillJellyScale.value
                        }
                        .then(
                            if (isExpanded) {
                                Modifier.pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            isInteractingWithExpanded = event.changes.any { it.pressed }
                                            lastInteractionTrigger = android.os.SystemClock.elapsedRealtime()
                                        }
                                    }
                                }
                            } else {
                                Modifier.clickable {
                                    if (canExpandPill) {
                                        AppHaptics.click()
                                        isExpanded = true
                                    }
                                }
                            }
                        ),
                    shape = RoundedCornerShape(cornerRadius),
                    color = pillColor,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    AnimatedContent(
                        targetState = isExpanded,
                        transitionSpec = {
                            (fadeIn(tween(170, easing = FastOutSlowInEasing)) + scaleIn(initialScale = 0.985f, animationSpec = spring(dampingRatio = 0.92f, stiffness = 650f))) togetherWith
                                (fadeOut(tween(110, easing = FastOutSlowInEasing)) + scaleOut(targetScale = 0.99f, animationSpec = tween(110, easing = FastOutSlowInEasing))) using
                                SizeTransform(clip = false) { _, _ -> spring(dampingRatio = 0.9f, stiffness = 520f) }
                        },
                        label = "transfer_pill_content"
                    ) { expanded ->
                        if (expanded) {
                            if (pendingConnectionRequest != null) {
                                ConnectionRequestDetails(
                                    request = pendingConnectionRequest,
                                    onAccept = {
                                        AppHaptics.click()
                                        viewModel.acceptConnection(it)
                                    },
                                    onReject = {
                                        AppHaptics.reject()
                                        viewModel.rejectConnection(it)
                                    }
                                )
                            } else {
                                TransferDetailsContent(
                                    progress = transferProgress,
                                    completedCount = completedTransferCount,
                                    totalCount = totalTransferCount,
                                    title = currentTitle,
                                    peerName = senderName,
                                    artwork = artwork,
                                    isReceiving = isReceiving,
                                    isTapDetected = isTapDetected,
                                    isPreparing = isConnectedPreparing,
                                    completed = isTransferComplete,
                                    failed = showConnectionErrorForUi,
                                    onCollapse = {
                                        AppHaptics.click()
                                        if (isTransferComplete) {
                                            viewModel.resetSharing()
                                        } else {
                                            isExpanded = false
                                        }
                                    }
                                )
                            }
                        } else {
                            ProgressPill(
                                isTransferring = isTransferring,
                                isTapDetected = isTapDetected,
                                isPreparing = isConnectedPreparing,
                                isScanning = isScanning,
                                isReceiving = isReceiving,
                                scanTitle = scanProgress.phase.label,
                                scanCurrent = scanProgress.current,
                                scanTotal = scanProgress.total,
                                progress = transferProgress,
                                completedCount = completedTransferCount,
                                totalCount = totalTransferCount,
                                title = currentTitle,
                                completed = isTransferComplete,
                                failed = showConnectionErrorForUi,
                                request = connectionRequest,
                                onAccept = {
                                    AppHaptics.click()
                                    viewModel.acceptConnection(it)
                                },
                                onReject = {
                                    AppHaptics.reject()
                                    viewModel.rejectConnection(it)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressPill(
    isTransferring: Boolean,
    isTapDetected: Boolean,
    isPreparing: Boolean,
    isScanning: Boolean,
    isReceiving: Boolean,
    scanTitle: String,
    scanCurrent: Int,
    scanTotal: Int,
    progress: Float,
    completedCount: Int,
    totalCount: Int,
    title: String?,
    completed: Boolean,
    failed: Boolean,
    request: ConnectionRequest?,
    onAccept: (ConnectionRequest) -> Unit,
    onReject: (ConnectionRequest) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = if (request != null) 6.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
            when {
                request != null -> {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${request.endpointName} wants to send",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { onReject(request) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                                Text("Decline", color = MaterialTheme.colorScheme.error)
                            }
                            TextButton(onClick = { onAccept(request) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                                Text("Accept", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                failed -> {
                    Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Column {
                        Text("Couldn’t connect", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Text("Try tapping the devices again", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                isTapDetected -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.5.dp
                    )
                    Column {
                        Text("Tap detected", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Text("Connecting to nearby device…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                completed -> {
                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(
                            if (isReceiving) "Music received" else "Transfer complete",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text("Tap to view details", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                isTransferring -> {
                    CircularProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.5.dp,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                    Column {
                        Text(
                            when {
                                title.isNullOrBlank() -> if (isReceiving) "Receiving files…" else "Sending files…"
                                isReceiving -> "Receiving $title"
                                else -> "Sending $title"
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (totalCount > 0) "$completedCount of $totalCount files" else "Transfer in progress",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                isScanning -> {
                    CircularProgressIndicator(
                        progress = { scanCurrent.toFloat() / scanTotal.coerceAtLeast(1) },
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.5.dp,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                    Column {
                        Text(scanTitle, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        if (scanTotal > 0) {
                            Text("$scanCurrent of $scanTotal tracks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                isPreparing -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.5.dp
                    )
                    Column {
                        Text("Preparing transfer…", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Text("Connected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.5.dp,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                    Column {
                        Text("Transferring Media", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Text(
                            if (totalCount > 0) "$completedCount of $totalCount files" else "Transfer in progress",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
    }
}

@Composable
private fun TransferDetailsContent(
    progress: Float,
    completedCount: Int,
    totalCount: Int,
    title: String?,
    peerName: String?,
    artwork: androidx.compose.ui.graphics.ImageBitmap?,
    isReceiving: Boolean,
    isTapDetected: Boolean,
    isPreparing: Boolean,
    completed: Boolean,
    failed: Boolean,
    onCollapse: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(400, easing = LinearOutSlowInEasing),
        label = "incoming_transfer_progress"
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    completed && isReceiving -> "Transfer complete"
                    completed -> "Sent successfully"
                    failed -> "Couldn’t connect"
                    isTapDetected -> "Tap detected"
                    isPreparing -> "Preparing transfer…"
                    isReceiving -> "Receiving from ${peerName ?: "Device"}"
                    else -> "Sending to ${peerName ?: "Device"}"
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(onClick = onCollapse) { Text("Done") }
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.size(136.dp), contentAlignment = Alignment.Center) {
            if (failed) {
                Box(
                    Modifier.size(116.dp).clip(CircleShape).background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(52.dp))
                }
            } else if (!isTapDetected && !isPreparing && artwork != null) {
                Image(
                    bitmap = artwork,
                    contentDescription = null,
                    modifier = Modifier.size(116.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    Modifier.size(116.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                }
            }
            if (failed) {
                // The error symbol fills the artwork slot above; there is no active progress ring.
            } else if (completed) {
                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.BottomEnd).size(36.dp).background(MaterialTheme.colorScheme.surface, CircleShape).padding(6.dp))
            } else if (isTapDetected || isPreparing) {
                CircularProgressIndicator(modifier = Modifier.fillMaxSize(), strokeWidth = 5.dp)
            } else {
                CircularProgressIndicator(
                    progress = { animatedProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 5.dp,
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            when {
                failed -> "Tap the devices again to retry the connection"
                completed && isReceiving -> "Music received from ${peerName ?: "Device"}"
                completed -> "Transfer sent successfully"
                isTapDetected -> "Connecting to nearby device"
                isPreparing -> "Waiting for transfer to start"
                totalCount > 0 -> "${if (isReceiving) "Receiving" else "Sending"} ${completedCount + 1} of $totalCount"
                else -> if (isReceiving) "Receiving music" else "Sending music"
            },
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                failed || isTapDetected || isPreparing -> ""
                totalCount > 1 -> "$totalCount items"
                else -> title.orEmpty().ifBlank { "Transfer details" }
            },
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ConnectionRequestDetails(
    request: ConnectionRequest,
    onAccept: (ConnectionRequest) -> Unit,
    onReject: (ConnectionRequest) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Transfer request", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Text(
            "${request.endpointName} wants to send music",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            TextButton(onClick = { onReject(request) }) { Text("Decline", color = MaterialTheme.colorScheme.error) }
            Button(onClick = { onAccept(request) }) { Text("Accept") }
        }
    }
}
