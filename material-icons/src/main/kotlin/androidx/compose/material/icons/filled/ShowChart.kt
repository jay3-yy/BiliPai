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
import kotlin.Deprecated

@Deprecated(
    "Use the AutoMirrored version at Icons.AutoMirrored.Filled.ShowChart",
    ReplaceWith( "Icons.AutoMirrored.Filled.ShowChart",
            "androidx.compose.material.icons.automirrored.filled.ShowChart"),
)
public val Icons.Filled.ShowChart: ImageVector
    get() {
        if (_showChart != null) {
            return _showChart!!
        }
        _showChart = materialIcon(name = "Filled.ShowChart") {
            materialPath {
                moveTo(3.5f, 18.49f)
                lineToRelative(6.0f, -6.01f)
                lineToRelative(4.0f, 4.0f)
                lineTo(22.0f, 6.92f)
                lineToRelative(-1.41f, -1.41f)
                lineToRelative(-7.09f, 7.97f)
                lineToRelative(-4.0f, -4.0f)
                lineTo(2.0f, 16.99f)
                close()
            }
        }
        return _showChart!!
    }

private var _showChart: ImageVector? = null
