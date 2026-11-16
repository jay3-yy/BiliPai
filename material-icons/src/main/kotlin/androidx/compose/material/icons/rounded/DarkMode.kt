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

package androidx.compose.material.icons.rounded

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

public val Icons.Rounded.DarkMode: ImageVector
    get() {
        if (_darkMode != null) {
            return _darkMode!!
        }
        _darkMode = materialIcon(name = "Rounded.DarkMode") {
            materialPath {
                moveTo(11.01f, 3.05f)
                curveTo(6.51f, 3.54f, 3.0f, 7.36f, 3.0f, 12.0f)
                curveToRelative(0.0f, 4.97f, 4.03f, 9.0f, 9.0f, 9.0f)
                curveToRelative(4.63f, 0.0f, 8.45f, -3.5f, 8.95f, -8.0f)
                curveToRelative(0.09f, -0.79f, -0.78f, -1.42f, -1.54f, -0.95f)
                curveToRelative(-0.84f, 0.54f, -1.84f, 0.85f, -2.91f, 0.85f)
                curveToRelative(-2.98f, 0.0f, -5.4f, -2.42f, -5.4f, -5.4f)
                curveToRelative(0.0f, -1.06f, 0.31f, -2.06f, 0.84f, -2.89f)
                curveTo(12.39f, 3.94f, 11.9f, 2.98f, 11.01f, 3.05f)
                close()
            }
        }
        return _darkMode!!
    }

private var _darkMode: ImageVector? = null
