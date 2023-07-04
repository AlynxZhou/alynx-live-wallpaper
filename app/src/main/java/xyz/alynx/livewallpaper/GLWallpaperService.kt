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

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.opengl.GLSurfaceView
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import android.widget.Toast
import androidx.annotation.UiThread
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.*
import xyz.alynx.livewallpaper.LWApplication.Companion.getCards
import xyz.alynx.livewallpaper.LWApplication.Companion.getCurrentWallpaperCard
import xyz.alynx.livewallpaper.Utils.debug
import java.io.IOException

/**
 * Generally, WallpaperService should not depend other parts of app.
 * Because sometimes (like phone restarted) service starts before Activity and Application.
 * So we cannot get data from them.
 *
 *
 * To solve this, it's better to store WallpaperCard
 * into storage, SharedPreferences is better than JSON because it's easier to get data.
 *
 *
 * So when we cannot get current WallpaperCard from LWApplication, we read SharedPreference,
 * then build a temp WallpaperCard (in fact only type and path matter for service, so we can
 * set thumbnail to null).
 *
 *
 * And when we get a current WallpaperCard, we save it to SharedPreference for further loading.
 */
@UiThread
class GLWallpaperService : WallpaperService() {
    inner class GLWallpaperEngine(private val context: Context) : Engine() {
        private var glSurfaceView: GLWallpaperSurfaceView? = null
        private var exoPlayer: ExoPlayer? = null
        private var videoSource: MediaSource? = null
        private var trackSelector: DefaultTrackSelector? = null
        private var wallpaperCard: WallpaperCard? = null
        private var oldWallpaperCard: WallpaperCard? = null
        private var renderer: GLWallpaperRenderer? = null
        private var allowSlide = false
        private var videoRotation = 0
        private var videoWidth = 0
        private var videoHeight = 0
        private var progress: Long = 0

        private inner class GLWallpaperSurfaceView(
            context: Context) : GLSurfaceView(context) {
            /**
             * This is a hack. Because Android Live Wallpaper only has a Surface.
             * So we create a GLSurfaceView, and when drawing to its Surface,
             * we replace it with WallpaperEngine's Surface.
             */
            override fun getHolder(): SurfaceHolder {
                return surfaceHolder
            }

            fun onDestroy() {
                super.onDetachedFromWindow()
            }
        }

        init {
            //            Log.d("AppLog", "ctor isUiThread:" + (Thread.currentThread().id == Looper.getMainLooper().thread.id))
            setTouchEventsEnabled(false)
        }

        @UiThread
        override fun onCreate(
            surfaceHolder: SurfaceHolder) {
            //            Log.d("AppLog", "onCreate isUiThread:" + (Thread.currentThread().id == Looper.getMainLooper().thread.id))
            super.onCreate(surfaceHolder)
            val pref = getSharedPreferences(
                LWApplication.OPTIONS_PREF, MODE_PRIVATE
            )
            allowSlide = pref.getBoolean(LWApplication.SLIDE_WALLPAPER_KEY, false)
        }

        @UiThread
        override fun onSurfaceCreated(
            surfaceHolder: SurfaceHolder) {
            //            Log.d("AppLog", "onSurfaceCreated isUiThread:" + (Thread.currentThread().id == Looper.getMainLooper().thread.id))
            super.onSurfaceCreated(surfaceHolder)
            createGLSurfaceView()
            val width = surfaceHolder.surfaceFrame.width()
            val height = surfaceHolder.surfaceFrame.height()
            renderer!!.setScreenSize(width, height)
            startPlayer()
        }

        @UiThread
        override fun onVisibilityChanged(visible: Boolean) {
            //            Log.d("AppLog", "onVisibilityChanged isUiThread:" + (Thread.currentThread().id == Looper.getMainLooper().thread.id))
            super.onVisibilityChanged(visible)
            if (renderer != null) {
                if (visible) {
                    val pref = getSharedPreferences(
                        LWApplication.OPTIONS_PREF, MODE_PRIVATE
                    )
                    allowSlide = pref.getBoolean(LWApplication.SLIDE_WALLPAPER_KEY, false)
                    glSurfaceView!!.onResume()
                    startPlayer()
                } else {
                    stopPlayer()
                    glSurfaceView!!.onPause()
                    // Prevent useless renderer calculating.
                    allowSlide = false
                }
            }
        }

        @UiThread
        override fun onOffsetsChanged(
            xOffset: Float, yOffset: Float,
            xOffsetStep: Float, yOffsetStep: Float,
            xPixelOffset: Int, yPixelOffset: Int
        ) {
            //            Log.d("AppLog", "onOffsetsChanged isUiThread:" + (Thread.currentThread().id == Looper.getMainLooper().thread.id))
            super.onOffsetsChanged(
                xOffset, yOffset, xOffsetStep,
                yOffsetStep, xPixelOffset, yPixelOffset
            )
            if (allowSlide && !isPreview) {
                renderer!!.setOffset(0.5f - xOffset, 0.5f - yOffset)
            }
        }

        @UiThread
        override fun onSurfaceChanged(
            surfaceHolder: SurfaceHolder, format: Int,
            width: Int, height: Int
        ) {
            //            Log.d("AppLog", "onSurfaceChanged isUiThread:" + (Thread.currentThread().id == Looper.getMainLooper().thread.id))
            super.onSurfaceChanged(surfaceHolder, format, width, height)
            renderer!!.setScreenSize(width, height)
        }

        @UiThread
        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
//            Log.d("AppLog", "onSurfaceDestroyed isUiThread:" + (Thread.currentThread().id == Looper.getMainLooper().thread.id))
            super.onSurfaceDestroyed(holder)
            stopPlayer()
            glSurfaceView!!.onDestroy()
        }

