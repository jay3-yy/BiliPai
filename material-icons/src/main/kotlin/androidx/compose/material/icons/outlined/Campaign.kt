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

public val Icons.Outlined.Campaign: ImageVector
    get() {
        if (_campaign != null) {
            return _campaign!!
        }
        _campaign = materialIcon(name = "Outlined.Campaign") {
            materialPath {
                moveTo(18.0f, 11.0f)
                curveToRelative(0.0f, 0.67f, 0.0f, 1.33f, 0.0f, 2.0f)
                curveToRelative(1.2f, 0.0f, 2.76f, 0.0f, 4.0f, 0.0f)
                curveToRelative(0.0f, -0.67f, 0.0f, -1.33f, 0.0f, -2.0f)
                curveTo(20.76f, 11.0f, 19.2f, 11.0f, 18.0f, 11.0f)
                close()
            }
            materialPath {
                moveTo(16.0f, 17.61f)
                curveToRelative(0.96f, 0.71f, 2.21f, 1.65f, 3.2f, 2.39f)
                curveToRelative(0.4f, -0.53f, 0.8f, -1.07f, 1.2f, -1.6f)
                curveToRelative(-0.99f, -0.74f, -2.24f, -1.68f, -3.2f, -2.4f)
                curveTo(16.8f, 16.54f, 16.4f, 17.08f, 16.0f, 17.61f)
                close()
            }
            materialPath {
                moveTo(20.4f, 5.6f)
                curveTo(20.0f, 5.07f, 19.6f, 4.53f, 19.2f, 4.0f)
                curveToRelative(-0.99f, 0.74f, -2.24f, 1.68f, -3.2f, 2.4f)
                curveToRelative(0.4f, 0.53f, 0.8f, 1.07f, 1.2f, 1.6f)
                curveTo(18.16f, 7.28f, 19.41f, 6.35f, 20.4f, 5.6f)
                close()
            }
            materialPath {
                moveTo(4.0f, 9.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(2.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(1.0f)
                verticalLineToRelative(4.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineToRelative(1.0f)
                lineToRelative(5.0f, 3.0f)
                verticalLineTo(6.0f)
                lineTo(8.0f, 9.0f)
                horizontalLineTo(4.0f)
                close()
                moveTo(9.03f, 10.71f)
                lineTo(11.0f, 9.53f)
                verticalLineToRelative(4.94f)
                lineToRelative(-1.97f, -1.18f)
                lineTo(8.55f, 13.0f)
                horizontalLineTo(8.0f)
                horizontalLineTo(4.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(4.0f)
                horizontalLineToRelative(0.55f)
                lineTo(9.03f, 10.71f)
                close()
            }
            materialPath {
                moveTo(15.5f, 12.0f)
                curveToRelative(0.0f, -1.33f, -0.58f, -2.53f, -1.5f, -3.35f)
                verticalLineToRelative(6.69f)
                curveTo(14.92f, 14.53f, 15.5f, 13.33f, 15.5f, 12.0f)
                close()
            }
        }
        return _campaign!!
    }

private var _campaign: ImageVector? = null
