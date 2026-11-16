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

public val Icons.Outlined.PersonAddAlt1: ImageVector
    get() {
        if (_personAddAlt1 != null) {
            return _personAddAlt1!!
        }
        _personAddAlt1 = materialIcon(name = "Outlined.PersonAddAlt1") {
            materialPath {
                moveTo(13.0f, 8.0f)
                curveToRelative(0.0f, -2.21f, -1.79f, -4.0f, -4.0f, -4.0f)
                reflectiveCurveTo(5.0f, 5.79f, 5.0f, 8.0f)
                reflectiveCurveToRelative(1.79f, 4.0f, 4.0f, 4.0f)
                reflectiveCurveTo(13.0f, 10.21f, 13.0f, 8.0f)
                close()
                moveTo(11.0f, 8.0f)
                curveToRelative(0.0f, 1.1f, -0.9f, 2.0f, -2.0f, 2.0f)
                reflectiveCurveTo(7.0f, 9.1f, 7.0f, 8.0f)
                reflectiveCurveToRelative(0.9f, -2.0f, 2.0f, -2.0f)
                reflectiveCurveTo(11.0f, 6.9f, 11.0f, 8.0f)
                close()
                moveTo(1.0f, 18.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(16.0f)
                verticalLineToRelative(-2.0f)
                curveToRelative(0.0f, -2.66f, -5.33f, -4.0f, -8.0f, -4.0f)
                reflectiveCurveTo(1.0f, 15.34f, 1.0f, 18.0f)
                close()
                moveTo(3.0f, 18.0f)
                curveToRelative(0.2f, -0.71f, 3.3f, -2.0f, 6.0f, -2.0f)
                curveToRelative(2.69f, 0.0f, 5.78f, 1.28f, 6.0f, 2.0f)
                horizontalLineTo(3.0f)
                close()
                moveTo(20.0f, 15.0f)
                verticalLineToRelative(-3.0f)
                horizontalLineToRelative(3.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(-3.0f)
                verticalLineTo(7.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(3.0f)
                horizontalLineToRelative(-3.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(3.0f)
                verticalLineToRelative(3.0f)
                horizontalLineTo(20.0f)
                close()
            }
        }
        return _personAddAlt1!!
    }

private var _personAddAlt1: ImageVector? = null
