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

public val Icons.Outlined.WorkspacePremium: ImageVector
    get() {
        if (_workspacePremium != null) {
            return _workspacePremium!!
        }
        _workspacePremium = materialIcon(name = "Outlined.WorkspacePremium") {
            materialPath {
                moveTo(9.68f, 13.69f)
                lineTo(12.0f, 11.93f)
                lineToRelative(2.31f, 1.76f)
                lineToRelative(-0.88f, -2.85f)
                lineTo(15.75f, 9.0f)
                horizontalLineToRelative(-2.84f)
                lineTo(12.0f, 6.19f)
                lineTo(11.09f, 9.0f)
                horizontalLineTo(8.25f)
                lineToRelative(2.31f, 1.84f)
                lineTo(9.68f, 13.69f)
                close()
                moveTo(20.0f, 10.0f)
                curveToRelative(0.0f, -4.42f, -3.58f, -8.0f, -8.0f, -8.0f)
                reflectiveCurveToRelative(-8.0f, 3.58f, -8.0f, 8.0f)
                curveToRelative(0.0f, 2.03f, 0.76f, 3.87f, 2.0f, 5.28f)
                verticalLineTo(23.0f)
                lineToRelative(6.0f, -2.0f)
                lineToRelative(6.0f, 2.0f)
                verticalLineToRelative(-7.72f)
                curveTo(19.24f, 13.87f, 20.0f, 12.03f, 20.0f, 10.0f)
                close()
                moveTo(12.0f, 4.0f)
                curveToRelative(3.31f, 0.0f, 6.0f, 2.69f, 6.0f, 6.0f)
                reflectiveCurveToRelative(-2.69f, 6.0f, -6.0f, 6.0f)
                reflectiveCurveToRelative(-6.0f, -2.69f, -6.0f, -6.0f)
                reflectiveCurveTo(8.69f, 4.0f, 12.0f, 4.0f)
                close()
                moveTo(12.0f, 19.0f)
                lineToRelative(-4.0f, 1.02f)
                verticalLineToRelative(-3.1f)
                curveTo(9.18f, 17.6f, 10.54f, 18.0f, 12.0f, 18.0f)
                reflectiveCurveToRelative(2.82f, -0.4f, 4.0f, -1.08f)
                verticalLineToRelative(3.1f)
                lineTo(12.0f, 19.0f)
                close()
            }
        }
        return _workspacePremium!!
    }

private var _workspacePremium: ImageVector? = null
