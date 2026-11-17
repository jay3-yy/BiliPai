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

public val Icons.Rounded.Whatshot: ImageVector
    get() {
        if (_whatshot != null) {
            return _whatshot!!
        }
        _whatshot = materialIcon(name = "Rounded.Whatshot") {
            materialPath {
                moveTo(17.09f, 4.56f)
                curveToRelative(-0.7f, -1.03f, -1.5f, -1.99f, -2.4f, -2.85f)
                curveToRelative(-0.35f, -0.34f, -0.94f, -0.02f, -0.84f, 0.46f)
                curveToRelative(0.19f, 0.94f, 0.39f, 2.18f, 0.39f, 3.29f)
                curveToRelative(0.0f, 2.06f, -1.35f, 3.73f, -3.41f, 3.73f)
                curveToRelative(-1.54f, 0.0f, -2.8f, -0.93f, -3.35f, -2.26f)
                curveToRelative(-0.1f, -0.2f, -0.14f, -0.32f, -0.2f, -0.54f)
                curveToRelative(-0.11f, -0.42f, -0.66f, -0.55f, -0.9f, -0.18f)
                curveToRelative(-0.18f, 0.27f, -0.35f, 0.54f, -0.51f, 0.83f)
                curveTo(4.68f, 9.08f, 4.0f, 11.46f, 4.0f, 14.0f)
                curveToRelative(0.0f, 4.42f, 3.58f, 8.0f, 8.0f, 8.0f)
                reflectiveCurveToRelative(8.0f, -3.58f, 8.0f, -8.0f)
                curveToRelative(0.0f, -3.49f, -1.08f, -6.73f, -2.91f, -9.44f)
                close()
                moveTo(11.71f, 19.0f)
                curveToRelative(-1.78f, 0.0f, -3.22f, -1.4f, -3.22f, -3.14f)
                curveToRelative(0.0f, -1.62f, 1.05f, -2.76f, 2.81f, -3.12f)
                curveToRelative(1.47f, -0.3f, 2.98f, -0.93f, 4.03f, -1.92f)
                curveToRelative(0.28f, -0.26f, 0.74f, -0.14f, 0.82f, 0.23f)
                curveToRelative(0.23f, 1.02f, 0.35f, 2.08f, 0.35f, 3.15f)
                curveToRelative(0.01f, 2.65f, -2.14f, 4.8f, -4.79f, 4.8f)
                close()
            }
        }
        return _whatshot!!
    }

private var _whatshot: ImageVector? = null