        @UiThread
        private fun createGLSurfaceView() {
            if (glSurfaceView != null) {
                glSurfaceView!!.onDestroy()
                glSurfaceView = null
            }
            glSurfaceView = GLWallpaperSurfaceView(context)
            val activityManager = getSystemService(
                ACTIVITY_SERVICE
            ) as ActivityManager
            val configInfo = activityManager.deviceConfigurationInfo
            renderer = when {
                configInfo.reqGlEsVersion >= 0x30000 -> {
                    debug(TAG, "Support GLESv3")
                    glSurfaceView!!.setEGLContextClientVersion(3)
                    GLES30WallpaperRenderer(context)
                }

                configInfo.reqGlEsVersion >= 0x20000 -> {
                    debug(TAG, "Fallback to GLESv2")
                    glSurfaceView!!.setEGLContextClientVersion(2)
                    GLES20WallpaperRenderer(context)
                }

                else -> {
                    Toast.makeText(context, R.string.gles_version, Toast.LENGTH_LONG).show()
                    throw RuntimeException("Needs GLESv2 or higher")
                }
            }
            glSurfaceView!!.preserveEGLContextOnPause = true
            glSurfaceView!!.setRenderer(renderer)
            // On demand render will lead to black screen.
            glSurfaceView!!.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }

        private fun checkWallpaperCardValid(): Boolean {
            val wallpaperCard = wallpaperCard ?: return false
            if (wallpaperCard.type === WallpaperCard.Type.INTERNAL) {
                return true
            }
            var res = true
            // Ask persistable permission here because AddCardTask may not have context.
            val uri = wallpaperCard.uri
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            try {
                val resolver = contentResolver
                val pfd = resolver.openFileDescriptor(
                    uri, "r"
                )
                if (pfd == null) {
                    res = false
                } else {
                    pfd.close()
                }
            } catch (e: IOException) {
                e.printStackTrace()
                res = false
            }
            return res
        }

        private fun loadWallpaperCard() {
            oldWallpaperCard = wallpaperCard
            wallpaperCard = if (isPreview) {
                LWApplication.previewWallpaperCard
            } else {
                getCurrentWallpaperCard(context)
            }
            if (!checkWallpaperCardValid()) {
                if (wallpaperCard != null) {
                    // File is removed by user.
                    Toast.makeText(context, R.string.invalid_path, Toast.LENGTH_LONG).show()
                    wallpaperCard!!.setInvalid()
                }
                // Load default wallpaper.
                val cards: List<WallpaperCard?> = getCards(context)
                if (cards.isNotEmpty() && cards[0] != null) {
                    wallpaperCard = cards[0]
                } else {
                    wallpaperCard = null
                    Toast.makeText(context, R.string.default_failed, Toast.LENGTH_LONG).show()
                    throw RuntimeException("Failed to fallback to internal wallpaper")
                }
            }
        }

