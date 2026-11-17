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

public val Icons.Outlined.People: ImageVector
    get() {
        if (_people != null) {
            return _people!!
        }
        _people = materialIcon(name = "Outlined.People") {
            materialPath {
                moveTo(9.0f, 13.75f)
                curveToRelative(-2.34f, 0.0f, -7.0f, 1.17f, -7.0f, 3.5f)
                lineTo(2.0f, 19.0f)
                horizontalLineToRelative(14.0f)
                verticalLineToRelative(-1.75f)
                curveToRelative(0.0f, -2.33f, -4.66f, -3.5f, -7.0f, -3.5f)
                close()
                moveTo(4.34f, 17.0f)
                curveToRelative(0.84f, -0.58f, 2.87f, -1.25f, 4.66f, -1.25f)
                reflectiveCurveToRelative(3.82f, 0.67f, 4.66f, 1.25f)
                lineTo(4.34f, 17.0f)
                close()
                moveTo(9.0f, 12.0f)
                curveToRelative(1.93f, 0.0f, 3.5f, -1.57f, 3.5f, -3.5f)
                reflectiveCurveTo(10.93f, 5.0f, 9.0f, 5.0f)
                reflectiveCurveTo(5.5f, 6.57f, 5.5f, 8.5f)
                reflectiveCurveTo(7.07f, 12.0f, 9.0f, 12.0f)
                close()
                moveTo(9.0f, 7.0f)
                curveToRelative(0.83f, 0.0f, 1.5f, 0.67f, 1.5f, 1.5f)
                reflectiveCurveTo(9.83f, 10.0f, 9.0f, 10.0f)
                reflectiveCurveToRelative(-1.5f, -0.67f, -1.5f, -1.5f)
                reflectiveCurveTo(8.17f, 7.0f, 9.0f, 7.0f)
                close()
                moveTo(16.04f, 13.81f)
                curveToRelative(1.16f, 0.84f, 1.96f, 1.96f, 1.96f, 3.44f)
                lineTo(18.0f, 19.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(-1.75f)
                curveToRelative(0.0f, -2.02f, -3.5f, -3.17f, -5.96f, -3.44f)
                close()
                moveTo(15.0f, 12.0f)
                curveToRelative(1.93f, 0.0f, 3.5f, -1.57f, 3.5f, -3.5f)
                reflectiveCurveTo(16.93f, 5.0f, 15.0f, 5.0f)
                curveToRelative(-0.54f, 0.0f, -1.04f, 0.13f, -1.5f, 0.35f)
                curveToRelative(0.63f, 0.89f, 1.0f, 1.98f, 1.0f, 3.15f)
                reflectiveCurveToRelative(-0.37f, 2.26f, -1.0f, 3.15f)
                curveToRelative(0.46f, 0.22f, 0.96f, 0.35f, 1.5f, 0.35f)
                close()
            }
        }
        return _people!!
    }

private var _people: ImageVector? = null
