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

public val Icons.Outlined.TheaterComedy: ImageVector
    get() {
        if (_theaterComedy != null) {
            return _theaterComedy!!
        }
        _theaterComedy = materialIcon(name = "Outlined.TheaterComedy") {
            materialPath {
                moveTo(19.0f, 6.5f)
                moveToRelative(-1.0f, 0.0f)
                arcToRelative(1.0f, 1.0f, 0.0f, true, true, 2.0f, 0.0f)
                arcToRelative(1.0f, 1.0f, 0.0f, true, true, -2.0f, 0.0f)
            }
            materialPath {
                moveTo(15.0f, 6.5f)
                moveToRelative(-1.0f, 0.0f)
                arcToRelative(1.0f, 1.0f, 0.0f, true, true, 2.0f, 0.0f)
                arcToRelative(1.0f, 1.0f, 0.0f, true, true, -2.0f, 0.0f)
            }
            materialPath {
                moveTo(16.99f, 9.0f)
                curveToRelative(-1.38f, 0.0f, -2.5f, 0.84f, -2.5f, 1.88f)
                horizontalLineToRelative(5.0f)
                curveTo(19.49f, 9.84f, 18.37f, 9.0f, 16.99f, 9.0f)
                close()
            }
            materialPath {
                moveTo(1.0f, 16.0f)
                curveToRelative(0.0f, 3.31f, 2.69f, 6.0f, 6.0f, 6.0f)
                reflectiveCurveToRelative(6.0f, -2.69f, 6.0f, -6.0f)
                verticalLineTo(9.0f)
                horizontalLineTo(1.0f)
                verticalLineTo(16.0f)
                close()
                moveTo(3.0f, 11.0f)
                horizontalLineToRelative(8.0f)
                verticalLineToRelative(5.0f)
                curveToRelative(0.0f, 2.21f, -1.79f, 4.0f, -4.0f, 4.0f)
                reflectiveCurveToRelative(-4.0f, -1.79f, -4.0f, -4.0f)
                verticalLineTo(11.0f)
                close()
            }
            materialPath {
                moveTo(11.0f, 2.0f)
                verticalLineToRelative(5.5f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(4.0f)
                horizontalLineToRelative(8.0f)
                verticalLineToRelative(5.0f)
                curveToRelative(0.0f, 2.21f, -1.79f, 4.0f, -4.0f, 4.0f)
                curveToRelative(-0.95f, 0.0f, -1.81f, -0.35f, -2.5f, -0.9f)
                verticalLineToRelative(2.35f)
                curveTo(15.26f, 14.8f, 16.11f, 15.0f, 17.0f, 15.0f)
                curveToRelative(3.31f, 0.0f, 6.0f, -2.69f, 6.0f, -6.0f)
                verticalLineTo(2.0f)
                horizontalLineTo(11.0f)
                close()
            }
            materialPath {
                moveTo(5.0f, 13.5f)
                moveToRelative(-1.0f, 0.0f)
                arcToRelative(1.0f, 1.0f, 0.0f, true, true, 2.0f, 0.0f)
                arcToRelative(1.0f, 1.0f, 0.0f, true, true, -2.0f, 0.0f)
            }
            materialPath {
                moveTo(9.0f, 13.5f)
                moveToRelative(-1.0f, 0.0f)
                arcToRelative(1.0f, 1.0f, 0.0f, true, true, 2.0f, 0.0f)
                arcToRelative(1.0f, 1.0f, 0.0f, true, true, -2.0f, 0.0f)
            }
            materialPath {
                moveTo(7.0f, 17.88f)
                curveToRelative(1.38f, 0.0f, 2.5f, -0.84f, 2.5f, -1.88f)
                horizontalLineToRelative(-5.0f)
                curveTo(4.5f, 17.04f, 5.62f, 17.88f, 7.0f, 17.88f)
                close()
            }
        }
        return _theaterComedy!!
    }

private var _theaterComedy: ImageVector? = null
