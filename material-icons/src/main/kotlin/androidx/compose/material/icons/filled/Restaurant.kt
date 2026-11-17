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

public val Icons.Filled.Restaurant: ImageVector
    get() {
        if (_restaurant != null) {
            return _restaurant!!
        }
        _restaurant = materialIcon(name = "Filled.Restaurant") {
            materialPath {
                moveTo(11.0f, 9.0f)
                lineTo(9.0f, 9.0f)
                lineTo(9.0f, 2.0f)
                lineTo(7.0f, 2.0f)
                verticalLineToRelative(7.0f)
                lineTo(5.0f, 9.0f)
                lineTo(5.0f, 2.0f)
                lineTo(3.0f, 2.0f)
                verticalLineToRelative(7.0f)
                curveToRelative(0.0f, 2.12f, 1.66f, 3.84f, 3.75f, 3.97f)
                lineTo(6.75f, 22.0f)
                horizontalLineToRelative(2.5f)
                verticalLineToRelative(-9.03f)
                curveTo(11.34f, 12.84f, 13.0f, 11.12f, 13.0f, 9.0f)
                lineTo(13.0f, 2.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(7.0f)
                close()
                moveTo(16.0f, 6.0f)
                verticalLineToRelative(8.0f)
                horizontalLineToRelative(2.5f)
                verticalLineToRelative(8.0f)
                lineTo(21.0f, 22.0f)
                lineTo(21.0f, 2.0f)
                curveToRelative(-2.76f, 0.0f, -5.0f, 2.24f, -5.0f, 4.0f)
                close()
            }
        }
        return _restaurant!!
    }

private var _restaurant: ImageVector? = null
