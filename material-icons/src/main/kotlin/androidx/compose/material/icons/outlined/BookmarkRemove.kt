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

public val Icons.Outlined.BookmarkRemove: ImageVector
    get() {
        if (_bookmarkRemove != null) {
            return _bookmarkRemove!!
        }
        _bookmarkRemove = materialIcon(name = "Outlined.BookmarkRemove") {
            materialPath {
                moveTo(17.0f, 11.0f)
                verticalLineToRelative(6.97f)
                lineToRelative(-5.0f, -2.14f)
                lineToRelative(-5.0f, 2.14f)
                verticalLineTo(5.0f)
                horizontalLineToRelative(6.0f)
                verticalLineTo(3.0f)
                horizontalLineTo(7.0f)
                curveTo(5.9f, 3.0f, 5.0f, 3.9f, 5.0f, 5.0f)
                verticalLineToRelative(16.0f)
                lineToRelative(7.0f, -3.0f)
                lineToRelative(7.0f, 3.0f)
                verticalLineTo(11.0f)
                horizontalLineTo(17.0f)
                close()
                moveTo(21.0f, 7.0f)
                horizontalLineToRelative(-6.0f)
                verticalLineTo(5.0f)
                horizontalLineToRelative(6.0f)
                verticalLineTo(7.0f)
                close()
            }
        }
        return _bookmarkRemove!!
    }

private var _bookmarkRemove: ImageVector? = null
