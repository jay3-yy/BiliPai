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

public val Icons.Filled.Gamepad: ImageVector
    get() {
        if (_gamepad != null) {
            return _gamepad!!
        }
        _gamepad = materialIcon(name = "Filled.Gamepad") {
            materialPath {
                moveTo(15.0f, 7.5f)
                verticalLineTo(2.0f)
                horizontalLineTo(9.0f)
                verticalLineToRelative(5.5f)
                lineToRelative(3.0f, 3.0f)
                lineToRelative(3.0f, -3.0f)
                close()
                moveTo(7.5f, 9.0f)
                horizontalLineTo(2.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(5.5f)
                lineToRelative(3.0f, -3.0f)
                lineToRelative(-3.0f, -3.0f)
                close()
                moveTo(9.0f, 16.5f)
                verticalLineTo(22.0f)
                horizontalLineToRelative(6.0f)
                verticalLineToRelative(-5.5f)
                lineToRelative(-3.0f, -3.0f)
                lineToRelative(-3.0f, 3.0f)
                close()
                moveTo(16.5f, 9.0f)
                lineToRelative(-3.0f, 3.0f)
                lineToRelative(3.0f, 3.0f)
                horizontalLineTo(22.0f)
                verticalLineTo(9.0f)
                horizontalLineToRelative(-5.5f)
                close()
            }
        }
        return _gamepad!!
    }

private var _gamepad: ImageVector? = null
