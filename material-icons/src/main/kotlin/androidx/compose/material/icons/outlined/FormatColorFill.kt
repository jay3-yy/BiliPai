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

public val Icons.Outlined.FormatColorFill: ImageVector
    get() {
        if (_formatColorFill != null) {
            return _formatColorFill!!
        }
        _formatColorFill = materialIcon(name = "Outlined.FormatColorFill") {
            materialPath {
                moveTo(16.56f, 8.94f)
                lineTo(7.62f, 0.0f)
                lineTo(6.21f, 1.41f)
                lineToRelative(2.38f, 2.38f)
                lineTo(3.44f, 8.94f)
                curveToRelative(-0.59f, 0.59f, -0.59f, 1.54f, 0.0f, 2.12f)
                lineToRelative(5.5f, 5.5f)
                curveTo(9.23f, 16.85f, 9.62f, 17.0f, 10.0f, 17.0f)
                reflectiveCurveToRelative(0.77f, -0.15f, 1.06f, -0.44f)
                lineToRelative(5.5f, -5.5f)
                curveTo(17.15f, 10.48f, 17.15f, 9.53f, 16.56f, 8.94f)
                close()
                moveTo(5.21f, 10.0f)
                lineTo(10.0f, 5.21f)
                lineTo(14.79f, 10.0f)
                horizontalLineTo(5.21f)
                close()
                moveTo(19.0f, 11.5f)
                curveToRelative(0.0f, 0.0f, -2.0f, 2.17f, -2.0f, 3.5f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                reflectiveCurveToRelative(2.0f, -0.9f, 2.0f, -2.0f)
                curveTo(21.0f, 13.67f, 19.0f, 11.5f, 19.0f, 11.5f)
                close()
                moveTo(2.0f, 20.0f)
                horizontalLineToRelative(20.0f)
                verticalLineToRelative(4.0f)
                horizontalLineTo(2.0f)
                verticalLineTo(20.0f)
                close()
            }
        }
        return _formatColorFill!!
    }

private var _formatColorFill: ImageVector? = null
