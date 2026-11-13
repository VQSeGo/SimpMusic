package com.maxrave.simpmusic

import platform.UIKit.UIDevice
actual fun getPlatform(): Platform = Platform.iOS

// No cheap process start time on iOS; the marks only matter on Android and desktop.
actual fun millisSinceProcessStart(): Long = 0L
