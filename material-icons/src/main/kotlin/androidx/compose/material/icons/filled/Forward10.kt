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

public val Icons.Filled.Forward10: ImageVector
    get() {
        if (_forward10 != null) {
            return _forward10!!
        }
        _forward10 = materialIcon(name = "Filled.Forward10") {
            materialPath {
                moveTo(18.0f, 13.0f)
                curveToRelative(0.0f, 3.31f, -2.69f, 6.0f, -6.0f, 6.0f)
                reflectiveCurveToRelative(-6.0f, -2.69f, -6.0f, -6.0f)
                reflectiveCurveToRelative(2.69f, -6.0f, 6.0f, -6.0f)
                verticalLineToRelative(4.0f)
                lineToRelative(5.0f, -5.0f)
                lineToRelative(-5.0f, -5.0f)
                verticalLineToRelative(4.0f)
                curveToRelative(-4.42f, 0.0f, -8.0f, 3.58f, -8.0f, 8.0f)
                curveToRelative(0.0f, 4.42f, 3.58f, 8.0f, 8.0f, 8.0f)
                reflectiveCurveToRelative(8.0f, -3.58f, 8.0f, -8.0f)
                horizontalLineTo(18.0f)
                close()
            }
            materialPath {
                moveTo(10.86f, 15.94f)
                lineToRelative(0.0f, -4.27f)
                lineToRelative(-0.09f, 0.0f)
                lineToRelative(-1.77f, 0.63f)
                lineToRelative(0.0f, 0.69f)
                lineToRelative(1.01f, -0.31f)
                lineToRelative(0.0f, 3.26f)
                close()
            }
            materialPath {
                moveTo(12.25f, 13.44f)
                verticalLineToRelative(0.74f)
                curveToRelative(0.0f, 1.9f, 1.31f, 1.82f, 1.44f, 1.82f)
                curveToRelative(0.14f, 0.0f, 1.44f, 0.09f, 1.44f, -1.82f)
                verticalLineToRelative(-0.74f)
                curveToRelative(0.0f, -1.9f, -1.31f, -1.82f, -1.44f, -1.82f)
                curveTo(13.55f, 11.62f, 12.25f, 11.53f, 12.25f, 13.44f)
                close()
                moveTo(14.29f, 13.32f)
                verticalLineToRelative(0.97f)
                curveToRelative(0.0f, 0.77f, -0.21f, 1.03f, -0.59f, 1.03f)
                curveToRelative(-0.38f, 0.0f, -0.6f, -0.26f, -0.6f, -1.03f)
                verticalLineToRelative(-0.97f)
                curveToRelative(0.0f, -0.75f, 0.22f, -1.01f, 0.59f, -1.01f)
                curveTo(14.07f, 12.3f, 14.29f, 12.57f, 14.29f, 13.32f)
                close()
            }
        }
        return _forward10!!
    }

private var _forward10: ImageVector? = null
