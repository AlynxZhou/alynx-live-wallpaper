/*
 * Copyright 2019 Alynx Zhou
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package xyz.alynx.livewallpaper

import android.graphics.Bitmap
import android.net.Uri
import org.json.*

/**
 *
 * WallpaperCard: saving wallpaper info (name, path, type, thumbnail) to display or play.
 *
 */
class WallpaperCard internal constructor(
    @JvmField
    val name: String,
    @JvmField
    val path: String,
    @JvmField
    val uri: Uri,
    @JvmField
    val type: Type,
    @JvmField
    val thumbnail: Bitmap?
) {

    // It's hard to access file path with Android built-in file chooser.
    // So actually path is a path.
    var isValid = true
        private set

    // INTERNAL means this video is bundled into app assets.
    // So it cannot be removed.
    enum class Type {
        INTERNAL,
        EXTERNAL
    }

    /**
     * @param card WallpaperCard to compare.
     * @return boolean true for equal, false for not.
     *
     * Only compare path, other parts are not matter.
     */
    fun equals(card: WallpaperCard): Boolean {
        return path == card.path
    }

    fun isRemovable(): Boolean = type == Type.EXTERNAL
    fun isCurrent(): Boolean = LWApplication.isCurrentWallpaperCard(this)

    fun setInvalid() {
        isValid = false
    }

    @Throws(JSONException::class)
    fun toJSON(): JSONObject {
        val json = JSONObject()
        json.put("name", name)
        json.put("path", path)
        when (type) {
            Type.INTERNAL -> json.put("type", "INTERNAL")
            Type.EXTERNAL -> json.put("type", "EXTERNAL")
        }
        return json
    }

    companion object {
        @Suppress("unused")
        private val TAG = "WallpaperCard"
    }
}
