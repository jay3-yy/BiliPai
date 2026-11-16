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

public val Icons.Outlined.VerticalAlignBottom: ImageVector
    get() {
        if (_verticalAlignBottom != null) {
            return _verticalAlignBottom!!
        }
        _verticalAlignBottom = materialIcon(name = "Outlined.VerticalAlignBottom") {
            materialPath {
                moveTo(16.0f, 13.0f)
                horizontalLineToRelative(-3.0f)
                verticalLineTo(3.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(10.0f)
                horizontalLineTo(8.0f)
                lineToRelative(4.0f, 4.0f)
                lineToRelative(4.0f, -4.0f)
                close()
                moveTo(4.0f, 19.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(16.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineTo(4.0f)
                close()
            }
        }
        return _verticalAlignBottom!!
    }

private var _verticalAlignBottom: ImageVector? = null
