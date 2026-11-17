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

package androidx.compose.material.icons.filled

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

public val Icons.Filled.Report: ImageVector
    get() {
        if (_report != null) {
            return _report!!
        }
        _report = materialIcon(name = "Filled.Report") {
            materialPath {
                moveTo(15.73f, 3.0f)
                lineTo(8.27f, 3.0f)
                lineTo(3.0f, 8.27f)
                verticalLineToRelative(7.46f)
                lineTo(8.27f, 21.0f)
                horizontalLineToRelative(7.46f)
                lineTo(21.0f, 15.73f)
                lineTo(21.0f, 8.27f)
                lineTo(15.73f, 3.0f)
                close()
                moveTo(12.0f, 17.3f)
                curveToRelative(-0.72f, 0.0f, -1.3f, -0.58f, -1.3f, -1.3f)
                curveToRelative(0.0f, -0.72f, 0.58f, -1.3f, 1.3f, -1.3f)
                curveToRelative(0.72f, 0.0f, 1.3f, 0.58f, 1.3f, 1.3f)
                curveToRelative(0.0f, 0.72f, -0.58f, 1.3f, -1.3f, 1.3f)
                close()
                moveTo(13.0f, 13.0f)
                horizontalLineToRelative(-2.0f)
                lineTo(11.0f, 7.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(6.0f)
                close()
            }
        }
        return _report!!
    }

private var _report: ImageVector? = null
