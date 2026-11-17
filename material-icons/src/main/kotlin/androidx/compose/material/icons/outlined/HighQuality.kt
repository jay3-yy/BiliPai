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

public val Icons.Outlined.HighQuality: ImageVector
    get() {
        if (_highQuality != null) {
            return _highQuality!!
        }
        _highQuality = materialIcon(name = "Outlined.HighQuality") {
            materialPath {
                moveTo(19.0f, 4.0f)
                lineTo(5.0f, 4.0f)
                curveToRelative(-1.11f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(12.0f)
                curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(14.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(21.0f, 6.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(19.0f, 18.0f)
                lineTo(5.0f, 18.0f)
                lineTo(5.0f, 6.0f)
                horizontalLineToRelative(14.0f)
                verticalLineToRelative(12.0f)
                close()
                moveTo(7.5f, 13.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(2.0f)
                lineTo(11.0f, 15.0f)
                lineTo(11.0f, 9.0f)
                lineTo(9.5f, 9.0f)
                verticalLineToRelative(2.5f)
                horizontalLineToRelative(-2.0f)
                lineTo(7.5f, 9.0f)
                lineTo(6.0f, 9.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(1.5f)
                close()
                moveTo(14.0f, 15.0f)
                horizontalLineToRelative(0.75f)
                verticalLineToRelative(1.5f)
                horizontalLineToRelative(1.5f)
                lineTo(16.25f, 15.0f)
                lineTo(17.0f, 15.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(-4.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineToRelative(-3.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(4.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                close()
                moveTo(14.5f, 10.5f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(3.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(-3.0f)
                close()
            }
        }
        return _highQuality!!
    }

private var _highQuality: ImageVector? = null
