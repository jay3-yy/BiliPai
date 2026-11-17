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

public val Icons.Outlined.TextFormat: ImageVector
    get() {
        if (_textFormat != null) {
            return _textFormat!!
        }
        _textFormat = materialIcon(name = "Outlined.TextFormat") {
            materialPath {
                moveTo(5.0f, 17.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(14.0f)
                verticalLineToRelative(-2.0f)
                lineTo(5.0f, 17.0f)
                close()
                moveTo(9.5f, 12.8f)
                horizontalLineToRelative(5.0f)
                lineToRelative(0.9f, 2.2f)
                horizontalLineToRelative(2.1f)
                lineTo(12.75f, 4.0f)
                horizontalLineToRelative(-1.5f)
                lineTo(6.5f, 15.0f)
                horizontalLineToRelative(2.1f)
                lineToRelative(0.9f, -2.2f)
                close()
                moveTo(12.0f, 5.98f)
                lineTo(13.87f, 11.0f)
                horizontalLineToRelative(-3.74f)
                lineTo(12.0f, 5.98f)
                close()
            }
        }
        return _textFormat!!
    }

private var _textFormat: ImageVector? = null
