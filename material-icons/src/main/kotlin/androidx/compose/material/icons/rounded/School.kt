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

package androidx.compose.material.icons.rounded

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

public val Icons.Rounded.School: ImageVector
    get() {
        if (_school != null) {
            return _school!!
        }
        _school = materialIcon(name = "Rounded.School") {
            materialPath {
                moveTo(5.0f, 13.18f)
                verticalLineToRelative(2.81f)
                curveToRelative(0.0f, 0.73f, 0.4f, 1.41f, 1.04f, 1.76f)
                lineToRelative(5.0f, 2.73f)
                curveToRelative(0.6f, 0.33f, 1.32f, 0.33f, 1.92f, 0.0f)
                lineToRelative(5.0f, -2.73f)
                curveToRelative(0.64f, -0.35f, 1.04f, -1.03f, 1.04f, -1.76f)
                verticalLineToRelative(-2.81f)
                lineToRelative(-6.04f, 3.3f)
                curveToRelative(-0.6f, 0.33f, -1.32f, 0.33f, -1.92f, 0.0f)
                lineTo(5.0f, 13.18f)
                close()
                moveTo(11.04f, 3.52f)
                lineToRelative(-8.43f, 4.6f)
                curveToRelative(-0.69f, 0.38f, -0.69f, 1.38f, 0.0f, 1.76f)
                lineToRelative(8.43f, 4.6f)
                curveToRelative(0.6f, 0.33f, 1.32f, 0.33f, 1.92f, 0.0f)
                lineTo(21.0f, 10.09f)
                lineTo(21.0f, 16.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                reflectiveCurveToRelative(1.0f, -0.45f, 1.0f, -1.0f)
                lineTo(23.0f, 9.59f)
                curveToRelative(0.0f, -0.37f, -0.2f, -0.7f, -0.52f, -0.88f)
                lineToRelative(-9.52f, -5.19f)
                curveToRelative(-0.6f, -0.32f, -1.32f, -0.32f, -1.92f, 0.0f)
                close()
            }
        }
        return _school!!
    }

private var _school: ImageVector? = null