        @get:Throws(IOException::class)
        private val videoMetadata: Unit
            get() {
                val mmr = MediaMetadataRetriever()
                when (wallpaperCard!!.type) {
                    WallpaperCard.Type.INTERNAL -> {
                        val afd = assets.openFd(wallpaperCard!!.path)
                        mmr.setDataSource(
                            afd.fileDescriptor,
                            afd.startOffset,
                            afd.declaredLength
                        )
                        afd.close()
                    }

                    WallpaperCard.Type.EXTERNAL -> mmr.setDataSource(context, wallpaperCard!!.uri)
                }
                val rotation = mmr.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
                )
                val width = mmr.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
                )
                val height = mmr.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
                )
                mmr.release()
                videoRotation = rotation!!.toInt()
                videoWidth = width!!.toInt()
                videoHeight = height!!.toInt()
            }

        @UiThread
        private fun startPlayer() {
            //            Log.d("AppLog", "startPlayer isUiThread:" + (Thread.currentThread().id == Looper.getMainLooper().thread.id))
            if (exoPlayer != null) {
                stopPlayer()
            }
            debug(TAG, "Player starting")
            loadWallpaperCard()
            if (wallpaperCard == null) {
                // gg
                return
            }
            try {
                videoMetadata
            } catch (e: IOException) {
                e.printStackTrace()
                // gg
                return
            }
            trackSelector = DefaultTrackSelector(context)
            exoPlayer = ExoPlayer.Builder(context).setTrackSelector(trackSelector!!).build()
            exoPlayer!!.volume = 0.0f
            // Disable audio decoder.
            val count = exoPlayer!!.rendererCount
            for (i in 0 until count) {
                if (exoPlayer!!.getRendererType(i) == C.TRACK_TYPE_AUDIO) {
                    trackSelector!!.setParameters(
                        trackSelector!!.buildUponParameters().setRendererDisabled(i, true)
                    )
                }
            }
            exoPlayer!!.repeatMode = Player.REPEAT_MODE_ALL
            val dataSourceFactory: DataSource.Factory = DefaultDataSource.Factory(context)
            //            (
            //                    context, Util.getUserAgent(context, "xyz.alynx.livewallpaper")
            //            )
            // ExoPlayer can load file:///android_asset/ uri correctly.
            videoSource =
                ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(wallpaperCard!!.uri))
            // Let we assume video has correct info in metadata, or user should fix it.
            renderer!!.setVideoSizeAndRotation(videoWidth, videoHeight, videoRotation)
            // This must be set after getting video info.
            renderer!!.setSourcePlayer(exoPlayer!!)
            exoPlayer!!.setMediaSource(videoSource!!)
            exoPlayer!!.prepare()
            // ExoPlayer's video size changed listener is buggy. Don't use it.
            // It give's width and height after rotation, but did not rotate frames.
            if (oldWallpaperCard != null &&
                oldWallpaperCard!! == wallpaperCard!!) {
                exoPlayer!!.seekTo(progress)
            }
            exoPlayer!!.playWhenReady = true
        }

        @UiThread
        private fun stopPlayer() {
//            Log.d("AppLog", "stopPlayer isUiThread:" + (Thread.currentThread().id == Looper.getMainLooper().thread.id))
            if (exoPlayer != null) {
                if (exoPlayer!!.playWhenReady) {
                    debug(TAG, "Player stopping")
                    exoPlayer!!.playWhenReady = false
                    progress = exoPlayer!!.currentPosition
                    exoPlayer!!.stop()
                }
                exoPlayer!!.release()
                exoPlayer = null
            }
            videoSource = null
            trackSelector = null
        }

    }

    @UiThread
    override fun onCreateEngine(): Engine {
        //        Log.d("AppLog", "onCreateEngine isUiThread:" + (Thread.currentThread().id == Looper.getMainLooper().thread.id))
        return GLWallpaperEngine(this)
    }

    companion object {
        private const val TAG = "GLWallpaperEngine"
    }

}
