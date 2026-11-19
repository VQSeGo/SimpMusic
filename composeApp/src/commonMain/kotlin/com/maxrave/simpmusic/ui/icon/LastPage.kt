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
val SimpIcons.LastPage: ImageVector
  get() {
    if (_LastPage != null) {
      return _LastPage!!
    }
    _LastPage =
      ImageVector.Builder(
          name = "LastPage",
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
            moveTo(10.2f, 12f)
            lineTo(6.3f, 8.1f)
            quadTo(6.03f, 7.82f, 6.03f, 7.4f)
            reflectiveQuadTo(6.3f, 6.7f)
            reflectiveQuadTo(7f, 6.43f)
            reflectiveQuadTo(7.7f, 6.7f)
            lineToRelative(4.6f, 4.6f)
            quadToRelative(0.15f, 0.15f, 0.21f, 0.33f)
            reflectiveQuadTo(12.58f, 12f)
            reflectiveQuadToRelative(-0.06f, 0.38f)
            reflectiveQuadTo(12.3f, 12.7f)
            lineTo(7.7f, 17.3f)
            quadTo(7.43f, 17.58f, 7f, 17.58f)
            quadToRelative(-0.42f, 0f, -0.7f, -0.27f)
            quadTo(6.03f, 17.02f, 6.03f, 16.6f)
            reflectiveQuadTo(6.3f, 15.9f)
            lineTo(10.2f, 12f)
            close()
            moveTo(17.71f, 6.29f)
            quadTo(18f, 6.57f, 18f, 7f)
            verticalLineTo(17f)
            quadToRelative(0f, 0.43f, -0.29f, 0.71f)
            reflectiveQuadTo(17f, 18f)
            reflectiveQuadTo(16.29f, 17.71f)
            quadTo(16f, 17.43f, 16f, 17f)
            verticalLineTo(7f)
            quadTo(16f, 6.57f, 16.29f, 6.29f)
            reflectiveQuadTo(17f, 6f)
            reflectiveQuadToRelative(0.71f, 0.29f)
            close()
          }
        }
        .buildSimpIcon()
    return _LastPage!!
  }

private var _LastPage: ImageVector? = null
