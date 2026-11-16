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

public val Icons.Outlined.FolderCopy: ImageVector
    get() {
        if (_folderCopy != null) {
            return _folderCopy!!
        }
        _folderCopy = materialIcon(name = "Outlined.FolderCopy") {
            materialPath {
                moveTo(3.0f, 19.0f)
                horizontalLineToRelative(17.0f)
                verticalLineToRelative(2.0f)
                horizontalLineTo(3.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, -0.9f, -2.0f, -2.0f)
                verticalLineTo(6.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(19.0f)
                close()
                moveTo(23.0f, 6.0f)
                verticalLineToRelative(9.0f)
                curveToRelative(0.0f, 1.1f, -0.9f, 2.0f, -2.0f, 2.0f)
                horizontalLineTo(7.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, -0.9f, -2.0f, -2.0f)
                lineTo(5.01f, 4.0f)
                curveTo(5.01f, 2.9f, 5.9f, 2.0f, 7.0f, 2.0f)
                horizontalLineToRelative(5.0f)
                lineToRelative(2.0f, 2.0f)
                horizontalLineToRelative(7.0f)
                curveTo(22.1f, 4.0f, 23.0f, 4.9f, 23.0f, 6.0f)
                close()
                moveTo(7.0f, 15.0f)
                horizontalLineToRelative(14.0f)
                verticalLineTo(6.0f)
                horizontalLineToRelative(-7.83f)
                lineToRelative(-2.0f, -2.0f)
                horizontalLineTo(7.0f)
                verticalLineTo(15.0f)
                close()
            }
        }
        return _folderCopy!!
    }

private var _folderCopy: ImageVector? = null
