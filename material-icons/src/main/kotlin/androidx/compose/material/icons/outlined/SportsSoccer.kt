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

public val Icons.Outlined.SportsSoccer: ImageVector
    get() {
        if (_sportsSoccer != null) {
            return _sportsSoccer!!
        }
        _sportsSoccer = materialIcon(name = "Outlined.SportsSoccer") {
            materialPath {
                moveTo(12.0f, 2.0f)
                curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                curveToRelative(0.0f, 5.52f, 4.48f, 10.0f, 10.0f, 10.0f)
                reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
                curveTo(22.0f, 6.48f, 17.52f, 2.0f, 12.0f, 2.0f)
                close()
                moveTo(13.0f, 5.3f)
                lineToRelative(1.35f, -0.95f)
                curveToRelative(1.82f, 0.56f, 3.37f, 1.76f, 4.38f, 3.34f)
                lineToRelative(-0.39f, 1.34f)
                lineToRelative(-1.35f, 0.46f)
                lineTo(13.0f, 6.7f)
                verticalLineTo(5.3f)
                close()
                moveTo(9.65f, 4.35f)
                lineTo(11.0f, 5.3f)
                verticalLineToRelative(1.4f)
                lineTo(7.01f, 9.49f)
                lineTo(5.66f, 9.03f)
                lineTo(5.27f, 7.69f)
                curveTo(6.28f, 6.12f, 7.83f, 4.92f, 9.65f, 4.35f)
                close()
                moveTo(7.08f, 17.11f)
                lineToRelative(-1.14f, 0.1f)
                curveTo(4.73f, 15.81f, 4.0f, 13.99f, 4.0f, 12.0f)
                curveToRelative(0.0f, -0.12f, 0.01f, -0.23f, 0.02f, -0.35f)
                lineToRelative(1.0f, -0.73f)
                lineTo(6.4f, 11.4f)
                lineToRelative(1.46f, 4.34f)
                lineTo(7.08f, 17.11f)
                close()
                moveTo(14.5f, 19.59f)
                curveTo(13.71f, 19.85f, 12.87f, 20.0f, 12.0f, 20.0f)
                reflectiveCurveToRelative(-1.71f, -0.15f, -2.5f, -0.41f)
                lineToRelative(-0.69f, -1.49f)
                lineTo(9.45f, 17.0f)
                horizontalLineToRelative(5.11f)
                lineToRelative(0.64f, 1.11f)
                lineTo(14.5f, 19.59f)
                close()
                moveTo(14.27f, 15.0f)
                horizontalLineTo(9.73f)
                lineToRelative(-1.35f, -4.02f)
                lineTo(12.0f, 8.44f)
                lineToRelative(3.63f, 2.54f)
                lineTo(14.27f, 15.0f)
                close()
                moveTo(18.06f, 17.21f)
                lineToRelative(-1.14f, -0.1f)
                lineToRelative(-0.79f, -1.37f)
                lineToRelative(1.46f, -4.34f)
                lineToRelative(1.39f, -0.47f)
                lineToRelative(1.0f, 0.73f)
                curveTo(19.99f, 11.77f, 20.0f, 11.88f, 20.0f, 12.0f)
                curveTo(20.0f, 13.99f, 19.27f, 15.81f, 18.06f, 17.21f)
                close()
            }
        }
        return _sportsSoccer!!
    }

private var _sportsSoccer: ImageVector? = null
