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

public val Icons.Outlined.Stars: ImageVector
    get() {
        if (_stars != null) {
            return _stars!!
        }
        _stars = materialIcon(name = "Outlined.Stars") {
            materialPath {
                moveTo(11.99f, 2.0f)
                curveTo(6.47f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                reflectiveCurveToRelative(4.47f, 10.0f, 9.99f, 10.0f)
                curveTo(17.52f, 22.0f, 22.0f, 17.52f, 22.0f, 12.0f)
                reflectiveCurveTo(17.52f, 2.0f, 11.99f, 2.0f)
                close()
                moveTo(19.47f, 9.16f)
                lineToRelative(-5.01f, -0.43f)
                lineToRelative(-2.0f, -4.71f)
                curveToRelative(3.21f, 0.19f, 5.91f, 2.27f, 7.01f, 5.14f)
                close()
                moveTo(14.4f, 15.42f)
                lineTo(12.0f, 13.98f)
                lineToRelative(-2.39f, 1.44f)
                lineToRelative(0.63f, -2.72f)
                lineToRelative(-2.11f, -1.83f)
                lineToRelative(2.78f, -0.24f)
                lineTo(12.0f, 8.06f)
                lineToRelative(1.09f, 2.56f)
                lineToRelative(2.78f, 0.24f)
                lineToRelative(-2.11f, 1.83f)
                lineToRelative(0.64f, 2.73f)
                close()
                moveTo(11.54f, 4.02f)
                lineToRelative(-2.0f, 4.72f)
                lineToRelative(-5.02f, 0.43f)
                curveToRelative(1.1f, -2.88f, 3.8f, -4.97f, 7.02f, -5.15f)
                close()
                moveTo(4.0f, 12.0f)
                curveToRelative(0.0f, -0.64f, 0.08f, -1.26f, 0.23f, -1.86f)
                lineToRelative(3.79f, 3.28f)
                lineToRelative(-1.11f, 4.75f)
                curveTo(5.13f, 16.7f, 4.0f, 14.48f, 4.0f, 12.0f)
                close()
                moveTo(7.84f, 18.82f)
                lineTo(12.0f, 16.31f)
                lineToRelative(4.16f, 2.5f)
                curveToRelative(-1.22f, 0.75f, -2.64f, 1.19f, -4.17f, 1.19f)
                curveToRelative(-1.52f, 0.0f, -2.94f, -0.44f, -4.15f, -1.18f)
                close()
                moveTo(17.09f, 18.17f)
                lineToRelative(-1.11f, -4.75f)
                lineToRelative(3.79f, -3.28f)
                curveToRelative(0.14f, 0.59f, 0.23f, 1.22f, 0.23f, 1.86f)
                curveToRelative(0.0f, 2.48f, -1.14f, 4.7f, -2.91f, 6.17f)
                close()
            }
        }
        return _stars!!
    }

private var _stars: ImageVector? = null
