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

public val Icons.Outlined.HourglassTop: ImageVector
    get() {
        if (_hourglassTop != null) {
            return _hourglassTop!!
        }
        _hourglassTop = materialIcon(name = "Outlined.HourglassTop") {
            materialPath {
                moveTo(6.0f, 2.0f)
                lineToRelative(0.01f, 6.0f)
                lineTo(10.0f, 12.0f)
                lineToRelative(-3.99f, 4.01f)
                lineTo(6.0f, 22.0f)
                horizontalLineToRelative(12.0f)
                verticalLineToRelative(-6.0f)
                lineToRelative(-4.0f, -4.0f)
                lineToRelative(4.0f, -3.99f)
                verticalLineTo(2.0f)
                horizontalLineTo(6.0f)
                close()
                moveTo(16.0f, 16.5f)
                verticalLineTo(20.0f)
                horizontalLineTo(8.0f)
                verticalLineToRelative(-3.5f)
                lineToRelative(4.0f, -4.0f)
                lineTo(16.0f, 16.5f)
                close()
            }
        }
        return _hourglassTop!!
    }

private var _hourglassTop: ImageVector? = null
