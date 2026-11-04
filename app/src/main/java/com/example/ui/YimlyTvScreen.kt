package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.YimlyDarkBg
import com.example.ui.theme.YimlyPink

import android.app.Activity
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@Composable
fun YimlyTvScreen(
    url: String,
    modifier: Modifier = Modifier
) {
    var showExitDialog by remember { mutableStateOf(false) }
    var isExiting by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Handle Back action from phone/tablet back gestures and system buttons
    BackHandler(enabled = !isExiting) {
        if (showExitDialog) {
            showExitDialog = false
        } else if (webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
        } else {
            showExitDialog = true
        }
    }

    if (showExitDialog) {
        YimlyExitDialog(
            isExiting = isExiting,
            onDismiss = {
                if (!isExiting) {
                    showExitDialog = false
                }
            },
            onConfirmExit = {
                if (!isExiting) {
                    isExiting = true
                    coroutineScope.launch {
                        // 1. Await actual completion of session end or timeout
                        val result = YimlyMediaShutdown.awaitSessionEndCompletion(webViewRef, timeoutMs = 3000L)
                        Log.i("YimlyExit", "Session termination result: inRoom=${result.inRoom}, success=${result.success}, status=${result.status}, error=${result.error}")

                        // 2. Stop all audio and video playback elements immediately
                        webViewRef?.let { webView ->
                            try {
                                webView.evaluateJavascript(YimlyMediaShutdown.PAUSE_MEDIA_JAVASCRIPT, null)
                            } catch (e: Exception) {
                                Log.w("YimlyExit", "Error pausing media on exit: ${e.message}")
                            }

                            // 3. Clean up and destroy WebView safely
                            try {
                                webView.webChromeClient = null
                                webView.webViewClient = WebViewClient()
                                webView.stopLoading()
                                webView.loadUrl("about:blank")
                                webView.clearHistory()
                                webView.removeAllViews()
                                webView.destroy()
                            } catch (e: Exception) {
                                Log.w("YimlyExit", "Error destroying WebView on exit: ${e.message}")
                            }
                        }

                        // 4. Finish activity and exit app cleanly
                        (context as? Activity)?.finish()
                    }
                }
            }
        )
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = YimlyDarkBg
    ) {
        YimlyHostRoomContainer(
            hostRoomUrl = url,
            onWebViewCreated = { webView ->
                webViewRef = webView
            },
            onExitRequested = {
                if (!isExiting) {
                    showExitDialog = true
                }
            }
        )
    }
}

@Composable
fun YimlyExitDialog(
    isExiting: Boolean,
    onDismiss: () -> Unit,
    onConfirmExit: () -> Unit
) {
    val cancelInteractionSource = remember { MutableInteractionSource() }
    val isCancelFocused by cancelInteractionSource.collectIsFocusedAsState()

    val confirmInteractionSource = remember { MutableInteractionSource() }
    val isConfirmFocused by confirmInteractionSource.collectIsFocusedAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .widthIn(min = 320.dp, max = 480.dp)
            .testTag("exit_dialog"),
        shape = RoundedCornerShape(24.dp),
        containerColor = Color(0xFF1B1428),
        tonalElevation = 6.dp,
        title = {
            Text(
                text = stringResource(R.string.exit_dialog_title),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                modifier = Modifier.testTag("exit_dialog_title")
            )
        },
        text = {
            Text(
                text = stringResource(R.string.exit_dialog_message),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    color = Color(0xFFDCD6E5),
                    lineHeight = 22.sp
                ),
                modifier = Modifier.testTag("exit_dialog_message")
            )
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                enabled = !isExiting,
                interactionSource = cancelInteractionSource,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCancelFocused) Color.White else Color(0xFF282038),
                    contentColor = if (isCancelFocused) Color(0xFF120D1D) else Color(0xFFE2DCED),
                    disabledContainerColor = Color(0xFF201A2C),
                    disabledContentColor = Color(0xFF7A708C)
                ),
                border = BorderStroke(
                    width = if (isCancelFocused) 2.5.dp else 1.dp,
                    color = if (isCancelFocused) YimlyPink else Color(0xFF3F3456)
                ),
                modifier = Modifier
                    .focusable(interactionSource = cancelInteractionSource)
                    .height(48.dp)
                    .testTag("exit_dialog_cancel")
            ) {
                Text(
                    text = stringResource(R.string.exit_dialog_cancel),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmExit,
                enabled = !isExiting,
                interactionSource = confirmInteractionSource,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConfirmFocused) Color.White else Color(0xFFE11D48),
                    contentColor = if (isConfirmFocused) Color(0xFF881337) else Color.White,
                    disabledContainerColor = Color(0xFF5A1626),
                    disabledContentColor = Color(0xFF9F727D)
                ),
                border = BorderStroke(
                    width = if (isConfirmFocused) 2.5.dp else 1.dp,
                    color = if (isConfirmFocused) Color(0xFFFB7185) else Color(0xFFBE123C)
                ),
                modifier = Modifier
                    .focusable(interactionSource = confirmInteractionSource)
                    .height(48.dp)
                    .testTag("exit_dialog_confirm")
            ) {
                Text(
                    text = if (isExiting) "Leaving…" else stringResource(R.string.exit_dialog_confirm),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    )
}

