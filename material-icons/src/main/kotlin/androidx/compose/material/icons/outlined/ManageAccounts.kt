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

public val Icons.Outlined.ManageAccounts: ImageVector
    get() {
        if (_manageAccounts != null) {
            return _manageAccounts!!
        }
        _manageAccounts = materialIcon(name = "Outlined.ManageAccounts") {
            materialPath {
                moveTo(4.0f, 18.0f)
                verticalLineToRelative(-0.65f)
                curveToRelative(0.0f, -0.34f, 0.16f, -0.66f, 0.41f, -0.81f)
                curveTo(6.1f, 15.53f, 8.03f, 15.0f, 10.0f, 15.0f)
                curveToRelative(0.03f, 0.0f, 0.05f, 0.0f, 0.08f, 0.01f)
                curveToRelative(0.1f, -0.7f, 0.3f, -1.37f, 0.59f, -1.98f)
                curveTo(10.45f, 13.01f, 10.23f, 13.0f, 10.0f, 13.0f)
                curveToRelative(-2.42f, 0.0f, -4.68f, 0.67f, -6.61f, 1.82f)
                curveTo(2.51f, 15.34f, 2.0f, 16.32f, 2.0f, 17.35f)
                verticalLineTo(20.0f)
                horizontalLineToRelative(9.26f)
                curveToRelative(-0.42f, -0.6f, -0.75f, -1.28f, -0.97f, -2.0f)
                horizontalLineTo(4.0f)
                close()
            }
            materialPath {
                moveTo(10.0f, 12.0f)
                curveToRelative(2.21f, 0.0f, 4.0f, -1.79f, 4.0f, -4.0f)
                reflectiveCurveToRelative(-1.79f, -4.0f, -4.0f, -4.0f)
                curveTo(7.79f, 4.0f, 6.0f, 5.79f, 6.0f, 8.0f)
                reflectiveCurveTo(7.79f, 12.0f, 10.0f, 12.0f)
                close()
                moveTo(10.0f, 6.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, 0.9f, 2.0f, 2.0f)
                reflectiveCurveToRelative(-0.9f, 2.0f, -2.0f, 2.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, -0.9f, -2.0f, -2.0f)
                reflectiveCurveTo(8.9f, 6.0f, 10.0f, 6.0f)
                close()
            }
            materialPath {
                moveTo(20.75f, 16.0f)
                curveToRelative(0.0f, -0.22f, -0.03f, -0.42f, -0.06f, -0.63f)
                lineToRelative(1.14f, -1.01f)
                lineToRelative(-1.0f, -1.73f)
                lineToRelative(-1.45f, 0.49f)
                curveToRelative(-0.32f, -0.27f, -0.68f, -0.48f, -1.08f, -0.63f)
                lineTo(18.0f, 11.0f)
                horizontalLineToRelative(-2.0f)
                lineToRelative(-0.3f, 1.49f)
                curveToRelative(-0.4f, 0.15f, -0.76f, 0.36f, -1.08f, 0.63f)
                lineToRelative(-1.45f, -0.49f)
                lineToRelative(-1.0f, 1.73f)
                lineToRelative(1.14f, 1.01f)
                curveToRelative(-0.03f, 0.21f, -0.06f, 0.41f, -0.06f, 0.63f)
                reflectiveCurveToRelative(0.03f, 0.42f, 0.06f, 0.63f)
                lineToRelative(-1.14f, 1.01f)
                lineToRelative(1.0f, 1.73f)
                lineToRelative(1.45f, -0.49f)
                curveToRelative(0.32f, 0.27f, 0.68f, 0.48f, 1.08f, 0.63f)
                lineTo(16.0f, 21.0f)
                horizontalLineToRelative(2.0f)
                lineToRelative(0.3f, -1.49f)
                curveToRelative(0.4f, -0.15f, 0.76f, -0.36f, 1.08f, -0.63f)
                lineToRelative(1.45f, 0.49f)
                lineToRelative(1.0f, -1.73f)
                lineToRelative(-1.14f, -1.01f)
                curveTo(20.72f, 16.42f, 20.75f, 16.22f, 20.75f, 16.0f)
                close()
                moveTo(17.0f, 18.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, -0.9f, -2.0f, -2.0f)
                reflectiveCurveToRelative(0.9f, -2.0f, 2.0f, -2.0f)
                reflectiveCurveToRelative(2.0f, 0.9f, 2.0f, 2.0f)
                reflectiveCurveTo(18.1f, 18.0f, 17.0f, 18.0f)
                close()
            }
        }
        return _manageAccounts!!
    }

private var _manageAccounts: ImageVector? = null
