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

public val Icons.Outlined.TouchApp: ImageVector
    get() {
        if (_touchApp != null) {
            return _touchApp!!
        }
        _touchApp = materialIcon(name = "Outlined.TouchApp") {
            materialPath {
                moveTo(18.19f, 12.44f)
                lineToRelative(-3.24f, -1.62f)
                curveToRelative(1.29f, -1.0f, 2.12f, -2.56f, 2.12f, -4.32f)
                curveToRelative(0.0f, -3.03f, -2.47f, -5.5f, -5.5f, -5.5f)
                reflectiveCurveToRelative(-5.5f, 2.47f, -5.5f, 5.5f)
                curveToRelative(0.0f, 2.13f, 1.22f, 3.98f, 3.0f, 4.89f)
                verticalLineToRelative(3.26f)
                curveToRelative(-2.15f, -0.46f, -2.02f, -0.44f, -2.26f, -0.44f)
                curveToRelative(-0.53f, 0.0f, -1.03f, 0.21f, -1.41f, 0.59f)
                lineTo(4.0f, 16.22f)
                lineToRelative(5.09f, 5.09f)
                curveTo(9.52f, 21.75f, 10.12f, 22.0f, 10.74f, 22.0f)
                horizontalLineToRelative(6.3f)
                curveToRelative(0.98f, 0.0f, 1.81f, -0.7f, 1.97f, -1.67f)
                lineToRelative(0.8f, -4.71f)
                curveTo(20.03f, 14.32f, 19.38f, 13.04f, 18.19f, 12.44f)
                close()
                moveTo(17.84f, 15.29f)
                lineTo(17.04f, 20.0f)
                horizontalLineToRelative(-6.3f)
                curveToRelative(-0.09f, 0.0f, -0.17f, -0.04f, -0.24f, -0.1f)
                lineToRelative(-3.68f, -3.68f)
                lineToRelative(4.25f, 0.89f)
                verticalLineTo(6.5f)
                curveToRelative(0.0f, -0.28f, 0.22f, -0.5f, 0.5f, -0.5f)
                curveToRelative(0.28f, 0.0f, 0.5f, 0.22f, 0.5f, 0.5f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(1.76f)
                lineToRelative(3.46f, 1.73f)
                curveTo(17.69f, 14.43f, 17.91f, 14.86f, 17.84f, 15.29f)
                close()
                moveTo(8.07f, 6.5f)
                curveToRelative(0.0f, -1.93f, 1.57f, -3.5f, 3.5f, -3.5f)
                reflectiveCurveToRelative(3.5f, 1.57f, 3.5f, 3.5f)
                curveToRelative(0.0f, 0.95f, -0.38f, 1.81f, -1.0f, 2.44f)
                verticalLineTo(6.5f)
                curveToRelative(0.0f, -1.38f, -1.12f, -2.5f, -2.5f, -2.5f)
                curveToRelative(-1.38f, 0.0f, -2.5f, 1.12f, -2.5f, 2.5f)
                verticalLineToRelative(2.44f)
                curveTo(8.45f, 8.31f, 8.07f, 7.45f, 8.07f, 6.5f)
                close()
            }
        }
        return _touchApp!!
    }

private var _touchApp: ImageVector? = null
