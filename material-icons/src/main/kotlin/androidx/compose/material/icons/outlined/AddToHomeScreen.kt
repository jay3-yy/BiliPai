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
    "Use the AutoMirrored version at Icons.AutoMirrored.Outlined.AddToHomeScreen",
    ReplaceWith( "Icons.AutoMirrored.Outlined.AddToHomeScreen",
            "androidx.compose.material.icons.automirrored.outlined.AddToHomeScreen"),
)
public val Icons.Outlined.AddToHomeScreen: ImageVector
    get() {
        if (_addToHomeScreen != null) {
            return _addToHomeScreen!!
        }
        _addToHomeScreen = materialIcon(name = "Outlined.AddToHomeScreen") {
            materialPath {
                moveTo(18.0f, 1.01f)
                lineTo(8.0f, 1.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(3.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(5.0f)
                horizontalLineToRelative(10.0f)
                verticalLineToRelative(14.0f)
                horizontalLineTo(8.0f)
                verticalLineToRelative(-1.0f)
                horizontalLineTo(6.0f)
                verticalLineToRelative(3.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(10.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(3.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -1.99f, -2.0f, -1.99f)
                close()
                moveTo(10.0f, 15.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(8.0f)
                horizontalLineTo(5.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(3.59f)
                lineTo(3.0f, 15.59f)
                lineTo(4.41f, 17.0f)
                lineTo(10.0f, 11.41f)
                verticalLineTo(15.0f)
                close()
            }
        }
        return _addToHomeScreen!!
    }

private var _addToHomeScreen: ImageVector? = null
