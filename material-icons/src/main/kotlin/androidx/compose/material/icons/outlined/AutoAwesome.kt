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

public val Icons.Outlined.AutoAwesome: ImageVector
    get() {
        if (_autoAwesome != null) {
            return _autoAwesome!!
        }
        _autoAwesome = materialIcon(name = "Outlined.AutoAwesome") {
            materialPath {
                moveTo(19.0f, 9.0f)
                lineToRelative(1.25f, -2.75f)
                lineToRelative(2.75f, -1.25f)
                lineToRelative(-2.75f, -1.25f)
                lineToRelative(-1.25f, -2.75f)
                lineToRelative(-1.25f, 2.75f)
                lineToRelative(-2.75f, 1.25f)
                lineToRelative(2.75f, 1.25f)
                close()
            }
            materialPath {
                moveTo(19.0f, 15.0f)
                lineToRelative(-1.25f, 2.75f)
                lineToRelative(-2.75f, 1.25f)
                lineToRelative(2.75f, 1.25f)
                lineToRelative(1.25f, 2.75f)
                lineToRelative(1.25f, -2.75f)
                lineToRelative(2.75f, -1.25f)
                lineToRelative(-2.75f, -1.25f)
                close()
            }
            materialPath {
                moveTo(11.5f, 9.5f)
                lineTo(9.0f, 4.0f)
                lineTo(6.5f, 9.5f)
                lineTo(1.0f, 12.0f)
                lineToRelative(5.5f, 2.5f)
                lineTo(9.0f, 20.0f)
                lineToRelative(2.5f, -5.5f)
                lineTo(17.0f, 12.0f)
                lineTo(11.5f, 9.5f)
                close()
                moveTo(9.99f, 12.99f)
                lineTo(9.0f, 15.17f)
                lineToRelative(-0.99f, -2.18f)
                lineTo(5.83f, 12.0f)
                lineToRelative(2.18f, -0.99f)
                lineTo(9.0f, 8.83f)
                lineToRelative(0.99f, 2.18f)
                lineTo(12.17f, 12.0f)
                lineTo(9.99f, 12.99f)
                close()
            }
        }
        return _autoAwesome!!
    }

private var _autoAwesome: ImageVector? = null
