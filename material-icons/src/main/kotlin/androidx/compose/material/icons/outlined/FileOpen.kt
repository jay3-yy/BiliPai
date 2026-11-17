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

public val Icons.Outlined.FileOpen: ImageVector
    get() {
        if (_fileOpen != null) {
            return _fileOpen!!
        }
        _fileOpen = materialIcon(name = "Outlined.FileOpen") {
            materialPath {
                moveTo(15.0f, 22.0f)
                horizontalLineTo(6.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, -0.9f, -2.0f, -2.0f)
                verticalLineTo(4.0f)
                curveToRelative(0.0f, -1.1f, 0.9f, -2.0f, 2.0f, -2.0f)
                horizontalLineToRelative(8.0f)
                lineToRelative(6.0f, 6.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineTo(9.0f)
                horizontalLineToRelative(-5.0f)
                verticalLineTo(4.0f)
                horizontalLineTo(6.0f)
                verticalLineToRelative(16.0f)
                horizontalLineToRelative(9.0f)
                verticalLineTo(22.0f)
                close()
                moveTo(19.0f, 21.66f)
                lineToRelative(0.0f, -2.24f)
                lineToRelative(2.95f, 2.95f)
                lineToRelative(1.41f, -1.41f)
                lineTo(20.41f, 18.0f)
                horizontalLineToRelative(2.24f)
                verticalLineToRelative(-2.0f)
                horizontalLineTo(17.0f)
                verticalLineToRelative(5.66f)
                horizontalLineTo(19.0f)
                close()
            }
        }
        return _fileOpen!!
    }

private var _fileOpen: ImageVector? = null
