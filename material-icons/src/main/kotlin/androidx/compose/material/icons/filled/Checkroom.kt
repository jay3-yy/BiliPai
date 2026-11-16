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

public val Icons.Filled.Checkroom: ImageVector
    get() {
        if (_checkroom != null) {
            return _checkroom!!
        }
        _checkroom = materialIcon(name = "Filled.Checkroom") {
            materialPath {
                moveTo(21.6f, 18.2f)
                lineTo(13.0f, 11.75f)
                verticalLineToRelative(-0.91f)
                curveToRelative(1.65f, -0.49f, 2.8f, -2.17f, 2.43f, -4.05f)
                curveToRelative(-0.26f, -1.31f, -1.3f, -2.4f, -2.61f, -2.7f)
                curveTo(10.54f, 3.57f, 8.5f, 5.3f, 8.5f, 7.5f)
                horizontalLineToRelative(2.0f)
                curveTo(10.5f, 6.67f, 11.17f, 6.0f, 12.0f, 6.0f)
                reflectiveCurveToRelative(1.5f, 0.67f, 1.5f, 1.5f)
                curveToRelative(0.0f, 0.84f, -0.69f, 1.52f, -1.53f, 1.5f)
                curveTo(11.43f, 8.99f, 11.0f, 9.45f, 11.0f, 9.99f)
                verticalLineToRelative(1.76f)
                lineTo(2.4f, 18.2f)
                curveTo(1.63f, 18.78f, 2.04f, 20.0f, 3.0f, 20.0f)
                horizontalLineToRelative(9.0f)
                horizontalLineToRelative(9.0f)
                curveTo(21.96f, 20.0f, 22.37f, 18.78f, 21.6f, 18.2f)
                close()
                moveTo(6.0f, 18.0f)
                lineToRelative(6.0f, -4.5f)
                lineToRelative(6.0f, 4.5f)
                horizontalLineTo(6.0f)
                close()
            }
        }
        return _checkroom!!
    }

private var _checkroom: ImageVector? = null
