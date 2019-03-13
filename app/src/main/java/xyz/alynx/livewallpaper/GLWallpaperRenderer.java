/**
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

package xyz.alynx.livewallpaper;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.view.Surface;

import com.google.android.exoplayer2.SimpleExoPlayer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GLWallpaperRenderer implements GLSurfaceView.Renderer {
    private final static String TAG = "GLWallpaperRenderer";
    private final int BYTES_PER_FLOAT = 4;
    private final int BYTES_PER_INT = 4;
    private Context context = null;
    private FloatBuffer vertices = null;
    private FloatBuffer texCoords = null;
    private IntBuffer indices = null;
    private float[] mvp = null;
    private int program = 0;
    private int mvpLocation = 0;
    private int[] textures = null;
    private SurfaceTexture surfaceTexture = null;
    private int screenWidth = 0;
    private int screenHeight = 0;
    private int videoWidth = 0;
    private int videoHeight = 0;
    private int videoRotation = 0;
    private float xOffset = 0;
    private float yOffset = 0;
    private boolean dirty = false;

    public GLWallpaperRenderer(Context context) {
        this.context = context;

        // Those replaced glGenBuffers() and glBufferData().
        float[] vertexArray = {
            // x, y
            // bottom left
            -1.0f, -1.0f,
            // top left
            -1.0f, 1.0f,
            // bottom right
            1.0f, -1.0f,
            // top right
            1.0f, 1.0f
        };
        vertices = ByteBuffer.allocateDirect(
            vertexArray.length * BYTES_PER_FLOAT
        ).order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertices.put(vertexArray);

        float[] texCoordArray = {
            // u, v
            // bottom left
            0.0f, 1.0f,
            // top left
            0.0f, 0.0f,
            // bottom right
            1.0f, 1.0f,
            // top right
            1.0f, 0.0f
        };
        texCoords = ByteBuffer.allocateDirect(
            texCoordArray.length * BYTES_PER_FLOAT
        ).order(ByteOrder.nativeOrder()).asFloatBuffer();
        texCoords.put(texCoordArray);

        int[] indexArray = {
            0, 1, 2,
            3, 2, 1
        };
        indices = ByteBuffer.allocateDirect(
            indexArray.length * BYTES_PER_INT
        ).order(ByteOrder.nativeOrder()).asIntBuffer();
        indices.put(indexArray);

        textures = new int[1];

        mvp = new float[] {
            1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f
        };
    }

    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
        vertices.position(0);
        texCoords.position(0);
        indices.position(0);

        // No depth test for 2D video.
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glDepthMask(false);
        GLES30.glDisable(GLES30.GL_CULL_FACE);
        GLES30.glDisable(GLES30.GL_BLEND);

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glGenTextures(textures.length, textures, 0);
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0]);
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_MIN_FILTER,
            GLES30.GL_LINEAR
        );
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_MAG_FILTER,
            GLES30.GL_LINEAR
        );
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_WRAP_S,
            GLES30.GL_CLAMP_TO_EDGE
        );
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_WRAP_T,
            GLES30.GL_CLAMP_TO_EDGE
        );

        // Position is set in shader sources.
        GLES30.glVertexAttribPointer(
            0, 2, GLES30.GL_FLOAT,
            false, 0, vertices
        );
        GLES30.glVertexAttribPointer(
            1, 2, GLES30.GL_FLOAT,
            false, 0, texCoords
        );
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glEnableVertexAttribArray(1);

        try{
            program = Utils.linkProgram(
                Utils.compileShaderResource(context, GLES30.GL_VERTEX_SHADER, R.raw.vertex),
                Utils.compileShaderResource(context, GLES30.GL_FRAGMENT_SHADER, R.raw.fragment)
            );
            mvpLocation = GLES30.glGetUniformLocation(program, "mvp");
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl10, int width, int height) {
        GLES30.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl10) {
        if (surfaceTexture == null) {
            return;
        }

        if (dirty) {
            surfaceTexture.updateTexImage();
            dirty = false;
        }

        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);

        GLES30.glUseProgram(program);
        GLES30.glUniformMatrix4fv(mvpLocation, 1, false, mvp, 0);
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, 6, GLES30.GL_UNSIGNED_INT, indices);
    }

    public void setSourcePlayer(SimpleExoPlayer exoPlayer) {
        // Re-create SurfaceTexture when getting a new player.
        // Because maybe a new video is loaded.
        createSurfaceTexture();
        exoPlayer.setVideoSurface(new Surface(surfaceTexture));
    }

    public void setScreenSize(int width, int height) {
        if (screenWidth != width || screenHeight != height) {
            screenWidth = width;
            screenHeight = height;
            Utils.debug(TAG, String.format("Set screen size to %dx%d", screenWidth, screenHeight));
            updateMatrix();
        }
    }

    public void setVideoSizeAndRotation(int width, int height, int rotation) {
        // MediaPlayer already get right width and height, so we don't need to swap them.
        // Just record the rotation for frame display.
        if (videoWidth != width || videoHeight != height || videoRotation != rotation) {
            videoWidth = width;
            videoHeight = height;
            videoRotation = rotation;
            Utils.debug(TAG, String.format("Set video size to %dx%d", videoWidth, videoHeight));
            Utils.debug(TAG, String.format("Set video rotation to %d", videoRotation));
            updateMatrix();
        }
    }

    public void setOffset(float xOffset, float yOffset) {
        float maxXOffset = (1.0f - (
            (float)screenWidth / screenHeight) / ((float)videoWidth / videoHeight)
        ) / 2;
        float maxYOffset = (1.0f - (
            (float)screenHeight / screenWidth) / ((float)videoHeight / videoWidth)
        ) / 2;
        if (xOffset > maxXOffset) {
            xOffset = maxXOffset;
        }
        if (xOffset < -maxXOffset) {
            xOffset = -maxXOffset;
        }
        if (yOffset > maxYOffset) {
            yOffset = maxYOffset;
        }
        if (yOffset < -maxXOffset) {
            yOffset = -maxYOffset;
        }
        if (this.xOffset != xOffset || this.yOffset != yOffset) {
            this.xOffset = xOffset;
            this.yOffset = yOffset;
            Utils.debug(TAG, String.format("Set offset to %fx%f", this.xOffset, this.yOffset));
            updateMatrix();
        }
    }

    private void createSurfaceTexture() {
        if (surfaceTexture != null) {
            surfaceTexture.release();
        }
        surfaceTexture = new SurfaceTexture(textures[0]);
        surfaceTexture.setDefaultBufferSize(videoWidth, videoHeight);
        surfaceTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
            @Override
            public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                dirty = true;
                Utils.debug(TAG, "stamp: " + surfaceTexture.getTimestamp());
            }
        });
    }

    private void updateMatrix() {
        // Start with an identify matrix.
        for (int i = 0; i < 16; ++i) {
            mvp[i] = 0.0f;
        }
        mvp[0] = mvp[5] = mvp[10] = mvp[15] = 1.0f;
        // OpenGL model matrix: scaling, rotating, translating.
        float videoRatio = (float)videoWidth / videoHeight;
        float screenRatio = (float)screenWidth / screenHeight;
        if (videoRatio >= screenRatio) {
            Utils.debug(TAG, "X-cropping");
            // Treat video and screen width as 1, and compare width to scale.
            Matrix.scaleM(
                mvp, 0,
                ((float)videoWidth / videoHeight) / ((float)screenWidth / screenHeight),
                1, 1
            );
            // Some video recorder save video frames in direction differs from recoring,
            // and add a rotation metadata. Need to detect and rotate them.
            if (videoRotation % 360 != 0) {
                Matrix.rotateM(mvp, 0, -videoRotation, 0,0, 1);
            }
            Matrix.translateM(mvp, 0, xOffset, 0, 0);
        } else {
            Utils.debug(TAG, "Y-cropping");
            // Treat video and screen height as 1, and compare height to scale.
            Matrix.scaleM(
                mvp, 0, 1,
                ((float)videoHeight / videoWidth) / ((float)screenHeight / screenWidth), 1
            );
            // Some video recorder save video frames in direction differs from recoring,
            // and add a rotation metadata. Need to detect and rotate them.
            if (videoRotation % 360 != 0) {
                Matrix.rotateM(mvp, 0, -videoRotation, 0,0, 1);
            }
            Matrix.translateM(mvp, 0, 0, yOffset, 0);
        }
        // This is a 2D center crop, so we only need model matrix, no view and projection.
    }
}
