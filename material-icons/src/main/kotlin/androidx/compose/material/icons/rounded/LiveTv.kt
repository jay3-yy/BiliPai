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

public val Icons.Rounded.LiveTv: ImageVector
    get() {
        if (_liveTv != null) {
            return _liveTv!!
        }
        _liveTv = materialIcon(name = "Rounded.LiveTv") {
            materialPath {
                moveTo(10.5f, 17.15f)
                lineToRelative(3.98f, -2.28f)
                curveToRelative(0.67f, -0.38f, 0.67f, -1.35f, 0.0f, -1.74f)
                lineToRelative(-3.98f, -2.28f)
                curveToRelative(-0.67f, -0.38f, -1.5f, 0.11f, -1.5f, 0.87f)
                verticalLineToRelative(4.55f)
                curveToRelative(0.0f, 0.77f, 0.83f, 1.26f, 1.5f, 0.88f)
                close()
                moveTo(21.0f, 6.0f)
                horizontalLineToRelative(-7.59f)
                lineToRelative(2.94f, -2.94f)
                curveToRelative(0.2f, -0.2f, 0.2f, -0.51f, 0.0f, -0.71f)
                reflectiveCurveToRelative(-0.51f, -0.2f, -0.71f, 0.0f)
                lineTo(12.0f, 5.99f)
                lineTo(8.36f, 2.35f)
                curveToRelative(-0.2f, -0.2f, -0.51f, -0.2f, -0.71f, 0.0f)
                reflectiveCurveToRelative(-0.2f, 0.51f, 0.0f, 0.71f)
                lineTo(10.59f, 6.0f)
                lineTo(3.0f, 6.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.89f, -2.0f, 2.0f)
                verticalLineToRelative(12.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(18.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(23.0f, 8.0f)
                curveToRelative(0.0f, -1.11f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(20.0f, 20.0f)
                lineTo(4.0f, 20.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                lineTo(3.0f, 9.0f)
                curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                horizontalLineToRelative(16.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                verticalLineToRelative(10.0f)
                curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                close()
            }
        }
        return _liveTv!!
    }

private var _liveTv: ImageVector? = null
