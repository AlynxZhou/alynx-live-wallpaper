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
import org.json.JSONException
import org.json.JSONObject
import xyz.alynx.livewallpaper.LWApplication.Companion.isCurrentWallpaperCard

/**
 *
 * WallpaperCard: saving wallpaper info (name, path, type, thumbnail) to display or play.
 *
 */
class WallpaperCard internal constructor(
        path: String,
        uri: Uri,
        val type: Type,
        val thumbnail: Bitmap?
) {
    var name: String? = null
        private set

    // It's hard to access file path with Android built-in file chooser.
    // So actually path is a path.
    var path: String = path
        private set
    var uri: Uri = uri
        private set
    var isValid = true
        private set

    // INTERNAL means this video is bundled into app assets.
    // So it cannot be removed.
    enum class Type {
        INTERNAL, EXTERNAL
    }


    /**
     * @return boolean true for removable, false for not.
     *
     * Only EXTERNAL wallpaper can be removed.
     */
    val isRemovable: Boolean
        get() = type == Type.EXTERNAL

    /**
     * @return boolean true for current, false for not.
     *
     * Compare this to LWApplication's currentWallpaperCard.
     */
    val isCurrent: Boolean
        get() = isCurrentWallpaperCard(this)

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

    /**
     * Only compare path, other parts are not matter.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WallpaperCard) return false
        if (path != other.path) return false
        return true
    }

    override fun hashCode(): Int {
        return path.hashCode()
    }
}
