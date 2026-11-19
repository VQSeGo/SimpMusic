package com.maxrave.simpmusic.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
val SimpIcons.FirstPage: ImageVector
  get() {
    if (_FirstPage != null) {
      return _FirstPage!!
    }
    _FirstPage =
      ImageVector.Builder(
          name = "FirstPage",
          defaultWidth = 24.dp,
          defaultHeight = 24.dp,
          viewportWidth = 24f,
          viewportHeight = 24f,
        )
        .apply {
          path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Bevel,
            strokeLineMiter = 1f,
            pathFillType = PathFillType.Companion.NonZero,
          ) {
            moveTo(6.29f, 17.71f)
            quadTo(6f, 17.43f, 6f, 17f)
            verticalLineTo(7f)
            quadTo(6f, 6.57f, 6.29f, 6.29f)
            reflectiveQuadTo(7f, 6f)
            reflectiveQuadTo(7.71f, 6.29f)
            reflectiveQuadTo(8f, 7f)
            verticalLineTo(17f)
            quadToRelative(0f, 0.43f, -0.29f, 0.71f)
            reflectiveQuadTo(7f, 18f)
            quadTo(6.58f, 18f, 6.29f, 17.71f)
            close()
            moveTo(13.8f, 12f)
            lineToRelative(3.9f, 3.9f)
            quadToRelative(0.28f, 0.28f, 0.28f, 0.7f)
            quadToRelative(0f, 0.42f, -0.28f, 0.7f)
            quadTo(17.43f, 17.58f, 17f, 17.58f)
            reflectiveQuadTo(16.3f, 17.3f)
            lineTo(11.7f, 12.7f)
            quadTo(11.55f, 12.55f, 11.49f, 12.38f)
            reflectiveQuadTo(11.43f, 12f)
            reflectiveQuadToRelative(0.06f, -0.38f)
            reflectiveQuadTo(11.7f, 11.3f)
            lineTo(16.3f, 6.7f)
            quadTo(16.58f, 6.43f, 17f, 6.43f)
            reflectiveQuadTo(17.7f, 6.7f)
            quadToRelative(0.28f, 0.27f, 0.28f, 0.7f)
            reflectiveQuadTo(17.7f, 8.1f)
            lineTo(13.8f, 12f)
            close()
          }
        }
        .buildSimpIcon()
    return _FirstPage!!
  }

private var _FirstPage: ImageVector? = null
