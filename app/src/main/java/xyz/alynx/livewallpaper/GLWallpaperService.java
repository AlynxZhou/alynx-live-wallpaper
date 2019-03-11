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
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ConfigurationInfo;
import android.content.res.AssetFileDescriptor;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.ParcelFileDescriptor;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;
import android.widget.Toast;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 *
 * Generally, WallpaperService should not depend other parts of app.
 * Because sometimes (like phone restarted) service starts before Activity and Application.
 * So we cannot get data from them.
 *
 * To solve this, it's better to store WallpaperCard
 * into storage, SharedPreferences is better than JSON because it's easier to get data.
 *
 * So when we cannot get current WallpaperCard from LWApplication, we read SharedPreference,
 * then build a temp WallpaperCard (in fact only type and path matter for service, so we can
 * set thumbnail to null).
 *
 * And when we get a current WallpaperCard, we save it to SharedPreference for further loading.
 *
 */
public class GLWallpaperService extends WallpaperService {
    private final static String TAG = "GLWallpaperService";
    protected class GLWallpaperEngine extends Engine {
        private final static String TAG = "GLWallpaperEngine";
        private final static String PREF_NAME = "currentWallpaperCard";
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

            /**
             * This is a hack. Because Android Live Wallpaper only has a Surface.
             * So we create a GLSurfaceView, and when drawing to its Surface,
             * we replace it with WallpaperEngine's Surface.
             */
            @Override
            public SurfaceHolder getHolder() {
                return getSurfaceHolder();
            }

            public void onDestroy() {
                super.onDetachedFromWindow();
            }
        }

        public GLWallpaperEngine(Context context) {
            this.context = context;
        }

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
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

        private void checkWallpaperCardValid() {
            if (wallpaperCard.getType() == WallpaperCard.Type.INTERNAL) {
                return;
            }
            ContentResolver resolver = getContentResolver();
            ParcelFileDescriptor pfd = null;
            try {
                pfd = resolver.openFileDescriptor(wallpaperCard.getUri(), "r");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            if (pfd == null) {
                // File is removed by user.
                Toast.makeText(context, R.string.invalid_path, Toast.LENGTH_LONG).show();
                wallpaperCard.setInvalid();
                // Load default wallpaper.
                List<WallpaperCard> cards = LWApplication.getCards();
                if (cards != null && cards.size() > 0) {
                    wallpaperCard = cards.get(0);
                } else {
                    wallpaperCard = null;
                    Toast.makeText(context, R.string.default_failed, Toast.LENGTH_LONG).show();
                }
            }
        }

        private void loadWallpaperCard() {
            oldWallpaperCard = wallpaperCard;
            wallpaperCard = LWApplication.getCurrentWallpaperCard();
            // If no current card, means that services started and application not start.
            // Read preference and build a temp card.
            SharedPreferences pref = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
            if (wallpaperCard == null) {
                WallpaperCard.Type type = WallpaperCard.Type.EXTERNAL;
                Uri uri = null;
                if (Objects.equals(pref.getString("type", null), "INTERNAL")) {
                    type = WallpaperCard.Type.INTERNAL;
                    uri = Uri.parse("file:///android_asset/" + pref.getString("path", null));
                } else {
                    uri = Uri.parse(pref.getString("path", null));
                }
                wallpaperCard = new WallpaperCard(
                    pref.getString("name", null),
                    pref.getString("path", null),
                    uri, type, null
                );
                LWApplication.setCurrentWallpaperCard(wallpaperCard);
            }
            checkWallpaperCardValid();
            // Save to preference.
            SharedPreferences.Editor prefEditor = pref.edit();
            prefEditor.putString("name", wallpaperCard.getName());
            prefEditor.putString("path", wallpaperCard.getPath());
            switch (wallpaperCard.getType()) {
            case INTERNAL:
                prefEditor.putString("type",  "INTERNAL");
                break;
            case EXTERNAL:
                prefEditor.putString("type", "EXTERNAL");
                break;
            }
            prefEditor.apply();
        }

        private int getVideoRotation() {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            try {
                switch (wallpaperCard.getType()) {
                case INTERNAL:
                    AssetFileDescriptor afd = getAssets().openFd(wallpaperCard.getPath());
                    mmr.setDataSource(
                        afd.getFileDescriptor(),
                        afd.getStartOffset(),
                        afd.getLength()
                    );
                    afd.close();
                    break;
                case EXTERNAL:
                    mmr.setDataSource(context, wallpaperCard.getUri());
                    break;
                }
            } catch (IOException e) {
                // Typically file removing, just restart, engine will check wallpaper path.
                e.printStackTrace();
                stopPlayer();
                startPlayer();
            }
            String rotation = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            mmr.release();
            try {
                return Integer.parseInt(rotation);
            } catch (NumberFormatException e) {
                e.printStackTrace();
                return 0;
            }
        }

        private void startPlayer() {
            if (mediaPlayer != null) {
                stopPlayer();
            }
            loadWallpaperCard();
            mediaPlayer = new MediaPlayer();
            renderer.setSourceMediaPlayer(mediaPlayer);
            mediaPlayer.setLooping(true);
            mediaPlayer.setVolume(0.0f, 0.0f);
            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
                    // Typically file removing, just restart, engine will check wallpaper path.
                    stopPlayer();
                    startPlayer();
                    return true;
                }
            });
            mediaPlayer.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener() {
                @Override
                public void onVideoSizeChanged(MediaPlayer mediaPlayer, int width, int height) {
                    renderer.setVideoSizeAndRotation(width, height, getVideoRotation());
                }
            });
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mediaPlayer) {
                    renderer.setVideoSizeAndRotation(
                        mediaPlayer.getVideoWidth(),
                        mediaPlayer.getVideoHeight(),
                        getVideoRotation()
                    );
                    if (oldWallpaperCard != null &&
                        oldWallpaperCard.equals(wallpaperCard)) {
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
                switch (wallpaperCard.getType()) {
                case INTERNAL:
                    AssetFileDescriptor afd = getAssets().openFd(wallpaperCard.getPath());
                    mediaPlayer.setDataSource(
                        afd.getFileDescriptor(),
                        afd.getStartOffset(),
                        afd.getLength()
                    );
                    afd.close();
                    break;
                case EXTERNAL:
                    mediaPlayer.setDataSource(context, wallpaperCard.getUri());
                    break;
                }
                // This must be called after data source is set.
                mediaPlayer.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT);
                mediaPlayer.prepareAsync();
            } catch (IOException e) {
                // Typically file removing, just restart, engine will check wallpaper path.
                e.printStackTrace();
                stopPlayer();
                startPlayer();
            }
        }

        private void stopPlayer() {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                    progress = mediaPlayer.getCurrentPosition();
                    mediaPlayer.stop();
                }
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
