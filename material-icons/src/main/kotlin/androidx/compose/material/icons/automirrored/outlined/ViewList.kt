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

package androidx.compose.material.icons.automirrored.outlined

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

public val Icons.AutoMirrored.Outlined.ViewList: ImageVector
    get() {
        if (_viewList != null) {
            return _viewList!!
        }
        _viewList = materialIcon(name = "AutoMirrored.Outlined.ViewList", autoMirror = true) {
            materialPath {
                moveTo(3.0f, 5.0f)
                verticalLineToRelative(14.0f)
                horizontalLineToRelative(18.0f)
                verticalLineTo(5.0f)
                horizontalLineTo(3.0f)
                close()
                moveTo(7.0f, 7.0f)
                verticalLineToRelative(2.0f)
                horizontalLineTo(5.0f)
                verticalLineTo(7.0f)
                horizontalLineTo(7.0f)
                close()
                moveTo(5.0f, 13.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(2.0f)
                horizontalLineTo(5.0f)
                close()
                moveTo(5.0f, 15.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(2.0f)
                horizontalLineTo(5.0f)
                verticalLineTo(15.0f)
                close()
                moveTo(19.0f, 17.0f)
                horizontalLineTo(9.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(10.0f)
                verticalLineTo(17.0f)
                close()
                moveTo(19.0f, 13.0f)
                horizontalLineTo(9.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(10.0f)
                verticalLineTo(13.0f)
                close()
                moveTo(19.0f, 9.0f)
                horizontalLineTo(9.0f)
                verticalLineTo(7.0f)
                horizontalLineToRelative(10.0f)
                verticalLineTo(9.0f)
                close()
            }
        }
        return _viewList!!
    }

private var _viewList: ImageVector? = null
