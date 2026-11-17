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

public val Icons.Outlined.AddPhotoAlternate: ImageVector
    get() {
        if (_addPhotoAlternate != null) {
            return _addPhotoAlternate!!
        }
        _addPhotoAlternate = materialIcon(name = "Outlined.AddPhotoAlternate") {
            materialPath {
                moveTo(18.0f, 20.0f)
                lineTo(4.0f, 20.0f)
                lineTo(4.0f, 6.0f)
                horizontalLineToRelative(9.0f)
                lineTo(13.0f, 4.0f)
                lineTo(4.0f, 4.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(14.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(14.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineToRelative(-9.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(9.0f)
                close()
                moveTo(10.21f, 16.83f)
                lineToRelative(-1.96f, -2.36f)
                lineTo(5.5f, 18.0f)
                horizontalLineToRelative(11.0f)
                lineToRelative(-3.54f, -4.71f)
                close()
                moveTo(20.0f, 4.0f)
                lineTo(20.0f, 1.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(3.0f)
                horizontalLineToRelative(-3.0f)
                curveToRelative(0.01f, 0.01f, 0.0f, 2.0f, 0.0f, 2.0f)
                horizontalLineToRelative(3.0f)
                verticalLineToRelative(2.99f)
                curveToRelative(0.01f, 0.01f, 2.0f, 0.0f, 2.0f, 0.0f)
                lineTo(20.0f, 6.0f)
                horizontalLineToRelative(3.0f)
                lineTo(23.0f, 4.0f)
                horizontalLineToRelative(-3.0f)
                close()
            }
        }
        return _addPhotoAlternate!!
    }

private var _addPhotoAlternate: ImageVector? = null
