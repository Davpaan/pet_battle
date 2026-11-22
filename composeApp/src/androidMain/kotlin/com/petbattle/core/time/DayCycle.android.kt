package com.petbattle.core.time

import java.util.TimeZone

actual fun localUtcOffsetMillis(epochMillis: Long): Int = TimeZone.getDefault().getOffset(epochMillis)
