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

public val Icons.Outlined.Policy: ImageVector
    get() {
        if (_policy != null) {
            return _policy!!
        }
        _policy = materialIcon(name = "Outlined.Policy") {
            materialPath {
                moveTo(12.0f, 1.0f)
                lineTo(3.0f, 5.0f)
                verticalLineToRelative(6.0f)
                curveToRelative(0.0f, 5.55f, 3.84f, 10.74f, 9.0f, 12.0f)
                curveToRelative(5.16f, -1.26f, 9.0f, -6.45f, 9.0f, -12.0f)
                verticalLineTo(5.0f)
                lineTo(12.0f, 1.0f)
                close()
                moveTo(19.0f, 11.0f)
                curveToRelative(0.0f, 1.85f, -0.51f, 3.65f, -1.38f, 5.21f)
                lineToRelative(-1.45f, -1.45f)
                curveToRelative(1.29f, -1.94f, 1.07f, -4.58f, -0.64f, -6.29f)
                curveToRelative(-1.95f, -1.95f, -5.12f, -1.95f, -7.07f, 0.0f)
                curveToRelative(-1.95f, 1.95f, -1.95f, 5.12f, 0.0f, 7.07f)
                curveToRelative(1.71f, 1.71f, 4.35f, 1.92f, 6.29f, 0.64f)
                lineToRelative(1.72f, 1.72f)
                curveToRelative(-1.19f, 1.42f, -2.73f, 2.51f, -4.47f, 3.04f)
                curveTo(7.98f, 19.69f, 5.0f, 15.52f, 5.0f, 11.0f)
                verticalLineTo(6.3f)
                lineToRelative(7.0f, -3.11f)
                lineToRelative(7.0f, 3.11f)
                verticalLineTo(11.0f)
                close()
                moveTo(12.0f, 15.0f)
                curveToRelative(-1.66f, 0.0f, -3.0f, -1.34f, -3.0f, -3.0f)
                reflectiveCurveToRelative(1.34f, -3.0f, 3.0f, -3.0f)
                reflectiveCurveToRelative(3.0f, 1.34f, 3.0f, 3.0f)
                reflectiveCurveTo(13.66f, 15.0f, 12.0f, 15.0f)
                close()
            }
        }
        return _policy!!
    }

private var _policy: ImageVector? = null
