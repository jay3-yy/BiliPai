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

public val Icons.Outlined.Contacts: ImageVector
    get() {
        if (_contacts != null) {
            return _contacts!!
        }
        _contacts = materialIcon(name = "Outlined.Contacts") {
            materialPath {
                moveTo(20.0f, 4.0f)
                lineTo(4.0f, 4.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(12.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(16.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(22.0f, 6.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(20.0f, 18.0f)
                lineTo(4.0f, 18.0f)
                lineTo(4.0f, 6.0f)
                horizontalLineToRelative(16.0f)
                verticalLineToRelative(12.0f)
                close()
                moveTo(4.0f, 0.0f)
                horizontalLineToRelative(16.0f)
                verticalLineToRelative(2.0f)
                lineTo(4.0f, 2.0f)
                close()
                moveTo(4.0f, 22.0f)
                horizontalLineToRelative(16.0f)
                verticalLineToRelative(2.0f)
                lineTo(4.0f, 24.0f)
                close()
                moveTo(12.0f, 12.0f)
                curveToRelative(1.38f, 0.0f, 2.5f, -1.12f, 2.5f, -2.5f)
                reflectiveCurveTo(13.38f, 7.0f, 12.0f, 7.0f)
                reflectiveCurveTo(9.5f, 8.12f, 9.5f, 9.5f)
                reflectiveCurveTo(10.62f, 12.0f, 12.0f, 12.0f)
                close()
                moveTo(12.0f, 8.5f)
                curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                reflectiveCurveToRelative(-0.45f, 1.0f, -1.0f, 1.0f)
                reflectiveCurveToRelative(-1.0f, -0.45f, -1.0f, -1.0f)
                reflectiveCurveToRelative(0.45f, -1.0f, 1.0f, -1.0f)
                close()
                moveTo(17.0f, 15.99f)
                curveTo(17.0f, 13.9f, 13.69f, 13.0f, 12.0f, 13.0f)
                reflectiveCurveToRelative(-5.0f, 0.9f, -5.0f, 2.99f)
                lineTo(7.0f, 17.0f)
                horizontalLineToRelative(10.0f)
                verticalLineToRelative(-1.01f)
                close()
                moveTo(8.81f, 15.5f)
                curveToRelative(0.61f, -0.52f, 2.03f, -1.0f, 3.19f, -1.0f)
                curveToRelative(1.17f, 0.0f, 2.59f, 0.48f, 3.2f, 1.0f)
                lineTo(8.81f, 15.5f)
                close()
            }
        }
        return _contacts!!
    }

private var _contacts: ImageVector? = null
