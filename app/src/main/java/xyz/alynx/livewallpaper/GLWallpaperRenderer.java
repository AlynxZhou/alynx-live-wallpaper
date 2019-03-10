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
import android.media.MediaPlayer;
import android.opengl.GLES11Ext;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GLWallpaperRenderer implements GLSurfaceView.Renderer {
    private final static String TAG = "GLWallpaperRenderer";
    private Context context = null;
    private MediaPlayer mediaPlayer = null;
    private FloatBuffer vertices = null;
    private FloatBuffer texCoords = null;
    private IntBuffer indices = null;
    private int program = 0;
    private int[] textures = null;
    private SurfaceTexture surfaceTexture = null;
    private int screenWidth = 0;
    private int screenHeight = 0;
    private int videoWidth = 0;
    private int videoHeight = 0;
    private boolean dirty = false;
    private final int BYTES_PER_FLOAT = 4;
    private final int BYTES_PER_INT = 4;

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
    }

    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
        vertices.position(0);
        texCoords.position(0);
        indices.position(0);

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

        try {
            program = Utils.linkProgram(
                Utils.compileShaderResource(context, GLES30.GL_VERTEX_SHADER, R.raw.vertex),
                Utils.compileShaderResource(context, GLES30.GL_FRAGMENT_SHADER, R.raw.fragment)
            );
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
            // It seems we can only update texture within a EGL context.
            surfaceTexture.updateTexImage();
            dirty = false;
        }

        // No depth test for 2D video.
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glDepthMask(false);

        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);

        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0]);
        GLES30.glUseProgram(program);
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
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, 6, GLES30.GL_UNSIGNED_INT, indices);
        GLES30.glDisableVertexAttribArray(0);
        GLES30.glDisableVertexAttribArray(1);

        GLES30.glDepthMask(true);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
    }

    public void setSourceMediaPlayer(MediaPlayer mediaPlayer) {
        this.mediaPlayer = mediaPlayer;
        createSurfaceTexture();
    }

    public void setScreenSize(int width, int height) {
        if (screenWidth != width || screenHeight != height) {
            screenWidth = width;
            screenHeight = height;
            updateTexCoords();
        }
    }

    public void setVideoSize(int width, int height) {
        if (videoWidth != width || videoHeight != height) {
            videoWidth = width;
            videoHeight = height;
            createSurfaceTexture();
            updateTexCoords();
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
            public void onFrameAvailable(SurfaceTexture st) {
                dirty = true;
            }
        });
        if (mediaPlayer != null) {
            mediaPlayer.setSurface(new Surface(surfaceTexture));
        }
    }

    private void updateTexCoords() {
        // TODO: some video recorder save video in wrong resolution, and add a rotation metadata.
        // Need to detect and rotate them.
        // e.g. When record 1080x1920 video with a Samsung phone, it saves 1920x1080 in fact.
        // Like this: https://www.jacoduplessis.co.za/fix-samsung-video/
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
        float videoRatio = (float)videoWidth / videoHeight;
        float screenRatio = (float)screenWidth / screenHeight;
        if (videoRatio >= screenRatio) {
            float centerLength = (float)screenWidth / screenHeight * videoHeight;
            float leftEdge = (videoWidth - centerLength) / 2.0f;
            float rightEdge = leftEdge + centerLength;
            texCoordArray[0] = texCoordArray[2] = leftEdge / videoWidth;
            texCoordArray[4] = texCoordArray[6] = rightEdge / videoWidth;
        } else {
            float centerLength = (float)screenHeight / screenWidth * videoWidth;
            float topEdge = (videoHeight - centerLength) / 2.0f;
            float bottomEdge = topEdge + centerLength;
            texCoordArray[3] = texCoordArray[7] = topEdge / videoHeight;
            texCoordArray[1] = texCoordArray[5] = bottomEdge / videoHeight;
        }
        texCoords = ByteBuffer.allocateDirect(
            texCoordArray.length * BYTES_PER_FLOAT
        ).order(ByteOrder.nativeOrder()).asFloatBuffer();
        texCoords.put(texCoordArray).position(0);
    }
}
