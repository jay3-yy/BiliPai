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

public val Icons.Outlined.AlternateEmail: ImageVector
    get() {
        if (_alternateEmail != null) {
            return _alternateEmail!!
        }
        _alternateEmail = materialIcon(name = "Outlined.AlternateEmail") {
            materialPath {
                moveTo(12.0f, 1.95f)
                curveToRelative(-5.52f, 0.0f, -10.0f, 4.48f, -10.0f, 10.0f)
                reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
                horizontalLineToRelative(5.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(-5.0f)
                curveToRelative(-4.34f, 0.0f, -8.0f, -3.66f, -8.0f, -8.0f)
                reflectiveCurveToRelative(3.66f, -8.0f, 8.0f, -8.0f)
                reflectiveCurveToRelative(8.0f, 3.66f, 8.0f, 8.0f)
                verticalLineToRelative(1.43f)
                curveToRelative(0.0f, 0.79f, -0.71f, 1.57f, -1.5f, 1.57f)
                reflectiveCurveToRelative(-1.5f, -0.78f, -1.5f, -1.57f)
                verticalLineToRelative(-1.43f)
                curveToRelative(0.0f, -2.76f, -2.24f, -5.0f, -5.0f, -5.0f)
                reflectiveCurveToRelative(-5.0f, 2.24f, -5.0f, 5.0f)
                reflectiveCurveToRelative(2.24f, 5.0f, 5.0f, 5.0f)
                curveToRelative(1.38f, 0.0f, 2.64f, -0.56f, 3.54f, -1.47f)
                curveToRelative(0.65f, 0.89f, 1.77f, 1.47f, 2.96f, 1.47f)
                curveToRelative(1.97f, 0.0f, 3.5f, -1.6f, 3.5f, -3.57f)
                verticalLineToRelative(-1.43f)
                curveToRelative(0.0f, -5.52f, -4.48f, -10.0f, -10.0f, -10.0f)
                close()
                moveTo(12.0f, 14.95f)
                curveToRelative(-1.66f, 0.0f, -3.0f, -1.34f, -3.0f, -3.0f)
                reflectiveCurveToRelative(1.34f, -3.0f, 3.0f, -3.0f)
                reflectiveCurveToRelative(3.0f, 1.34f, 3.0f, 3.0f)
                reflectiveCurveToRelative(-1.34f, 3.0f, -3.0f, 3.0f)
                close()
            }
        }
        return _alternateEmail!!
    }

private var _alternateEmail: ImageVector? = null
