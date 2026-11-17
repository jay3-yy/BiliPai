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

public val Icons.Outlined.LiveTv: ImageVector
    get() {
        if (_liveTv != null) {
            return _liveTv!!
        }
        _liveTv = materialIcon(name = "Outlined.LiveTv") {
            materialPath {
                moveTo(9.0f, 10.0f)
                verticalLineToRelative(8.0f)
                lineToRelative(7.0f, -4.0f)
                close()
                moveTo(21.0f, 6.0f)
                horizontalLineToRelative(-7.58f)
                lineToRelative(3.29f, -3.29f)
                lineTo(16.0f, 2.0f)
                lineToRelative(-4.0f, 4.0f)
                horizontalLineToRelative(-0.03f)
                lineToRelative(-4.0f, -4.0f)
                lineToRelative(-0.69f, 0.71f)
                lineTo(10.56f, 6.0f)
                lineTo(3.0f, 6.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(12.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(18.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(23.0f, 8.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(21.0f, 20.0f)
                lineTo(3.0f, 20.0f)
                lineTo(3.0f, 8.0f)
                horizontalLineToRelative(18.0f)
                verticalLineToRelative(12.0f)
                close()
            }
        }
        return _liveTv!!
    }

private var _liveTv: ImageVector? = null
