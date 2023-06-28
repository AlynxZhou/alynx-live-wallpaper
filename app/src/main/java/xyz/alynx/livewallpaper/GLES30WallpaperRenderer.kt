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

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.Matrix
import android.view.Surface
import com.google.android.exoplayer2.*
import xyz.alynx.livewallpaper.Utils.compileShaderResourceGLES30
import xyz.alynx.livewallpaper.Utils.debug
import xyz.alynx.livewallpaper.Utils.linkProgramGLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

internal class GLES30WallpaperRenderer(context: Context) : GLWallpaperRenderer(context) {
    private val vertices: FloatBuffer
    private val texCoords: FloatBuffer
    private val indices: IntBuffer
    private val buffers: IntArray
    private val vertexArrays: IntArray
    private val textures: IntArray
    private val mvp: FloatArray
    private var program = 0
    private var mvpLocation = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var screenWidth = 0
    private var screenHeight = 0
    private var videoWidth = 0
    private var videoHeight = 0
    private var videoRotation = 0
    private var xOffset = 0f
    private var yOffset = 0f
    private var maxXOffset = 0f
    private var maxYOffset = 0f

    // Fix bug like https://stackoverflow.com/questions/14185661/surfacetexture-onframeavailablelistener-stops-being-called
    private var updatedFrame: Long = 0
    private var renderedFrame: Long = 0
    override fun onSurfaceCreated(gl10: GL10, eglConfig: EGLConfig) {
        // No depth test for 2D video.
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(false)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glGenTextures(textures.size, textures, 0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
        GLES30.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES30.GL_TEXTURE_MIN_FILTER,
                GLES30.GL_LINEAR
        )
        GLES30.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES30.GL_TEXTURE_MAG_FILTER,
                GLES30.GL_LINEAR
        )
        GLES30.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES30.GL_TEXTURE_WRAP_S,
                GLES30.GL_CLAMP_TO_EDGE
        )
        GLES30.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES30.GL_TEXTURE_WRAP_T,
                GLES30.GL_CLAMP_TO_EDGE
        )
        program = linkProgramGLES30(
                compileShaderResourceGLES30(
                        context, GLES30.GL_VERTEX_SHADER, R.raw.vertex_30
                ),
                compileShaderResourceGLES30(
                        context, GLES30.GL_FRAGMENT_SHADER, R.raw.fragment_30
                )
        )
        mvpLocation = GLES30.glGetUniformLocation(program, "mvp")
        GLES30.glGenBuffers(buffers.size, buffers, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffers[0])
        GLES30.glBufferData(
                GLES30.GL_ARRAY_BUFFER, vertices.capacity() * BYTES_PER_FLOAT,
                vertices, GLES30.GL_STATIC_DRAW
        )
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffers[1])
        GLES30.glBufferData(
                GLES30.GL_ARRAY_BUFFER, texCoords.capacity() * BYTES_PER_FLOAT,
                texCoords, GLES30.GL_STATIC_DRAW
        )
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, buffers[2])
        GLES30.glBufferData(
                GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.capacity() * BYTES_PER_INT,
                indices, GLES30.GL_STATIC_DRAW
        )
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)

        // Locations are set in shader sources.
        GLES30.glGenVertexArrays(vertexArrays.size, vertexArrays, 0)
        GLES30.glBindVertexArray(vertexArrays[0])
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffers[0])
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(
                0, 2, GLES30.GL_FLOAT, false, 2 * BYTES_PER_FLOAT, 0
        )
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffers[1])
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(
                1, 2, GLES30.GL_FLOAT, false, 2 * BYTES_PER_FLOAT, 0
        )
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, buffers[2])
        GLES30.glBindVertexArray(0)
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
    }

    override fun onSurfaceChanged(gl10: GL10, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl10: GL10) {
        if (surfaceTexture == null) {
            return
        }
        if (renderedFrame < updatedFrame) {
            surfaceTexture!!.updateTexImage()
            ++renderedFrame
            // Utils.debug(
            //     TAG, "renderedFrame: " + renderedFrame + " updatedFrame: " + updatedFrame
            // );
        }
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(mvpLocation, 1, false, mvp, 0)
        GLES30.glBindVertexArray(vertexArrays[0])
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, 6, GLES30.GL_UNSIGNED_INT, 0)
        GLES30.glBindVertexArray(0)
        GLES30.glUseProgram(0)
    }

    override fun setSourcePlayer(exoPlayer: ExoPlayer) {
        // Re-create SurfaceTexture when getting a new player.
        // Because maybe a new video is loaded.
        createSurfaceTexture()
        exoPlayer.setVideoSurface(Surface(surfaceTexture))
    }

    override fun setScreenSize(width: Int, height: Int) {
        if (screenWidth != width || screenHeight != height) {
            screenWidth = width
            screenHeight = height
            debug(TAG, String.format(
                    Locale.US, "Set screen size to %dx%d", screenWidth, screenHeight
            ))
            maxXOffset = (1.0f - screenWidth.toFloat() / screenHeight / (videoWidth.toFloat() / videoHeight)) / 2
            maxYOffset = (1.0f - screenHeight.toFloat() / screenWidth / (videoHeight.toFloat() / videoWidth)) / 2
            updateMatrix()
        }
    }

    override fun setVideoSizeAndRotation(width: Int, height: Int, rotation: Int) {
        // MediaMetadataRetriever always give us raw width and height and won't rotate them.
        // So we rotate them by ourselves.
        var newWidth = width
        var newHeight = height
        if (rotation % 180 != 0) {
            val swap = newWidth
            newWidth = newHeight
            newHeight = swap
        }
        if (videoWidth != newWidth || videoHeight != newHeight || videoRotation != rotation) {
            videoWidth = newWidth
            videoHeight = newHeight
            videoRotation = rotation
            debug(TAG, String.format(
                    Locale.US, "Set video size to %dx%d", videoWidth, videoHeight
            ))
            debug(TAG, String.format(
                    Locale.US, "Set video rotation to %d", videoRotation
            ))
            maxXOffset = (1.0f - screenWidth.toFloat() / screenHeight / (videoWidth.toFloat() / videoHeight)) / 2
            maxYOffset = (1.0f - screenHeight.toFloat() / screenWidth / (videoHeight.toFloat() / videoWidth)) / 2
            updateMatrix()
        }
    }

    override fun setOffset(xOffset: Float, yOffset: Float) {
        var newXOffset = xOffset
        var newYOffset = yOffset
        if (newXOffset > maxXOffset) {
            newXOffset = maxXOffset
        }
        if (newXOffset < -maxXOffset) {
            newXOffset = -maxXOffset
        }
        if (newYOffset > maxYOffset) {
            newYOffset = maxYOffset
        }
        if (newYOffset < -maxXOffset) {
            newYOffset = -maxYOffset
        }
        if (this.xOffset != newXOffset || this.yOffset != newYOffset) {
            this.xOffset = newXOffset
            this.yOffset = newYOffset
            debug(TAG, String.format(
                    Locale.US, "Set offset to %fx%f", this.xOffset, this.yOffset
            ))
            updateMatrix()
        }
    }

    private fun createSurfaceTexture() {
        if (surfaceTexture != null) {
            surfaceTexture!!.release()
            surfaceTexture = null
        }
        updatedFrame = 0
        renderedFrame = 0
        surfaceTexture = SurfaceTexture(textures[0])
        surfaceTexture!!.setDefaultBufferSize(videoWidth, videoHeight)
        surfaceTexture!!.setOnFrameAvailableListener { ++updatedFrame }
    }

    private fun updateMatrix() {
        // Players are buggy and unclear, so we do crop by ourselves.
        // Start with an identify matrix.
        for (i in 0..15) {
            mvp[i] = 0.0f
        }
        mvp[15] = 1.0f
        mvp[10] = mvp[15]
        mvp[5] = mvp[10]
        mvp[0] = mvp[5]
        // OpenGL model matrix: scaling, rotating, translating.
        val videoRatio = videoWidth.toFloat() / videoHeight
        val screenRatio = screenWidth.toFloat() / screenHeight
        if (videoRatio >= screenRatio) {
            debug(TAG, "X-cropping")
            // Treat video and screen width as 1, and compare width to scale.
            Matrix.scaleM(
                    mvp, 0,
                    videoWidth.toFloat() / videoHeight / (screenWidth.toFloat() / screenHeight), 1f, 1f)
            // Some video recorder save video frames in direction differs from recoring,
            // and add a rotation metadata. Need to detect and rotate them.
            if (videoRotation % 360 != 0) {
                Matrix.rotateM(mvp, 0, -videoRotation.toFloat(), 0f, 0f, 1f)
            }
            Matrix.translateM(mvp, 0, xOffset, 0f, 0f)
        } else {
            debug(TAG, "Y-cropping")
            // Treat video and screen height as 1, and compare height to scale.
            Matrix.scaleM(
                    mvp, 0, 1f,
                    videoHeight.toFloat() / videoWidth / (screenHeight.toFloat() / screenWidth), 1f)
            // Some video recorder save video frames in direction differs from recoring,
            // and add a rotation metadata. Need to detect and rotate them.
            if (videoRotation % 360 != 0) {
                Matrix.rotateM(mvp, 0, -videoRotation.toFloat(), 0f, 0f, 1f)
            }
            Matrix.translateM(mvp, 0, 0f, yOffset, 0f)
        }
        // This is a 2D center crop, so we only need model matrix, no view and projection.
    }

    companion object {
        private const val TAG = "GLES30WallpaperRenderer"
        private const val BYTES_PER_FLOAT = 4
        private const val BYTES_PER_INT = 4
    }

    init {

        // Those replaced glGenBuffers() and glBufferData().
        val vertexArray = floatArrayOf( // x, y
                // bottom left
                -1.0f, -1.0f,  // top left
                -1.0f, 1.0f,  // bottom right
                1.0f, -1.0f,  // top right
                1.0f, 1.0f
        )
        vertices = ByteBuffer.allocateDirect(
                vertexArray.size * BYTES_PER_FLOAT
        ).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vertices.put(vertexArray).position(0)
        val texCoordArray = floatArrayOf( // u, v
                // bottom left
                0.0f, 1.0f,  // top left
                0.0f, 0.0f,  // bottom right
                1.0f, 1.0f,  // top right
                1.0f, 0.0f
        )
        texCoords = ByteBuffer.allocateDirect(
                texCoordArray.size * BYTES_PER_FLOAT
        ).order(ByteOrder.nativeOrder()).asFloatBuffer()
        texCoords.put(texCoordArray).position(0)
        val indexArray = intArrayOf(
                0, 1, 2,
                3, 2, 1
        )
        indices = ByteBuffer.allocateDirect(
                indexArray.size * BYTES_PER_INT
        ).order(ByteOrder.nativeOrder()).asIntBuffer()
        indices.put(indexArray).position(0)
        vertexArrays = IntArray(1)
        buffers = IntArray(3)
        textures = IntArray(1)
        mvp = floatArrayOf(
                1.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 1.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f
        )
    }
}
