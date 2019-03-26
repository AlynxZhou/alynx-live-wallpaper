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

package xyz.alynx.livewallpaper;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

class Utils {
    @SuppressWarnings("unused")
    private static final String TAG = "Utils";

    /**
     * createVideoThumbnailFromUri
     * @param context Activity context or application context.
     * @param uri Video uri.
     * @return Bitmap thumbnail
     *
     * Hacked from ThumbnailUtils.createVideoThumbnail()'s code.
     */
    static Bitmap createVideoThumbnailFromUri(
        @NonNull final Context context,
        @NonNull final  Uri uri
    ) {
        Bitmap bitmap = null;
        final MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            bitmap = retriever.getFrameAtTime(-1);
        } catch (IllegalArgumentException e) {
            // Assume this is a corrupt video file
            e.printStackTrace();
        } catch (RuntimeException e) {
            // Assume this is a corrupt video file.
            e.printStackTrace();
        } finally {
            try {
                retriever.release();
            } catch (RuntimeException e) {
                // Ignore failures while cleaning up.
                e.printStackTrace();
            }
        }
        if (bitmap == null) {
            return null;
        }
        // Scale down the bitmap if it's too large.
        final int width = bitmap.getWidth();
        final int height = bitmap.getHeight();
        final int max = Math.max(width, height);
        if (max > 512) {
            final float scale = 512f / max;
            final int w = Math.round(scale * width);
            final int h = Math.round(scale * height);
            bitmap = Bitmap.createScaledBitmap(bitmap, w, h, true);
        }
        return bitmap;
    }

    static int compileShaderResourceGLES30(
        @NonNull Context context,
        final int shaderType,
        final int shaderRes
    ) throws RuntimeException {
        final InputStream inputStream = context.getResources().openRawResource(shaderRes);
        final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        final StringBuilder stringBuilder = new StringBuilder();
        try {
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line);
                stringBuilder.append('\n');
            }
        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }
        final String shaderSource = stringBuilder.toString();
        int shader = GLES30.glCreateShader(shaderType);
        if (shader == 0) {
            throw new RuntimeException("Failed to create shader");
        }
        GLES30.glShaderSource(shader, shaderSource);
        GLES30.glCompileShader(shader);
        final int[] status = new int[1];
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            final String log = GLES30.glGetShaderInfoLog(shader);
            GLES30.glDeleteShader(shader);
            throw new RuntimeException(log);
        }
        return shader;
    }

    static int linkProgramGLES30(
        final int vertShader,
        final int fragShader
    ) throws RuntimeException {
        int program = GLES30.glCreateProgram();
        if (program == 0) {
            throw new RuntimeException("Failed to create program");
        }
        GLES30.glAttachShader(program, vertShader);
        GLES30.glAttachShader(program, fragShader);
        GLES30.glLinkProgram(program);
        final int[] status = new int[1];
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0);
        if (status[0] == 0) {
            final String log = GLES30.glGetProgramInfoLog(program);
            GLES30.glDeleteProgram(program);
            throw new RuntimeException(log);
        }
        return program;
    }

    static int compileShaderResourceGLES20(
        @NonNull Context context,
        final int shaderType,
        final int shaderRes
    ) throws RuntimeException {
        final InputStream inputStream = context.getResources().openRawResource(shaderRes);
        final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        final StringBuilder stringBuilder = new StringBuilder();
        try {
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line);
                stringBuilder.append('\n');
            }
        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }
        final String shaderSource = stringBuilder.toString();
        int shader = GLES20.glCreateShader(shaderType);
        if (shader == 0) {
            throw new RuntimeException("Failed to create shader");
        }
        GLES20.glShaderSource(shader, shaderSource);
        GLES20.glCompileShader(shader);
        final int[] status = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            final String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new RuntimeException(log);
        }
        return shader;
    }

    static int linkProgramGLES20(
        final int vertShader,
        final int fragShader
    ) throws RuntimeException {
        int program = GLES20.glCreateProgram();
        if (program == 0) {
            throw new RuntimeException("Failed to create program");
        }
        GLES20.glAttachShader(program, vertShader);
        GLES20.glAttachShader(program, fragShader);
        GLES20.glLinkProgram(program);
        final int[] status = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0);
        if (status[0] == 0) {
            final String log = GLES20.glGetProgramInfoLog(program);
            GLES20.glDeleteProgram(program);
            throw new RuntimeException(log);
        }
        return program;
    }

    static void debug(@NonNull final String tag, @NonNull final String message) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message);
        }
    }
}
