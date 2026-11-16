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

public val Icons.Outlined.Cast: ImageVector
    get() {
        if (_cast != null) {
            return _cast!!
        }
        _cast = materialIcon(name = "Outlined.Cast") {
            materialPath {
                moveTo(21.0f, 3.0f)
                lineTo(3.0f, 3.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(3.0f)
                horizontalLineToRelative(2.0f)
                lineTo(3.0f, 5.0f)
                horizontalLineToRelative(18.0f)
                verticalLineToRelative(14.0f)
                horizontalLineToRelative(-7.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(7.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(23.0f, 5.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(1.0f, 18.0f)
                verticalLineToRelative(3.0f)
                horizontalLineToRelative(3.0f)
                curveToRelative(0.0f, -1.66f, -1.34f, -3.0f, -3.0f, -3.0f)
                close()
                moveTo(1.0f, 14.0f)
                verticalLineToRelative(2.0f)
                curveToRelative(2.76f, 0.0f, 5.0f, 2.24f, 5.0f, 5.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(0.0f, -3.87f, -3.13f, -7.0f, -7.0f, -7.0f)
                close()
                moveTo(1.0f, 10.0f)
                verticalLineToRelative(2.0f)
                curveToRelative(4.97f, 0.0f, 9.0f, 4.03f, 9.0f, 9.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(0.0f, -6.08f, -4.93f, -11.0f, -11.0f, -11.0f)
                close()
            }
        }
        return _cast!!
    }

private var _cast: ImageVector? = null
