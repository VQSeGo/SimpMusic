package com.maxrave.simpmusic

import android.os.Process
import android.os.SystemClock

actual fun getPlatform(): Platform = Platform.Android

actual fun millisSinceProcessStart(): Long = SystemClock.uptimeMillis() - Process.getStartUptimeMillis()