@Composable
fun YimlyHostRoomContainer(
    hostRoomUrl: String,
    modifier: Modifier = Modifier,
    onWebViewCreated: (WebView) -> Unit = {},
    onExitRequested: () -> Unit = {}
) {
    var isPageLoaded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("webview_container")
    ) {
        // Fullscreen host room webview
        YimlyHostRoomWebView(
            url = hostRoomUrl,
            modifier = Modifier.fillMaxSize(),
            onWebViewCreated = onWebViewCreated,
            onExitRequested = onExitRequested,
            onPageStarted = {
                // Page began loading
            },
            onPageFinished = {
                isPageLoaded = true
            }
        )

        // Smooth transition overlay while initial page renders
        AnimatedVisibility(
            visible = !isPageLoaded,
            exit = fadeOut(animationSpec = tween(400)),
            modifier = Modifier.fillMaxSize()
        ) {
            YimlyTvSplashScreen(
                statusText = stringResource(R.string.status_starting),
                isRetrying = false
            )
        }
    }
}

@Composable
fun YimlyTvSplashScreen(
    statusText: String,
    isRetrying: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "MicrophonePulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "MicPulseScale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF221124),
                        Color(0xFF130D1B),
                        YimlyDarkBg
                    ),
                    radius = 1600f
                )
            )
            .testTag("splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 48.dp)
        ) {
            // Existing Yimly Lucide Microphone Icon in #FF4FA3
            Box(
                modifier = Modifier
                    .size(108.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(Color(0xFF1B1428))
                    .border(BorderStroke(2.dp, YimlyPink.copy(alpha = 0.6f)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_yimly_mic),
                    contentDescription = "Yimly Microphone",
                    tint = YimlyPink,
                    modifier = Modifier.size(54.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // TV Display App Title
            Text(
                text = stringResource(R.string.title_yimly),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 6.sp,
                    color = Color.White,
                    fontFamily = FontFamily.SansSerif
                ),
                modifier = Modifier.testTag("app_title")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Subtitle status
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 1.2.sp,
                    color = Color(0xFFDCD6E5)
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(30.dp))

            // Loading spinner in Yimly Pink #FF4FA3
            CircularProgressIndicator(
                color = YimlyPink,
                trackColor = YimlyPink.copy(alpha = 0.2f),
                strokeWidth = 3.5.dp,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("loading_indicator")
            )
        }
    }
}

@Composable
fun YimlyTvErrorScreen(
    retryCountdown: Int,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF2B0E1D),
                        Color(0xFF180A15),
                        YimlyDarkBg
                    ),
                    radius = 1600f
                )
            )
            .testTag("error_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 48.dp)
        ) {
            // Yimly Microphone Icon in #FF4FA3
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1B1428))
                    .border(BorderStroke(2.dp, YimlyPink.copy(alpha = 0.6f)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_yimly_mic),
                    contentDescription = "Yimly Microphone",
                    tint = YimlyPink,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.title_yimly),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 6.sp,
                    color = Color.White
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.status_starting),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = YimlyPink
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.status_connecting),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    color = Color(0xFFDCD6E5)
                )
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Retrying in ${retryCountdown}s…",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    color = Color(0xFFAAA3B5)
                )
            )

            Spacer(modifier = Modifier.height(28.dp))

            // TV Remote Accessible Action Button
            Button(
                onClick = onRetry,
                interactionSource = interactionSource,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isFocused) Color.White else YimlyPink,
                    contentColor = if (isFocused) Color(0xFF120D1D) else Color.White
                ),
                border = if (isFocused) {
                    BorderStroke(3.dp, YimlyPink)
                } else {
                    BorderStroke(1.dp, YimlyPink.copy(alpha = 0.5f))
                },
                modifier = Modifier
                    .focusable(interactionSource = interactionSource)
                    .widthIn(min = 200.dp)
                    .height(52.dp)
                    .testTag("retry_button")
            ) {
                Text(
                    text = stringResource(R.string.action_retry),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )
            }
        }
    }
}
