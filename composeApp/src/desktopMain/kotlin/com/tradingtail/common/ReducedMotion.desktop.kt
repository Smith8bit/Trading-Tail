package com.tradingtail.common

import androidx.compose.runtime.Composable

// ponytail: Windows exposes "Show animations" via SystemParametersInfo(SPI_GETCLIENTAREAANIMATION),
// which needs JNI/JNA to read. No new dependency is worth one boolean — desktop keeps motion on.
// Wire it through JNA if a desktop user ever asks for reduced motion.
@Composable
actual fun reducedMotion(): Boolean = false
