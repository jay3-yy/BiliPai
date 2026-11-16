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

public val Icons.Outlined.ClosedCaptionOff: ImageVector
    get() {
        if (_closedCaptionOff != null) {
            return _closedCaptionOff!!
        }
        _closedCaptionOff = materialIcon(name = "Outlined.ClosedCaptionOff") {
            materialPath {
                moveTo(19.0f, 4.0f)
                horizontalLineTo(5.0f)
                curveTo(3.89f, 4.0f, 3.0f, 4.9f, 3.0f, 6.0f)
                verticalLineToRelative(12.0f)
                curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(14.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(6.0f)
                curveTo(21.0f, 4.9f, 20.1f, 4.0f, 19.0f, 4.0f)
                close()
                moveTo(19.0f, 18.0f)
                horizontalLineTo(5.0f)
                verticalLineTo(6.0f)
                horizontalLineToRelative(14.0f)
                verticalLineTo(18.0f)
                close()
            }
            materialPath {
                moveTo(7.0f, 15.0f)
                horizontalLineToRelative(3.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(-1.0f)
                horizontalLineTo(9.5f)
                verticalLineToRelative(0.5f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(-3.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(11.0f)
                horizontalLineTo(11.0f)
                verticalLineToRelative(-1.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineTo(7.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(4.0f)
                curveTo(6.0f, 14.55f, 6.45f, 15.0f, 7.0f, 15.0f)
                close()
            }
            materialPath {
                moveTo(14.0f, 15.0f)
                horizontalLineToRelative(3.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(-1.0f)
                horizontalLineToRelative(-1.5f)
                verticalLineToRelative(0.5f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(-3.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(11.0f)
                horizontalLineTo(18.0f)
                verticalLineToRelative(-1.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineToRelative(-3.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(4.0f)
                curveTo(13.0f, 14.55f, 13.45f, 15.0f, 14.0f, 15.0f)
                close()
            }
        }
        return _closedCaptionOff!!
    }

private var _closedCaptionOff: ImageVector? = null
