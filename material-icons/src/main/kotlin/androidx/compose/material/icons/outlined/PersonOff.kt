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

public val Icons.Outlined.PersonOff: ImageVector
    get() {
        if (_personOff != null) {
            return _personOff!!
        }
        _personOff = materialIcon(name = "Outlined.PersonOff") {
            materialPath {
                moveTo(20.0f, 17.17f)
                lineToRelative(-3.37f, -3.38f)
                curveToRelative(0.64f, 0.22f, 1.23f, 0.48f, 1.77f, 0.76f)
                curveTo(19.37f, 15.06f, 19.98f, 16.07f, 20.0f, 17.17f)
                close()
                moveTo(21.19f, 21.19f)
                lineToRelative(-1.41f, 1.41f)
                lineTo(17.17f, 20.0f)
                horizontalLineTo(4.0f)
                verticalLineToRelative(-2.78f)
                curveToRelative(0.0f, -1.12f, 0.61f, -2.15f, 1.61f, -2.66f)
                curveToRelative(1.29f, -0.66f, 2.87f, -1.22f, 4.67f, -1.45f)
                lineTo(1.39f, 4.22f)
                lineToRelative(1.41f, -1.41f)
                lineTo(21.19f, 21.19f)
                close()
                moveTo(15.17f, 18.0f)
                lineToRelative(-3.0f, -3.0f)
                curveToRelative(-0.06f, 0.0f, -0.11f, 0.0f, -0.17f, 0.0f)
                curveToRelative(-2.37f, 0.0f, -4.29f, 0.73f, -5.48f, 1.34f)
                curveTo(6.2f, 16.5f, 6.0f, 16.84f, 6.0f, 17.22f)
                verticalLineTo(18.0f)
                horizontalLineTo(15.17f)
                close()
                moveTo(12.0f, 6.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, 0.9f, 2.0f, 2.0f)
                curveToRelative(0.0f, 0.86f, -0.54f, 1.59f, -1.3f, 1.87f)
                lineToRelative(1.48f, 1.48f)
                curveTo(15.28f, 10.64f, 16.0f, 9.4f, 16.0f, 8.0f)
                curveToRelative(0.0f, -2.21f, -1.79f, -4.0f, -4.0f, -4.0f)
                curveToRelative(-1.4f, 0.0f, -2.64f, 0.72f, -3.35f, 1.82f)
                lineToRelative(1.48f, 1.48f)
                curveTo(10.41f, 6.54f, 11.14f, 6.0f, 12.0f, 6.0f)
                close()
            }
        }
        return _personOff!!
    }

private var _personOff: ImageVector? = null
