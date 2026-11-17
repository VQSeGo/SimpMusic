package com.maxrave.simpmusic.ui.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Room at the end of a page, so its last item clears the mini player and the bottom bar. */
@Composable
fun EndOfPage() {
    Spacer(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(280.dp),
    )
}
