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

public val Icons.Outlined.IosShare: ImageVector
    get() {
        if (_iosShare != null) {
            return _iosShare!!
        }
        _iosShare = materialIcon(name = "Outlined.IosShare") {
            materialPath {
                moveTo(16.0f, 5.0f)
                lineToRelative(-1.42f, 1.42f)
                lineToRelative(-1.59f, -1.59f)
                verticalLineTo(16.0f)
                horizontalLineToRelative(-1.98f)
                verticalLineTo(4.83f)
                lineTo(9.42f, 6.42f)
                lineTo(8.0f, 5.0f)
                lineToRelative(4.0f, -4.0f)
                lineTo(16.0f, 5.0f)
                close()
                moveTo(20.0f, 10.0f)
                verticalLineToRelative(11.0f)
                curveToRelative(0.0f, 1.1f, -0.9f, 2.0f, -2.0f, 2.0f)
                horizontalLineTo(6.0f)
                curveToRelative(-1.11f, 0.0f, -2.0f, -0.9f, -2.0f, -2.0f)
                verticalLineTo(10.0f)
                curveToRelative(0.0f, -1.11f, 0.89f, -2.0f, 2.0f, -2.0f)
                horizontalLineToRelative(3.0f)
                verticalLineToRelative(2.0f)
                horizontalLineTo(6.0f)
                verticalLineToRelative(11.0f)
                horizontalLineToRelative(12.0f)
                verticalLineTo(10.0f)
                horizontalLineToRelative(-3.0f)
                verticalLineTo(8.0f)
                horizontalLineToRelative(3.0f)
                curveTo(19.1f, 8.0f, 20.0f, 8.89f, 20.0f, 10.0f)
                close()
            }
        }
        return _iosShare!!
    }

private var _iosShare: ImageVector? = null
