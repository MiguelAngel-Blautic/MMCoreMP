package com.blautic.mmcore.Camera

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun CameraComposable(modifier: Modifier = Modifier,
                            onImageAnalyzed: (Any?) -> Unit,
                            content: @Composable BoxScope.() -> Unit)