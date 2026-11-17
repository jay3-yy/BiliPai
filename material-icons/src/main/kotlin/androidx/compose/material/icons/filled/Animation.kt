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

public val Icons.Filled.Animation: ImageVector
    get() {
        if (_animation != null) {
            return _animation!!
        }
        _animation = materialIcon(name = "Filled.Animation") {
            materialPath {
                moveTo(15.0f, 2.0f)
                curveToRelative(-2.71f, 0.0f, -5.05f, 1.54f, -6.22f, 3.78f)
                curveToRelative(-1.28f, 0.67f, -2.34f, 1.72f, -3.0f, 3.0f)
                curveTo(3.54f, 9.95f, 2.0f, 12.29f, 2.0f, 15.0f)
                curveToRelative(0.0f, 3.87f, 3.13f, 7.0f, 7.0f, 7.0f)
                curveToRelative(2.71f, 0.0f, 5.05f, -1.54f, 6.22f, -3.78f)
                curveToRelative(1.28f, -0.67f, 2.34f, -1.72f, 3.0f, -3.0f)
                curveTo(20.46f, 14.05f, 22.0f, 11.71f, 22.0f, 9.0f)
                curveToRelative(0.0f, -3.87f, -3.13f, -7.0f, -7.0f, -7.0f)
                close()
                moveTo(9.0f, 20.0f)
                curveToRelative(-2.76f, 0.0f, -5.0f, -2.24f, -5.0f, -5.0f)
                curveToRelative(0.0f, -1.12f, 0.37f, -2.16f, 1.0f, -3.0f)
                curveToRelative(0.0f, 3.87f, 3.13f, 7.0f, 7.0f, 7.0f)
                curveToRelative(-0.84f, 0.63f, -1.88f, 1.0f, -3.0f, 1.0f)
                close()
                moveTo(12.0f, 17.0f)
                curveToRelative(-2.76f, 0.0f, -5.0f, -2.24f, -5.0f, -5.0f)
                curveToRelative(0.0f, -1.12f, 0.37f, -2.16f, 1.0f, -3.0f)
                curveToRelative(0.0f, 3.86f, 3.13f, 6.99f, 7.0f, 7.0f)
                curveToRelative(-0.84f, 0.63f, -1.88f, 1.0f, -3.0f, 1.0f)
                close()
                moveTo(16.7f, 13.7f)
                curveToRelative(-0.53f, 0.19f, -1.1f, 0.3f, -1.7f, 0.3f)
                curveToRelative(-2.76f, 0.0f, -5.0f, -2.24f, -5.0f, -5.0f)
                curveToRelative(0.0f, -0.6f, 0.11f, -1.17f, 0.3f, -1.7f)
                curveToRelative(0.53f, -0.19f, 1.1f, -0.3f, 1.7f, -0.3f)
                curveToRelative(2.76f, 0.0f, 5.0f, 2.24f, 5.0f, 5.0f)
                curveToRelative(0.0f, 0.6f, -0.11f, 1.17f, -0.3f, 1.7f)
                close()
                moveTo(19.0f, 12.0f)
                curveToRelative(0.0f, -3.86f, -3.13f, -6.99f, -7.0f, -7.0f)
                curveToRelative(0.84f, -0.63f, 1.87f, -1.0f, 3.0f, -1.0f)
                curveToRelative(2.76f, 0.0f, 5.0f, 2.24f, 5.0f, 5.0f)
                curveToRelative(0.0f, 1.12f, -0.37f, 2.16f, -1.0f, 3.0f)
                close()
            }
        }
        return _animation!!
    }

private var _animation: ImageVector? = null
