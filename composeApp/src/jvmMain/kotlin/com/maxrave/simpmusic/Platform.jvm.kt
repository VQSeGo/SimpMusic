package com.maxrave.simpmusic

import java.lang.management.ManagementFactory

actual fun getPlatform(): Platform = Platform.Desktop

actual fun millisSinceProcessStart(): Long = System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().startTime
