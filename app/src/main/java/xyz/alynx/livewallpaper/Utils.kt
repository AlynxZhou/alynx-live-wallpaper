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

import android.app.WallpaperManager
import android.content.*
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.opengl.GLES20
import android.opengl.GLES30
import android.util.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import kotlin.math.max
import kotlin.math.roundToInt

internal object Utils {
    /**
     * createVideoThumbnailFromUri
     * @param context Activity context or application context.
     * @param uri Video uri.
     * @return Bitmap thumbnail
     *
     * Hacked from ThumbnailUtils.createVideoThumbnail()'s code.
     */
    @JvmStatic
    fun createVideoThumbnailFromUri(
        context: Context,
        uri: Uri
    ): Bitmap? {
        var bitmap: Bitmap? = null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            bitmap = retriever.getFrameAtTime(-1)
        } catch (e: RuntimeException) {
            // Assume this is a corrupt video file
            e.printStackTrace()
        } // Assume this is a corrupt video file.
        finally {
            try {
                retriever.release()
            } catch (e: RuntimeException) {
                // Ignore failures while cleaning up.
                e.printStackTrace()
            }
        }
        if (bitmap == null) {
            return null
        }
        // Scale down the bitmap if it's too large.
        val width = bitmap.width
        val height = bitmap.height
        val max = max(width, height)
        if (max > 512) {
            val scale = 512f / max
            val w = (scale * width).roundToInt()
            val h = (scale * height).roundToInt()
            bitmap = Bitmap.createScaledBitmap(bitmap, w, h, true)
        }
        return bitmap
    }

    fun prepareSetCurrentAsLiveWallpaperIntent(): Intent {
        val liveWallpaperIntent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        val p = GLWallpaperService::class.java.`package`!!.name
        val c = GLWallpaperService::class.java.canonicalName!!
        liveWallpaperIntent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(p, c))
        return liveWallpaperIntent
    }

    @JvmStatic
    @Throws(RuntimeException::class)
    fun compileShaderResourceGLES30(
        context: Context,
        shaderType: Int,
        shaderRes: Int
    ): Int {
        val inputStream = context.resources.openRawResource(shaderRes)
        val bufferedReader = BufferedReader(InputStreamReader(inputStream))
        var line: String?
        val stringBuilder = StringBuilder()
        try {
            while (bufferedReader.readLine().also { line = it } != null) {
                stringBuilder.append(line)
                stringBuilder.append('\n')
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return 0
        }
        val shaderSource = stringBuilder.toString()
        val shader = GLES30.glCreateShader(shaderType)
        if (shader == 0) {
            throw RuntimeException("Failed to create shader")
        }
        GLES30.glShaderSource(shader, shaderSource)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw RuntimeException(log)
        }
        return shader
    }

    @JvmStatic
    @Throws(RuntimeException::class)
    fun linkProgramGLES30(
        vertShader: Int,
        fragShader: Int
    ): Int {
        val program = GLES30.glCreateProgram()
        if (program == 0) {
            throw RuntimeException("Failed to create program")
        }
        GLES30.glAttachShader(program, vertShader)
        GLES30.glAttachShader(program, fragShader)
        GLES30.glLinkProgram(program)
        val status = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            throw RuntimeException(log)
        }
        return program
    }

    @JvmStatic
    @Throws(RuntimeException::class)
    fun compileShaderResourceGLES20(
        context: Context,
        shaderType: Int,
        shaderRes: Int
    ): Int {
        val inputStream = context.resources.openRawResource(shaderRes)
        val bufferedReader = BufferedReader(InputStreamReader(inputStream))
        var line: String?
        val stringBuilder = StringBuilder()
        try {
            while (bufferedReader.readLine().also { line = it } != null) {
                stringBuilder.append(line)
                stringBuilder.append('\n')
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return 0
        }
        val shaderSource = stringBuilder.toString()
        val shader = GLES20.glCreateShader(shaderType)
        if (shader == 0) {
            throw RuntimeException("Failed to create shader")
        }
        GLES20.glShaderSource(shader, shaderSource)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw RuntimeException(log)
        }
        return shader
    }

    @JvmStatic
    @Throws(RuntimeException::class)
    fun linkProgramGLES20(
        vertShader: Int,
        fragShader: Int
    ): Int {
        val program = GLES20.glCreateProgram()
        if (program == 0) {
            throw RuntimeException("Failed to create program")
        }
        GLES20.glAttachShader(program, vertShader)
        GLES20.glAttachShader(program, fragShader)
        GLES20.glLinkProgram(program)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            throw RuntimeException(log)
        }
        return program
    }

    @JvmStatic
    fun debug(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }
}
