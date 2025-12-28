/*
 * Copyright (C) 2025 O​u​t​er​Tu​ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.vikify.app.utils

import androidx.compose.ui.util.fastAny
import androidx.media3.exoplayer.offline.Download
import com.vikify.app.constants.MAX_COIL_JOBS
import com.vikify.app.constants.MAX_DL_JOBS
import com.vikify.app.constants.MAX_LM_SCANNER_JOBS
import com.vikify.app.constants.MAX_YTM_CONTENT_JOBS
import com.vikify.app.constants.MAX_YTM_SYNC_JOBS
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.newFixedThreadPoolContext
import java.time.LocalDateTime


/**
 *
 * coilCoroutine: Coil image resolution
 * lmScannerCoroutine: Heave processing tasks such as local media scan/extraction and downloads processing
 *
 */
// This will go down to be the best idea I've had or this will crash and burn like the Hindenburg.

val lmScannerCoroutine = Dispatchers.IO.limitedParallelism(MAX_LM_SCANNER_JOBS)

val dlCoroutine = Dispatchers.IO.limitedParallelism(MAX_DL_JOBS)

val coilCoroutine = Dispatchers.IO.limitedParallelism(MAX_COIL_JOBS)

val syncCoroutine = Dispatchers.IO.limitedParallelism(MAX_YTM_SYNC_JOBS)

val ytmCoroutine = Dispatchers.IO.limitedParallelism(MAX_YTM_CONTENT_JOBS)

@OptIn(DelicateCoroutinesApi::class)
val playerCoroutine = newFixedThreadPoolContext(4, "player_service_offload")

fun reportException(throwable: Throwable) {
    throwable.printStackTrace()
}

fun getThumbnailModel(thumbnailUrl: String, sizeX: Int = -1, sizeY: Int = -1): Any? {
    return if (thumbnailUrl.startsWith("/storage/")) {
        LocalArtworkPath(thumbnailUrl, sizeX, sizeY)
    } else {
        thumbnailUrl
    }
}
