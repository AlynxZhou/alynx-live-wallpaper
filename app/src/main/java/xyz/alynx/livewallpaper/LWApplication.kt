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

import android.app.Application
import android.content.Context
import android.graphics.*
import android.net.Uri
import org.json.*
import java.io.IOException
import java.util.*

class LWApplication : Application() {
    companion object {
        const val JSON_FILE_NAME = "data.json"
        private const val CURRENT_CARD_PREF = "currentWallpaperCard"

        const val OPTIONS_PREF = "options"
        const val SLIDE_WALLPAPER_KEY = "slideWallpaper"
        private const val INTERNAL_WALLPAPER_IMAGE_PATH = "wallpapers/fire-rain/fire-rain-512x384.webp"
        private const val INTERNAL_WALLPAPER_VIDEO_PATH = "wallpapers/fire-rain/fire-rain-720x720.mp4"

        private var cards: MutableList<WallpaperCard>? = null
        private var currentWallpaperCard: WallpaperCard? = null
        @JvmField
        var previewWallpaperCard: WallpaperCard? = null

        // INTERNAL WallpaperCard don't need to save.
        @JvmStatic
        @get:Throws(JSONException::class)
        val cardsJSONArray: JSONArray
            get() {
                val jsonArray = JSONArray()
                if (cards != null) {
                    for (card in cards!!) {
                        // INTERNAL WallpaperCard don't need to save.
                        if (card.type != WallpaperCard.Type.INTERNAL) {
                            jsonArray.put(card.toJSON())
                        }
                    }
                }
                return jsonArray
            }

        private fun saveWallpaperCardPreference(context: Context, wallpaperCard: WallpaperCard) {
            val pref = context.getSharedPreferences(CURRENT_CARD_PREF, MODE_PRIVATE)
            // Save to preference.
            val prefEditor = pref.edit()
            prefEditor.putString("name", wallpaperCard.name)
            prefEditor.putString("path", wallpaperCard.path)
            when (wallpaperCard.type) {
                WallpaperCard.Type.INTERNAL -> prefEditor.putString("type", "INTERNAL")
                WallpaperCard.Type.EXTERNAL -> prefEditor.putString("type", "EXTERNAL")
            }
            prefEditor.apply()
        }

        private fun loadWallpaperCardPreference(context: Context): WallpaperCard? {
            val pref = context.getSharedPreferences(CURRENT_CARD_PREF, MODE_PRIVATE)
            val name = pref.getString("name", null)
            val path = pref.getString("path", null)
            if (name == null || path == null) {
                return null
            }
            var type = WallpaperCard.Type.EXTERNAL
            val uri: Uri
            if (pref.getString("type", null) == "INTERNAL") {
                type = WallpaperCard.Type.INTERNAL
                uri = Uri.parse("file:///android_asset/$path")
            } else {
                uri = Uri.parse(path)
            }
            return WallpaperCard(name, path, uri, type, null)
        }

        private fun initCards(context: Context) {
            cards = ArrayList()
            var thumbnail: Bitmap? = null
            try {
                thumbnail = BitmapFactory.decodeStream(
                        context.assets.open(INTERNAL_WALLPAPER_IMAGE_PATH)
                )
            } catch (e: IOException) {
                e.printStackTrace()
            }
            cards!!.add(WallpaperCard(
                context.resources.getString(R.string.fire_rain),
                INTERNAL_WALLPAPER_VIDEO_PATH, Uri.parse(
                    "file:///android_asset/$INTERNAL_WALLPAPER_VIDEO_PATH"
                ), WallpaperCard.Type.INTERNAL, thumbnail!!
            ))
        }

        @JvmStatic
        fun getCards(context: Context): MutableList<WallpaperCard> {
            if (cards == null) {
                initCards(context)
            }
            return cards!!
        }

        @JvmStatic
        fun getCurrentWallpaperCard(context: Context): WallpaperCard? {
            if (currentWallpaperCard == null) {
                currentWallpaperCard = loadWallpaperCardPreference(context)
            }
            return currentWallpaperCard
        }

        @JvmStatic
        fun setCurrentWallpaperCard(context: Context, wallpaperCard: WallpaperCard?) {
            currentWallpaperCard = wallpaperCard
            if (wallpaperCard != null) {
                saveWallpaperCardPreference(context, wallpaperCard)
            }
        }

        @JvmStatic
        fun isCurrentWallpaperCard(wallpaperCard: WallpaperCard): Boolean {
            // Only check variable, no SharedPreference.
            // If wallpaper is not this app, Preference should not be cleared,
            // only currentWallpaperCard is set to null.
            // Because when we getCurrentWallpaperCard(), it loads Preference,
            // and set currentWallpaperCard to non-null.
            // We want to detect whether service is selected
            // by checking whether currentWallpaperCard is null.
            // If we use getCurrentWallpaperCard(), we cannot archive this.
            return currentWallpaperCard != null && wallpaperCard == currentWallpaperCard
        }
    }
}
