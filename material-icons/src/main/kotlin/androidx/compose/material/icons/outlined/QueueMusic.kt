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
import kotlin.Deprecated

@Deprecated(
    "Use the AutoMirrored version at Icons.AutoMirrored.Outlined.QueueMusic",
    ReplaceWith( "Icons.AutoMirrored.Outlined.QueueMusic",
            "androidx.compose.material.icons.automirrored.outlined.QueueMusic"),
)
public val Icons.Outlined.QueueMusic: ImageVector
    get() {
        if (_queueMusic != null) {
            return _queueMusic!!
        }
        _queueMusic = materialIcon(name = "Outlined.QueueMusic") {
            materialPath {
                moveTo(22.0f, 6.0f)
                horizontalLineToRelative(-5.0f)
                verticalLineToRelative(8.18f)
                curveTo(16.69f, 14.07f, 16.35f, 14.0f, 16.0f, 14.0f)
                curveToRelative(-1.66f, 0.0f, -3.0f, 1.34f, -3.0f, 3.0f)
                reflectiveCurveToRelative(1.34f, 3.0f, 3.0f, 3.0f)
                reflectiveCurveToRelative(3.0f, -1.34f, 3.0f, -3.0f)
                verticalLineTo(8.0f)
                horizontalLineToRelative(3.0f)
                verticalLineTo(6.0f)
                close()
                moveTo(15.0f, 6.0f)
                horizontalLineTo(3.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(12.0f)
                verticalLineTo(6.0f)
                close()
                moveTo(15.0f, 10.0f)
                horizontalLineTo(3.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(12.0f)
                verticalLineTo(10.0f)
                close()
                moveTo(11.0f, 14.0f)
                horizontalLineTo(3.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(8.0f)
                verticalLineTo(14.0f)
                close()
            }
        }
        return _queueMusic!!
    }

private var _queueMusic: ImageVector? = null
