package com.chethan616.clearpdf.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Larger screens (#157): a tablet or an unfolded phone is not a wider phone. Everything here leaves a
 * phone exactly as it is: each rule only starts past the widest phone.
 */

/** The widest a screen of rows and text grows (dp) before it is held to a column in the middle. */
val ReadableWidth: Dp = 720.dp

/**
 * A screen's content as a column: the full width on a phone; on a wider screen, at most [max] in the
 * middle, so its rows, fields and lines of text don't run from one edge of a tablet to the other.
 */
fun Modifier.screenColumn(max: Dp = ReadableWidth): Modifier =
    fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = max).fillMaxWidth()

/** The widest a phone is in dp, as Android's own window size classes have it (compact < 600). */
const val PhoneMaxWidthDp = 600

/**
 * The desk's columns: two on a phone, as always; on a wider screen as many as fit at a phone card's
 * size or more (three on a tablet upright, four turned), where two were stretched across it.
 */
@Composable
fun deskColumns(): StaggeredGridCells =
    if (LocalConfiguration.current.screenWidthDp < PhoneMaxWidthDp) StaggeredGridCells.Fixed(2) else StaggeredGridCells.Adaptive(260.dp)
