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

public val Icons.Outlined.Groups: ImageVector
    get() {
        if (_groups != null) {
            return _groups!!
        }
        _groups = materialIcon(name = "Outlined.Groups") {
            materialPath {
                moveTo(4.0f, 13.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                reflectiveCurveToRelative(-2.0f, 0.9f, -2.0f, 2.0f)
                curveTo(2.0f, 12.1f, 2.9f, 13.0f, 4.0f, 13.0f)
                close()
                moveTo(5.13f, 14.1f)
                curveTo(4.76f, 14.04f, 4.39f, 14.0f, 4.0f, 14.0f)
                curveToRelative(-0.99f, 0.0f, -1.93f, 0.21f, -2.78f, 0.58f)
                curveTo(0.48f, 14.9f, 0.0f, 15.62f, 0.0f, 16.43f)
                verticalLineTo(18.0f)
                lineToRelative(4.5f, 0.0f)
                verticalLineToRelative(-1.61f)
                curveTo(4.5f, 15.56f, 4.73f, 14.78f, 5.13f, 14.1f)
                close()
                moveTo(20.0f, 13.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                reflectiveCurveToRelative(-2.0f, 0.9f, -2.0f, 2.0f)
                curveTo(18.0f, 12.1f, 18.9f, 13.0f, 20.0f, 13.0f)
                close()
                moveTo(24.0f, 16.43f)
                curveToRelative(0.0f, -0.81f, -0.48f, -1.53f, -1.22f, -1.85f)
                curveTo(21.93f, 14.21f, 20.99f, 14.0f, 20.0f, 14.0f)
                curveToRelative(-0.39f, 0.0f, -0.76f, 0.04f, -1.13f, 0.1f)
                curveToRelative(0.4f, 0.68f, 0.63f, 1.46f, 0.63f, 2.29f)
                verticalLineTo(18.0f)
                lineToRelative(4.5f, 0.0f)
                verticalLineTo(16.43f)
                close()
                moveTo(16.24f, 13.65f)
                curveToRelative(-1.17f, -0.52f, -2.61f, -0.9f, -4.24f, -0.9f)
                curveToRelative(-1.63f, 0.0f, -3.07f, 0.39f, -4.24f, 0.9f)
                curveTo(6.68f, 14.13f, 6.0f, 15.21f, 6.0f, 16.39f)
                verticalLineTo(18.0f)
                horizontalLineToRelative(12.0f)
                verticalLineToRelative(-1.61f)
                curveTo(18.0f, 15.21f, 17.32f, 14.13f, 16.24f, 13.65f)
                close()
                moveTo(8.07f, 16.0f)
                curveToRelative(0.09f, -0.23f, 0.13f, -0.39f, 0.91f, -0.69f)
                curveToRelative(0.97f, -0.38f, 1.99f, -0.56f, 3.02f, -0.56f)
                reflectiveCurveToRelative(2.05f, 0.18f, 3.02f, 0.56f)
                curveToRelative(0.77f, 0.3f, 0.81f, 0.46f, 0.91f, 0.69f)
                horizontalLineTo(8.07f)
                close()
                moveTo(12.0f, 8.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                reflectiveCurveToRelative(-0.45f, 1.0f, -1.0f, 1.0f)
                reflectiveCurveToRelative(-1.0f, -0.45f, -1.0f, -1.0f)
                reflectiveCurveTo(11.45f, 8.0f, 12.0f, 8.0f)
                moveTo(12.0f, 6.0f)
                curveToRelative(-1.66f, 0.0f, -3.0f, 1.34f, -3.0f, 3.0f)
                curveToRelative(0.0f, 1.66f, 1.34f, 3.0f, 3.0f, 3.0f)
                reflectiveCurveToRelative(3.0f, -1.34f, 3.0f, -3.0f)
                curveTo(15.0f, 7.34f, 13.66f, 6.0f, 12.0f, 6.0f)
                lineTo(12.0f, 6.0f)
                close()
            }
        }
        return _groups!!
    }

private var _groups: ImageVector? = null
