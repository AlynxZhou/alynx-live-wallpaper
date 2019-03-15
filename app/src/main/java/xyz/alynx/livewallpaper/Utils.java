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
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class Utils {
    private static final String TAG = "Utils";

    /**
     * createVideoThumbnailFromUri
     * @param context
     * @param uri
     * @return Bitmap thumbnail
     *
     * Hacked from ThumbnailUtils.createVideoThumbnail()'s code.
     */
    public static Bitmap createVideoThumbnailFromUri(Context context, Uri uri) {
        Bitmap bitmap = null;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
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
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int max = Math.max(width, height);
        if (max > 512) {
            float scale = 512f / max;
            int w = Math.round(scale * width);
            int h = Math.round(scale * height);
            bitmap = Bitmap.createScaledBitmap(bitmap, w, h, true);
        }
        return bitmap;
    }

    public static int compileShaderResourceGLES30(
        Context context,
        int shaderType,
        int shaderRes
    ) throws RuntimeException {
        InputStream inputStream = context.getResources().openRawResource(shaderRes);
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        String line = null;
        StringBuilder stringBuilder = new StringBuilder();
        try {
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line);
                stringBuilder.append('\n');
            }
        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }
        String shaderSource = stringBuilder.toString();
        int shader = GLES30.glCreateShader(shaderType);
        if (shader == 0) {
            throw new RuntimeException("Failed to create shader");
        }
        GLES30.glShaderSource(shader, shaderSource);
        GLES30.glCompileShader(shader);
        int[] status = new int[1];
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            String log = GLES30.glGetShaderInfoLog(shader);
            GLES30.glDeleteShader(shader);
            throw new RuntimeException(log);
        }
        return shader;
    }

    public static int linkProgramGLES30(int vertShader, int fragShader) throws RuntimeException {
        int program = GLES30.glCreateProgram();
        if (program == 0) {
            throw new RuntimeException("Failed to create program");
        }
        GLES30.glAttachShader(program, vertShader);
        GLES30.glAttachShader(program, fragShader);
        GLES30.glLinkProgram(program);
        int[] status = new int[1];
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0);
        if (status[0] == 0) {
            String log = GLES30.glGetProgramInfoLog(program);
            GLES30.glDeleteProgram(program);
            throw new RuntimeException(log);
        }
        return program;
    }

    public static int compileShaderResourceGLES20(
        Context context,
        int shaderType,
        int shaderRes
    ) throws RuntimeException {
        InputStream inputStream = context.getResources().openRawResource(shaderRes);
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        String line = null;
        StringBuilder stringBuilder = new StringBuilder();
        try {
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line);
                stringBuilder.append('\n');
            }
        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }
        String shaderSource = stringBuilder.toString();
        int shader = GLES20.glCreateShader(shaderType);
        if (shader == 0) {
            throw new RuntimeException("Failed to create shader");
        }
        GLES20.glShaderSource(shader, shaderSource);
        GLES20.glCompileShader(shader);
        int[] status = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new RuntimeException(log);
        }
        return shader;
    }

    public static int linkProgramGLES20(int vertShader, int fragShader) throws RuntimeException {
        int program = GLES20.glCreateProgram();
        if (program == 0) {
            throw new RuntimeException("Failed to create program");
        }
        GLES20.glAttachShader(program, vertShader);
        GLES20.glAttachShader(program, fragShader);
        GLES20.glLinkProgram(program);
        int[] status = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0);
        if (status[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(program);
            GLES20.glDeleteProgram(program);
            throw new RuntimeException(log);
        }
        return program;
    }

    public static void debug(final String tag, String message) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message);
        }
    }
}
