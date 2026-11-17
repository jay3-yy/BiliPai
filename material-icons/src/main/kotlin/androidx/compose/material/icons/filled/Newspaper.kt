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

public val Icons.Filled.Newspaper: ImageVector
    get() {
        if (_newspaper != null) {
            return _newspaper!!
        }
        _newspaper = materialIcon(name = "Filled.Newspaper") {
            materialPath {
                moveTo(22.0f, 3.0f)
                lineToRelative(-1.67f, 1.67f)
                lineTo(18.67f, 3.0f)
                lineTo(17.0f, 4.67f)
                lineTo(15.33f, 3.0f)
                lineToRelative(-1.66f, 1.67f)
                lineTo(12.0f, 3.0f)
                lineToRelative(-1.67f, 1.67f)
                lineTo(8.67f, 3.0f)
                lineTo(7.0f, 4.67f)
                lineTo(5.33f, 3.0f)
                lineTo(3.67f, 4.67f)
                lineTo(2.0f, 3.0f)
                verticalLineToRelative(16.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                lineToRelative(16.0f, 0.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(3.0f)
                close()
                moveTo(11.0f, 19.0f)
                horizontalLineTo(4.0f)
                verticalLineToRelative(-6.0f)
                horizontalLineToRelative(7.0f)
                verticalLineTo(19.0f)
                close()
                moveTo(20.0f, 19.0f)
                horizontalLineToRelative(-7.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(7.0f)
                verticalLineTo(19.0f)
                close()
                moveTo(20.0f, 15.0f)
                horizontalLineToRelative(-7.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(7.0f)
                verticalLineTo(15.0f)
                close()
                moveTo(20.0f, 11.0f)
                horizontalLineTo(4.0f)
                verticalLineTo(8.0f)
                horizontalLineToRelative(16.0f)
                verticalLineTo(11.0f)
                close()
            }
        }
        return _newspaper!!
    }

private var _newspaper: ImageVector? = null
