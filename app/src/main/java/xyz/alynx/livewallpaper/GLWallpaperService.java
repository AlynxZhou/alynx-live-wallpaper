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

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ConfigurationInfo;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.AsyncTask;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Objects;

public class GLWallpaperService extends WallpaperService {
    private final static String TAG = "GLWallpaperService";
    protected class GLWallpaperEngine extends Engine {
        private final static String TAG = "GLWallpaperEngine";
        private Context context = null;
        private GLWallpaperSurfaceView glSurfaceView = null;
        private MediaPlayer mediaPlayer = null;
        private WallpaperCard wallpaperCard = null;
        private WallpaperCard oldWallpaperCard = null;
        private GLWallpaperRenderer renderer = null;
        private int progress = 0;

        private class GLWallpaperSurfaceView extends GLSurfaceView {
            public GLWallpaperSurfaceView(Context context) {
                super(context);
            }

            @Override
            public SurfaceHolder getHolder() {
                // This is a hack. Because Android Live Wallpaper only has a Surface.
                // So we create a GLSurfaceView, and when drawing to its Surface,
                // we replace it with WallpaperEngine's Surface.
                return getSurfaceHolder();
            }

            public void onDestroy() {
                super.onDetachedFromWindow();
            }
        }

        private class AddCardTask extends AsyncTask<String, Void, WallpaperCard> {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

            @Override
            protected WallpaperCard doInBackground(String... strings) {
                List<WallpaperCard> cards = LWApplication.getCards();
                String name = strings[0];
                if (name.length() > 30) {
                    name = name.substring(0, 30);
                }
                String path = strings[1];
                for (WallpaperCard card : cards) {
                    if (Objects.equals(card.getPath(), path)) {
                        cancel(true);
                        return null;
                    }
                }
                Bitmap thumbnail = Utils.createVideoThumbnailFromUri(
                    getApplicationContext(),
                    Uri.parse(path)
                );
                if (thumbnail == null) {
                    cancel(true);
                    return null;
                }
                Uri uri = Uri.parse(path);
                // Check for the freshest uri.
                getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                );
                WallpaperCard card = new WallpaperCard(
                    name, uri.toString(), WallpaperCard.Type.EXTERNAL, thumbnail
                );
                if (strings.length >= 3 && strings[2] != null) {
                    // Check for activate when resume from data file.
                    if (Objects.equals(card.getPath(), strings[2])) {
                        LWApplication.setActivateWallpaperCard(card);
                    }
                }
                return card;
            }

            @Override
            protected void onCancelled() {
                super.onCancelled();
            }

            @Override
            protected void onPostExecute(WallpaperCard card) {
                LWApplication.getCards().add(card);
            }
        }

        public GLWallpaperEngine(Context context) {
            this.context = context;
        }

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            List<WallpaperCard> cards = LWApplication.getCards();
            // If service starts before MainActivity (system restarted?), maybe this help.
            if (cards.size() == 1) {
                try {
                    FileInputStream fis = openFileInput(LWApplication.JSON_FILE_NAME);
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fis));
                    String line = null;
                    StringBuilder stringBuilder = new StringBuilder();
                    while ((line = bufferedReader.readLine()) != null) {
                        stringBuilder.append(line);
                        stringBuilder.append('\n');
                    }
                    String jsonSource = stringBuilder.toString();
                    JSONObject json = new JSONObject(jsonSource);
                    JSONArray cardsArray = json.getJSONArray("cards");
                    String activateWallpaperPath = json.getString("activateWallpaperPath");
                    // If already an activateCard, don't reload from data.
                    if (LWApplication.getActivateWallpaperCard() != null ||
                        activateWallpaperPath.length() == 0) {
                        activateWallpaperPath = null;
                    }
                    if (activateWallpaperPath != null) {
                        for (WallpaperCard card : cards) {
                            if (Objects.equals(card.getPath(), activateWallpaperPath)) {
                                LWApplication.setActivateWallpaperCard(card);
                            }
                        }
                    }
                    for (int i = 0; i < cardsArray.length(); ++i) {
                        String name = cardsArray.getJSONObject(i).getString("name");
                        String path = cardsArray.getJSONObject(i).getString("path");
                        boolean found = false;
                        for (WallpaperCard card : cards) {
                            if (Objects.equals(card.getPath(), path)) {
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            // Because task is async, we cannot check active outside task.
                            // So check inside.
                            new AddCardTask().execute(name, path, activateWallpaperPath);
                        }
                    }
                    if (LWApplication.getActivateWallpaperCard() == null) {
                        LWApplication.setActivateWallpaperCard(LWApplication.getCards().get(0));
                    }
                    bufferedReader.close();
                    fis.close();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder surfaceHolder) {
            super.onSurfaceCreated(surfaceHolder);
            glSurfaceView = createGLSurfaceView();
            int width = surfaceHolder.getSurfaceFrame().width();
            int height = surfaceHolder.getSurfaceFrame().height();
            renderer.setScreenSize(width, height);
            startPlayer();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            if (renderer != null) {
                if (visible) {
                    startPlayer();
                    glSurfaceView.onResume();
                } else {
                    stopPlayer();
                    glSurfaceView.onPause();
                }
            }
        }

