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

package androidx.compose.material.icons.automirrored.filled

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

public val Icons.AutoMirrored.Filled.ArrowForwardIos: ImageVector
    get() {
        if (_arrowForwardIos != null) {
            return _arrowForwardIos!!
        }
        _arrowForwardIos = materialIcon(name = "AutoMirrored.Filled.ArrowForwardIos", autoMirror =
                true) {
            materialPath {
                moveTo(6.23f, 20.23f)
                lineToRelative(1.77f, 1.77f)
                lineToRelative(10.0f, -10.0f)
                lineToRelative(-10.0f, -10.0f)
                lineToRelative(-1.77f, 1.77f)
                lineToRelative(8.23f, 8.23f)
                close()
            }
        }
        return _arrowForwardIos!!
    }

private var _arrowForwardIos: ImageVector? = null
