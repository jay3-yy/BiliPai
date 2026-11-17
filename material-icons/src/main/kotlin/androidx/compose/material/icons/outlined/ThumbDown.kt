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

public val Icons.Outlined.ThumbDown: ImageVector
    get() {
        if (_thumbDown != null) {
            return _thumbDown!!
        }
        _thumbDown = materialIcon(name = "Outlined.ThumbDown") {
            materialPath {
                moveTo(15.0f, 3.0f)
                lineTo(6.0f, 3.0f)
                curveToRelative(-0.83f, 0.0f, -1.54f, 0.5f, -1.84f, 1.22f)
                lineToRelative(-3.02f, 7.05f)
                curveToRelative(-0.09f, 0.23f, -0.14f, 0.47f, -0.14f, 0.73f)
                verticalLineToRelative(2.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(6.31f)
                lineToRelative(-0.95f, 4.57f)
                lineToRelative(-0.03f, 0.32f)
                curveToRelative(0.0f, 0.41f, 0.17f, 0.79f, 0.44f, 1.06f)
                lineTo(9.83f, 23.0f)
                lineToRelative(6.59f, -6.59f)
                curveToRelative(0.36f, -0.36f, 0.58f, -0.86f, 0.58f, -1.41f)
                lineTo(17.0f, 5.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(15.0f, 15.0f)
                lineToRelative(-4.34f, 4.34f)
                lineTo(12.0f, 14.0f)
                lineTo(3.0f, 14.0f)
                verticalLineToRelative(-2.0f)
                lineToRelative(3.0f, -7.0f)
                horizontalLineToRelative(9.0f)
                verticalLineToRelative(10.0f)
                close()
                moveTo(19.0f, 3.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(12.0f)
                horizontalLineToRelative(-4.0f)
                close()
            }
        }
        return _thumbDown!!
    }

private var _thumbDown: ImageVector? = null
