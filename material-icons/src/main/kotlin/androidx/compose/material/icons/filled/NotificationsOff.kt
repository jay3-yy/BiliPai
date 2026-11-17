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

public val Icons.Filled.NotificationsOff: ImageVector
    get() {
        if (_notificationsOff != null) {
            return _notificationsOff!!
        }
        _notificationsOff = materialIcon(name = "Filled.NotificationsOff") {
            materialPath {
                moveTo(20.0f, 18.69f)
                lineTo(7.84f, 6.14f)
                lineTo(5.27f, 3.49f)
                lineTo(4.0f, 4.76f)
                lineToRelative(2.8f, 2.8f)
                verticalLineToRelative(0.01f)
                curveToRelative(-0.52f, 0.99f, -0.8f, 2.16f, -0.8f, 3.42f)
                verticalLineToRelative(5.0f)
                lineToRelative(-2.0f, 2.0f)
                verticalLineToRelative(1.0f)
                horizontalLineToRelative(13.73f)
                lineToRelative(2.0f, 2.0f)
                lineTo(21.0f, 19.72f)
                lineToRelative(-1.0f, -1.03f)
                close()
                moveTo(12.0f, 22.0f)
                curveToRelative(1.11f, 0.0f, 2.0f, -0.89f, 2.0f, -2.0f)
                horizontalLineToRelative(-4.0f)
                curveToRelative(0.0f, 1.11f, 0.89f, 2.0f, 2.0f, 2.0f)
                close()
                moveTo(18.0f, 14.68f)
                lineTo(18.0f, 11.0f)
                curveToRelative(0.0f, -3.08f, -1.64f, -5.64f, -4.5f, -6.32f)
                lineTo(13.5f, 4.0f)
                curveToRelative(0.0f, -0.83f, -0.67f, -1.5f, -1.5f, -1.5f)
                reflectiveCurveToRelative(-1.5f, 0.67f, -1.5f, 1.5f)
                verticalLineToRelative(0.68f)
                curveToRelative(-0.15f, 0.03f, -0.29f, 0.08f, -0.42f, 0.12f)
                curveToRelative(-0.1f, 0.03f, -0.2f, 0.07f, -0.3f, 0.11f)
                horizontalLineToRelative(-0.01f)
                curveToRelative(-0.01f, 0.0f, -0.01f, 0.0f, -0.02f, 0.01f)
                curveToRelative(-0.23f, 0.09f, -0.46f, 0.2f, -0.68f, 0.31f)
                curveToRelative(0.0f, 0.0f, -0.01f, 0.0f, -0.01f, 0.01f)
                lineTo(18.0f, 14.68f)
                close()
            }
        }
        return _notificationsOff!!
    }

private var _notificationsOff: ImageVector? = null
