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
import android.opengl.Matrix;
import android.view.Surface;
import android.widget.Toast;

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
    private FloatBuffer mvp = null;
    private int program = 0;
    private int mvpLocation = 0;
    private int[] textures = null;
    private SurfaceTexture surfaceTexture = null;
    private int screenWidth = 0;
    private int screenHeight = 0;
    private int videoWidth = 0;
    private int videoHeight = 0;
    private int videoRotation = 0;
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

        float[] mvpArray = {
            1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f
        };
        mvp = ByteBuffer.allocateDirect(
            mvpArray.length * BYTES_PER_FLOAT
        ).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mvp.put(mvpArray);
    }

    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
        vertices.position(0);
        texCoords.position(0);
        indices.position(0);
        mvp.position(0);

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

        try{
            program = Utils.linkProgram(
                Utils.compileShaderResource(context, GLES30.GL_VERTEX_SHADER, R.raw.vertex),
                Utils.compileShaderResource(context, GLES30.GL_FRAGMENT_SHADER, R.raw.fragment)
            );
            mvpLocation = GLES30.glGetUniformLocation(program, "mvp");
        } catch (RuntimeException e) {
            e.printStackTrace();
            Toast.makeText(context, R.string.gl_runtime_error, Toast.LENGTH_LONG).show();
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

        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);

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
        GLES30.glUniformMatrix4fv(mvpLocation, 1, false, mvp);
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, 6, GLES30.GL_UNSIGNED_INT, indices);
        GLES30.glDisableVertexAttribArray(0);
        GLES30.glDisableVertexAttribArray(1);
    }

    public void setSourceMediaPlayer(MediaPlayer mediaPlayer) {
        this.mediaPlayer = mediaPlayer;
        createSurfaceTexture();
    }

    public void setScreenSize(int width, int height) {
        if (screenWidth != width || screenHeight != height) {
            screenWidth = width;
            screenHeight = height;
            updateMatrix();
        }
    }

    public void setVideoSizeAndRotation(int width, int height, int rotation) {
        // MediaPlayer already get right width and height, so we don't need to swap them.
        // Just record the rotation for frame display.
        videoRotation = rotation;
        if (videoWidth != width || videoHeight != height) {
            videoWidth = width;
            videoHeight = height;
            createSurfaceTexture();
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
            public void onFrameAvailable(SurfaceTexture st) {
                dirty = true;
            }
        });
        if (mediaPlayer != null) {
            mediaPlayer.setSurface(new Surface(surfaceTexture));
        }
    }

    private void updateMatrix() {
        // Start with an identify matrix.
        float[] model = {
            1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f
        };
        // Some video recorder save video frames in direction differs from recoring,
        // and add a rotation metadata. Need to detect and rotate them before we scaling.
        if (videoRotation % 360 != 0) {
            Matrix.rotateM(model, 0, -videoRotation, 0,0, 1);
        }
        float videoRatio = (float)videoWidth / videoHeight;
        float screenRatio = (float)screenWidth / screenHeight;
        if (videoRatio >= screenRatio) {
            // Treat video and screen width as 1, and compare width to scale.
            Matrix.scaleM(
                model, 0,
                ((float)videoWidth / videoHeight) / ((float)screenWidth / screenHeight),
                1, 1
            );
        } else {
            // Treat video and screen height as 1, and compare height to scale.
            Matrix.scaleM(
                model, 0, 1,
                ((float)videoHeight / videoWidth) / ((float)screenHeight / screenWidth), 1
            );
        }
        // This is a 2D center crop, so we only need model matrix, no view and projection.
        mvp = ByteBuffer.allocateDirect(
            model.length * BYTES_PER_FLOAT
        ).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mvp.put(model).position(0);
    }
}
