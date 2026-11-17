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
    "Use the AutoMirrored version at Icons.AutoMirrored.Outlined.ViewSidebar",
    ReplaceWith( "Icons.AutoMirrored.Outlined.ViewSidebar",
            "androidx.compose.material.icons.automirrored.outlined.ViewSidebar"),
)
public val Icons.Outlined.ViewSidebar: ImageVector
    get() {
        if (_viewSidebar != null) {
            return _viewSidebar!!
        }
        _viewSidebar = materialIcon(name = "Outlined.ViewSidebar") {
            materialPath {
                moveTo(2.0f, 4.0f)
                verticalLineToRelative(16.0f)
                horizontalLineToRelative(20.0f)
                verticalLineTo(4.0f)
                horizontalLineTo(2.0f)
                close()
                moveTo(20.0f, 8.67f)
                horizontalLineToRelative(-2.5f)
                verticalLineTo(6.0f)
                horizontalLineTo(20.0f)
                verticalLineTo(8.67f)
                close()
                moveTo(17.5f, 10.67f)
                horizontalLineTo(20.0f)
                verticalLineToRelative(2.67f)
                horizontalLineToRelative(-2.5f)
                verticalLineTo(10.67f)
                close()
                moveTo(4.0f, 6.0f)
                horizontalLineToRelative(11.5f)
                verticalLineToRelative(12.0f)
                horizontalLineTo(4.0f)
                verticalLineTo(6.0f)
                close()
                moveTo(17.5f, 18.0f)
                verticalLineToRelative(-2.67f)
                horizontalLineTo(20.0f)
                verticalLineTo(18.0f)
                horizontalLineTo(17.5f)
                close()
            }
        }
        return _viewSidebar!!
    }

private var _viewSidebar: ImageVector? = null
