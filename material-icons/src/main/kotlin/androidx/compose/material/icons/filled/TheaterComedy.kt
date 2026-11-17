/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.material.icons.filled

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

public val Icons.Filled.TheaterComedy: ImageVector
    get() {
        if (_theaterComedy != null) {
            return _theaterComedy!!
        }
        _theaterComedy = materialIcon(name = "Filled.TheaterComedy") {
            materialPath {
                moveTo(2.0f, 16.5f)
                curveTo(2.0f, 19.54f, 4.46f, 22.0f, 7.5f, 22.0f)
                reflectiveCurveToRelative(5.5f, -2.46f, 5.5f, -5.5f)
                verticalLineTo(10.0f)
                horizontalLineTo(2.0f)
                verticalLineTo(16.5f)
                close()
                moveTo(7.5f, 18.5f)
                curveTo(6.12f, 18.5f, 5.0f, 17.83f, 5.0f, 17.0f)
                horizontalLineToRelative(5.0f)
                curveTo(10.0f, 17.83f, 8.88f, 18.5f, 7.5f, 18.5f)
                close()
                moveTo(10.0f, 13.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                reflectiveCurveToRelative(-1.0f, -0.45f, -1.0f, -1.0f)
                curveTo(9.0f, 13.45f, 9.45f, 13.0f, 10.0f, 13.0f)
                close()
                moveTo(5.0f, 13.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                reflectiveCurveToRelative(-1.0f, -0.45f, -1.0f, -1.0f)
                curveTo(4.0f, 13.45f, 4.45f, 13.0f, 5.0f, 13.0f)
                close()
            }
            materialPath {
                moveTo(11.0f, 3.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(3.0f)
                verticalLineToRelative(2.5f)
                curveToRelative(0.0f, -0.83f, 1.12f, -1.5f, 2.5f, -1.5f)
                curveToRelative(1.38f, 0.0f, 2.5f, 0.67f, 2.5f, 1.5f)
                horizontalLineToRelative(-5.0f)
                verticalLineTo(14.0f)
                verticalLineToRelative(0.39f)
                curveToRelative(0.75f, 0.38f, 1.6f, 0.61f, 2.5f, 0.61f)
                curveToRelative(3.04f, 0.0f, 5.5f, -2.46f, 5.5f, -5.5f)
                verticalLineTo(3.0f)
                horizontalLineTo(11.0f)
                close()
                moveTo(14.0f, 8.08f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                reflectiveCurveToRelative(1.0f, 0.45f, 1.0f, 1.0f)
                curveTo(15.0f, 7.64f, 14.55f, 8.08f, 14.0f, 8.08f)
                close()
                moveTo(19.0f, 8.08f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                reflectiveCurveToRelative(1.0f, 0.45f, 1.0f, 1.0f)
                curveTo(20.0f, 7.64f, 19.55f, 8.08f, 19.0f, 8.08f)
                close()
            }
        }
        return _theaterComedy!!
    }

private var _theaterComedy: ImageVector? = null
