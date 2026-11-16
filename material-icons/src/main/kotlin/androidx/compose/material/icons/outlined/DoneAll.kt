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

public val Icons.Outlined.DoneAll: ImageVector
    get() {
        if (_doneAll != null) {
            return _doneAll!!
        }
        _doneAll = materialIcon(name = "Outlined.DoneAll") {
            materialPath {
                moveTo(18.0f, 7.0f)
                lineToRelative(-1.41f, -1.41f)
                lineToRelative(-6.34f, 6.34f)
                lineToRelative(1.41f, 1.41f)
                lineTo(18.0f, 7.0f)
                close()
                moveTo(22.24f, 5.59f)
                lineTo(11.66f, 16.17f)
                lineTo(7.48f, 12.0f)
                lineToRelative(-1.41f, 1.41f)
                lineTo(11.66f, 19.0f)
                lineToRelative(12.0f, -12.0f)
                lineToRelative(-1.42f, -1.41f)
                close()
                moveTo(0.41f, 13.41f)
                lineTo(6.0f, 19.0f)
                lineToRelative(1.41f, -1.41f)
                lineTo(1.83f, 12.0f)
                lineTo(0.41f, 13.41f)
                close()
            }
        }
        return _doneAll!!
    }

private var _doneAll: ImageVector? = null
