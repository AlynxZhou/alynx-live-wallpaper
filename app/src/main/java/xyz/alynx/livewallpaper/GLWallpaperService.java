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

import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ConfigurationInfo;
import android.content.res.AssetFileDescriptor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.ParcelFileDescriptor;
import android.service.wallpaper.WallpaperService;
import android.support.annotation.NonNull;
import android.view.SurfaceHolder;
import android.widget.Toast;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

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
    private static final String TAG = "GLWallpaperService";

    public class GLWallpaperEngine extends Engine {
        private static final String TAG = "GLWallpaperEngine";
        private static final String CURRENT_CARD_PREF = "currentWallpaperCard";
        private Context context;
        private GLWallpaperSurfaceView glSurfaceView = null;
        private SimpleExoPlayer exoPlayer = null;
        private MediaSource videoSource = null;
        private DefaultTrackSelector trackSelector = null;
        private WallpaperCard wallpaperCard = null;
        private WallpaperCard oldWallpaperCard = null;
        private GLWallpaperRenderer renderer = null;
        private boolean allowSlide = false;
        private int videoRotation = 0;
        private int videoWidth = 0;
        private int videoHeight = 0;
        private long progress = 0;

        private class GLWallpaperSurfaceView extends GLSurfaceView {
            private static final String TAG = "GLWallpaperSurface";

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

        GLWallpaperEngine(@NonNull final Context context) {
            this.context = context;
        }

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            final SharedPreferences pref = getSharedPreferences(
                LWApplication.OPTIONS_PREF, MODE_PRIVATE
            );
            allowSlide = pref.getBoolean(LWApplication.SLIDE_WALLPAPER_KEY, false);
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder surfaceHolder) {
            super.onSurfaceCreated(surfaceHolder);
            createGLSurfaceView();
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
                    glSurfaceView.onPause();
                    stopPlayer();
                }
            }
        }

        @Override
        public void onOffsetsChanged(
            float xOffset, float yOffset,
            float xOffsetStep, float yOffsetStep,
            int xPixelOffset, int yPixelOffset
        ) {
            super.onOffsetsChanged(
                xOffset, yOffset, xOffsetStep,
                yOffsetStep, xPixelOffset, yPixelOffset
            );
            if (allowSlide && !isPreview()) {
                renderer.setOffset(0.5f - xOffset, 0.5f - yOffset);
            }
        }

        @Override
        public void onSurfaceChanged(
            SurfaceHolder surfaceHolder, int format,
            int width, int height
        ) {
            super.onSurfaceChanged(surfaceHolder, format, width, height);
            renderer.setScreenSize(width, height);
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            glSurfaceView.onDestroy();
            stopPlayer();
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
        }

        private void createGLSurfaceView() {
            if (glSurfaceView != null) {
                glSurfaceView.onDestroy();
                glSurfaceView = null;
            }
            glSurfaceView = new GLWallpaperSurfaceView(context);
            final ActivityManager activityManager = (ActivityManager)getSystemService(
                Context.ACTIVITY_SERVICE
            );
            if (activityManager == null) {
                throw new RuntimeException("Cannot get ActivityManager");
            }
            final ConfigurationInfo configInfo = activityManager.getDeviceConfigurationInfo();
            if (configInfo.reqGlEsVersion >= 0x30000) {
                Utils.debug(TAG, "Support GLESv3");
                glSurfaceView.setEGLContextClientVersion(3);
                renderer = new GLES30WallpaperRenderer(context);
            } else if (configInfo.reqGlEsVersion >= 0x20000) {
                Utils.debug(TAG, "Fallback to GLESv2");
                glSurfaceView.setEGLContextClientVersion(2);
                renderer = new GLES20WallpaperRenderer(context);
            } else {
                Toast.makeText(context, R.string.gles_version, Toast.LENGTH_LONG).show();
                throw new RuntimeException("Needs GLESv2 or higher");
            }
            glSurfaceView.setPreserveEGLContextOnPause(true);
            glSurfaceView.setRenderer(renderer);
            // On demand render will lead to black screen.
            glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        }

        private boolean checkWallpaperCardValid() {
            if (wallpaperCard == null) {
                return false;
            }
            if (wallpaperCard.getType() == WallpaperCard.Type.INTERNAL) {
                return true;
            }
            boolean res = true;
            try {
                final ContentResolver resolver = getContentResolver();
                final ParcelFileDescriptor pfd = resolver.openFileDescriptor(
                    wallpaperCard.getUri(), "r"
                );
                if (pfd == null) {
                    res = false;
                } else {
                    pfd.close();
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                res = false;
            } catch (IOException e) {
                e.printStackTrace();
                res = false;
            }
            return res;
        }

        private void saveWallpaperCardPreference() {
            if (wallpaperCard == null) {
                Utils.debug(TAG, "Save wallpaper card failed, wallpaper card is null");
                return;
            }
            final SharedPreferences pref = getSharedPreferences(CURRENT_CARD_PREF, MODE_PRIVATE);
            // Save to preference.
            final SharedPreferences.Editor prefEditor = pref.edit();
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

        private void loadWallpaperCardPreference() {
            final SharedPreferences pref = getSharedPreferences(CURRENT_CARD_PREF, MODE_PRIVATE);
            final String name = pref.getString("name", null);
            final String path = pref.getString("path", null);
            if (name == null || path == null) {
                wallpaperCard = null;
                return;
            }
            WallpaperCard.Type type = WallpaperCard.Type.EXTERNAL;
            Uri uri;
            if (Objects.equals(pref.getString("type", null), "INTERNAL")) {
                type = WallpaperCard.Type.INTERNAL;
                uri = Uri.parse("file:///android_asset/" + path);
            } else {
                uri = Uri.parse(path);
            }
            wallpaperCard = new WallpaperCard(name, path, uri, type, null);
        }

        private void loadWallpaperCard() {
            oldWallpaperCard = wallpaperCard;
            if (isPreview()) {
                wallpaperCard = LWApplication.getPreviewWallpaperCard();
            } else {
                wallpaperCard = LWApplication.getCurrentWallpaperCard();
            }
            // If no current card, means that services started and application not start.
            // Read preference and build a temp card.
            if (wallpaperCard == null) {
                loadWallpaperCardPreference();
            }
            if (!checkWallpaperCardValid()) {
                if (wallpaperCard != null) {
                    // File is removed by user.
                    Toast.makeText(context, R.string.invalid_path, Toast.LENGTH_LONG).show();
                    wallpaperCard.setInvalid();
                }
                // Load default wallpaper.
                final List<WallpaperCard> cards = LWApplication.getCards();
                if (cards != null && cards.size() > 0) {
                    wallpaperCard = cards.get(0);
                    LWApplication.setCurrentWallpaperCard(wallpaperCard);
                } else {
                    wallpaperCard = null;
                    Toast.makeText(context, R.string.default_failed, Toast.LENGTH_LONG).show();
                    throw new RuntimeException("Failed to fallback to internal wallpaper");
                }
            }
            if (!isPreview()) {
                saveWallpaperCardPreference();
            }
        }

        private void getVideoMetadata() throws IOException {
            final MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            switch (wallpaperCard.getType()) {
            case INTERNAL:
                final AssetFileDescriptor afd = getAssets().openFd(wallpaperCard.getPath());
                mmr.setDataSource(
                    afd.getFileDescriptor(),
                    afd.getStartOffset(),
                    afd.getDeclaredLength()
                );
                afd.close();
                break;
            case EXTERNAL:
                mmr.setDataSource(context, wallpaperCard.getUri());
                break;
            }
            final String rotation = mmr.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
            );
            final String width = mmr.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
            );
            final String height = mmr.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
            );
            mmr.release();
            videoRotation = Integer.parseInt(rotation);
            videoWidth = Integer.parseInt(width);
            videoHeight = Integer.parseInt(height);

        }

        private void startPlayer() {
            if (exoPlayer != null) {
                stopPlayer();
            }
            Utils.debug(TAG, "Player starting");
            loadWallpaperCard();
            if (wallpaperCard == null) {
                // gg
                return;
            }
            try {
                getVideoMetadata();
            } catch (IOException e) {
                e.printStackTrace();
                stopPlayer();
                startPlayer();
            }
            trackSelector = new DefaultTrackSelector();
            exoPlayer = ExoPlayerFactory.newSimpleInstance(context, trackSelector);
            exoPlayer.setVolume(0.0f);
            // Disable audio decoder.
            final int count = exoPlayer.getRendererCount();
            for (int i = 0; i < count; ++i) {
                if (exoPlayer.getRendererType(i) == C.TRACK_TYPE_AUDIO) {
                    trackSelector.setParameters(
                        trackSelector.buildUponParameters().setRendererDisabled(i, true)
                    );
                    break;
                }
            }
            exoPlayer.setRepeatMode(Player.REPEAT_MODE_ALL);
            final DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(
                context, Util.getUserAgent(context, "xyz.alynx.livewallpaper")
            );
            // ExoPlayer can load file:///android_asset/ uri correctly.
            videoSource = new ExtractorMediaSource.Factory(
                dataSourceFactory
            ).createMediaSource(wallpaperCard.getUri());
            // Let we assume video has correct info in metadata, or user should fix it.
            renderer.setVideoSizeAndRotation(videoWidth, videoHeight, videoRotation);
            // This must be set after getting video info.
            renderer.setSourcePlayer(exoPlayer);
            exoPlayer.prepare(videoSource);
            // ExoPlayer's video size changed listener is buggy. Don't use it.
            // It give's width and height after rotation, but did not rotate frames.
            if (oldWallpaperCard != null &&
                oldWallpaperCard.equals(wallpaperCard)) {
                exoPlayer.seekTo(progress);
            }
            exoPlayer.setPlayWhenReady(true);
        }

        private void stopPlayer() {
            if (exoPlayer != null) {
                if (exoPlayer.getPlayWhenReady()) {
                    Utils.debug(TAG, "Player stopping");
                    exoPlayer.setPlayWhenReady(false);
                    progress = exoPlayer.getCurrentPosition();
                    exoPlayer.stop();
                }
                exoPlayer.release();
                exoPlayer = null;
            }
            videoSource = null;
            trackSelector = null;
        }
    }

    @Override
    public Engine onCreateEngine() {
        return new GLWallpaperEngine(this);
    }
}
