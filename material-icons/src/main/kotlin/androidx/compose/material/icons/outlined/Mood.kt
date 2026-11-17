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

package androidx.compose.material.icons.outlined

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

public val Icons.Outlined.Mood: ImageVector
    get() {
        if (_mood != null) {
            return _mood!!
        }
        _mood = materialIcon(name = "Outlined.Mood") {
            materialPath {
                moveTo(11.99f, 2.0f)
                curveTo(6.47f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                reflectiveCurveToRelative(4.47f, 10.0f, 9.99f, 10.0f)
                curveTo(17.52f, 22.0f, 22.0f, 17.52f, 22.0f, 12.0f)
                reflectiveCurveTo(17.52f, 2.0f, 11.99f, 2.0f)
                close()
                moveTo(12.0f, 20.0f)
                curveToRelative(-4.42f, 0.0f, -8.0f, -3.58f, -8.0f, -8.0f)
                reflectiveCurveToRelative(3.58f, -8.0f, 8.0f, -8.0f)
                reflectiveCurveToRelative(8.0f, 3.58f, 8.0f, 8.0f)
                reflectiveCurveToRelative(-3.58f, 8.0f, -8.0f, 8.0f)
                close()
                moveTo(15.5f, 11.0f)
                curveToRelative(0.83f, 0.0f, 1.5f, -0.67f, 1.5f, -1.5f)
                reflectiveCurveTo(16.33f, 8.0f, 15.5f, 8.0f)
                reflectiveCurveTo(14.0f, 8.67f, 14.0f, 9.5f)
                reflectiveCurveToRelative(0.67f, 1.5f, 1.5f, 1.5f)
                close()
                moveTo(8.5f, 11.0f)
                curveToRelative(0.83f, 0.0f, 1.5f, -0.67f, 1.5f, -1.5f)
                reflectiveCurveTo(9.33f, 8.0f, 8.5f, 8.0f)
                reflectiveCurveTo(7.0f, 8.67f, 7.0f, 9.5f)
                reflectiveCurveTo(7.67f, 11.0f, 8.5f, 11.0f)
                close()
                moveTo(12.0f, 17.5f)
                curveToRelative(2.33f, 0.0f, 4.31f, -1.46f, 5.11f, -3.5f)
                lineTo(6.89f, 14.0f)
                curveToRelative(0.8f, 2.04f, 2.78f, 3.5f, 5.11f, 3.5f)
                close()
            }
        }
        return _mood!!
    }

private var _mood: ImageVector? = null