        @Override
        public void onSurfaceChanged(
            SurfaceHolder surfaceHolder,
            int format,
            int width,
            int height
        ) {
            super.onSurfaceChanged(surfaceHolder, format, width, height);
            renderer.setScreenSize(width, height);
        }

        @Override
        public void onDestroy() {
            stopPlayer();
            glSurfaceView.onDestroy();
            super.onDestroy();
        }

        private GLWallpaperSurfaceView createGLSurfaceView() {
            glSurfaceView = new GLWallpaperSurfaceView(context);
            ActivityManager activityManager = (ActivityManager)getSystemService(
                Context.ACTIVITY_SERVICE
            );
            if (activityManager == null) {
                throw new RuntimeException("Cannot get ActivityManager");
            }
            ConfigurationInfo configInfo = activityManager.getDeviceConfigurationInfo();
            if (configInfo.reqGlEsVersion >= 0x30000) {
                glSurfaceView.setEGLContextClientVersion(3);
                glSurfaceView.setPreserveEGLContextOnPause(true);
                renderer = new GLWallpaperRenderer(context);
                glSurfaceView.setRenderer(renderer);
            } else {
                throw new RuntimeException("GLESv3 is not supported");
            }
            return glSurfaceView;
        }

        private void startPlayer() {
            if (mediaPlayer != null) {
                stopPlayer();
            }
            oldWallpaperCard = wallpaperCard;
            wallpaperCard = LWApplication.getActivateWallpaperCard();
            if (wallpaperCard == null) {
                return;
            }
            mediaPlayer = new MediaPlayer();
            renderer.setSourceMediaPlayer(mediaPlayer);
            mediaPlayer.setLooping(true);
            mediaPlayer.setVolume(0.0f, 0.0f);
            mediaPlayer.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener() {
                @Override
                public void onVideoSizeChanged(MediaPlayer mediaPlayer, int width, int height) {
                    renderer.setVideoSize(width, height);
                }
            });
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mediaPlayer) {
                    renderer.setVideoSize(
                        mediaPlayer.getVideoWidth(),
                        mediaPlayer.getVideoHeight()
                    );
                    if (oldWallpaperCard != null &&
                        Objects.equals(oldWallpaperCard.getPath(), wallpaperCard.getPath())) {
                        mediaPlayer.seekTo(progress);
                    } else {
                        mediaPlayer.seekTo(0);
                    }

                }
            });
            mediaPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
                @Override
                public void onSeekComplete(MediaPlayer mediaPlayer) {
                    mediaPlayer.start();
                }
            });
            try {
                if (wallpaperCard.getType() == WallpaperCard.Type.INTERNAL) {
                    AssetFileDescriptor afd = getAssets().openFd(wallpaperCard.getPath());
                    mediaPlayer.setDataSource(
                        afd.getFileDescriptor(),
                        afd.getStartOffset(),
                        afd.getLength()
                    );
                    afd.close();
                } else {
                    mediaPlayer.setDataSource(context, Uri.parse(wallpaperCard.getPath()));
                }
                // This must be called after data source is set.
                mediaPlayer.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT);
                mediaPlayer.prepareAsync();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void stopPlayer() {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                    progress = mediaPlayer.getCurrentPosition();
                }
                mediaPlayer.stop();
                mediaPlayer.reset();
                mediaPlayer.release();
                mediaPlayer = null;
            }
        }
    }

    @Override
    public Engine onCreateEngine() {
        return new GLWallpaperEngine(this);
    }
}
