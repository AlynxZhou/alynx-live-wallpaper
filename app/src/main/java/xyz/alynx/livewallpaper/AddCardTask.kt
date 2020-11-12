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

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import java.lang.ref.WeakReference

class AddCardTask(activity: AppCompatActivity, private val listener: AddCardTaskListener) : AsyncTask<String?, Void?, WallpaperCard?>() {
    private val activityRef: WeakReference<AppCompatActivity?> = WeakReference(activity)
    private var message: String? = null

    interface AddCardTaskListener {
        fun onPreExecute(message: String?)
        fun onPostExecute(message: String?, card: WallpaperCard)
        fun onCancelled(message: String?)
    }

    override fun onPreExecute() {
        super.onPreExecute()
        listener.onPreExecute(message)
    }

    override fun doInBackground(vararg strings: String?): WallpaperCard? {
        var name = strings[0]!!
        if (name.length > 30) {
            name = name.substring(0, 30)
        }
        val path = strings[1]
        val uri = Uri.parse(path)
        try {
            if (activityRef.get() != null) {
                // Ask for persistable permission.
                activityRef.get()!!.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            if (activityRef.get() != null) {
                message = String.format(
                        activityRef.get()!!.resources.getString(R.string.removed_invalid_card),
                        name
                )
            }
            cancel(true)
            return null
        }
        var thumbnail: Bitmap? = null
        if (activityRef.get() != null) {
            thumbnail = Utils.createVideoThumbnailFromUri(
                    activityRef.get()!!.applicationContext, uri
            )
        }
        if (thumbnail == null) {
            if (activityRef.get() != null) {
                message = String.format(
                        activityRef.get()!!.resources.getString(R.string.no_thumbnail),
                        name
                )
            }
            cancel(true)
            return null
        }
        return WallpaperCard(
                uri.toString(), uri, WallpaperCard.Type.EXTERNAL, thumbnail
        )
    }

    override fun onCancelled() {
        super.onCancelled()
        listener.onCancelled(message)
    }

    override fun onPostExecute(card: WallpaperCard?) {
        super.onPostExecute(card)
        if (card == null) {
            return
        }
        listener.onPostExecute(message, card)
    }
}
