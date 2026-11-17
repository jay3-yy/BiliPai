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

public val Icons.Filled.WatchLater: ImageVector
    get() {
        if (_watchLater != null) {
            return _watchLater!!
        }
        _watchLater = materialIcon(name = "Filled.WatchLater") {
            materialPath {
                moveTo(12.0f, 2.0f)
                curveTo(6.5f, 2.0f, 2.0f, 6.5f, 2.0f, 12.0f)
                reflectiveCurveToRelative(4.5f, 10.0f, 10.0f, 10.0f)
                reflectiveCurveToRelative(10.0f, -4.5f, 10.0f, -10.0f)
                reflectiveCurveTo(17.5f, 2.0f, 12.0f, 2.0f)
                close()
                moveTo(16.2f, 16.2f)
                lineTo(11.0f, 13.0f)
                verticalLineTo(7.0f)
                horizontalLineToRelative(1.5f)
                verticalLineToRelative(5.2f)
                lineToRelative(4.5f, 2.7f)
                lineTo(16.2f, 16.2f)
                close()
            }
        }
        return _watchLater!!
    }

private var _watchLater: ImageVector? = null
