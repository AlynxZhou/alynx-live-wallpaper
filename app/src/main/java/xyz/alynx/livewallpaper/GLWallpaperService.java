package xyz.alynx.livewallpaper;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.SurfaceHolder;

import java.io.IOException;
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

        protected class GLWallpaperSurfaceView extends GLSurfaceView {
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
        public void onSurfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
            super.onSurfaceChanged(surfaceHolder, format, width, height);
            renderer.setScreenSize(width, height);
        }

        @Override
        public void onDestroy() {
            Log.d(TAG, "Engine destroy");
            stopPlayer();
            glSurfaceView.onDestroy();
            super.onDestroy();
        }

        private GLWallpaperSurfaceView createGLSurfaceView() {
            glSurfaceView = new GLWallpaperSurfaceView(context);
            ActivityManager activityManager = (ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager == null) {
                throw new RuntimeException("Cannot get ActivityManager");
            }
            ConfigurationInfo configInfo = activityManager.getDeviceConfigurationInfo();
            Log.d(TAG, "GLES version: "  + configInfo.getGlEsVersion());
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
            Log.d(TAG, "Starting playing");
            Log.d(TAG, "Card name: " + wallpaperCard.getName());
            Log.d(TAG, "Card path: " + wallpaperCard.getPath());
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
                public void onPrepared(MediaPlayer mp) {
                    renderer.setVideoSize(mediaPlayer.getVideoWidth(), mediaPlayer.getVideoHeight());
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
                public void onSeekComplete(MediaPlayer mp) {
                    mediaPlayer.start();
                    Log.d(TAG, "progress: " + mediaPlayer.getCurrentPosition());
                }
            });
            try {
                if (wallpaperCard.getType() == WallpaperCard.Type.INTERNAL) {
                    AssetFileDescriptor assetFileDescriptor = getAssets().openFd(wallpaperCard.getPath());
                    mediaPlayer.setDataSource(
                        assetFileDescriptor.getFileDescriptor(),
                        assetFileDescriptor.getStartOffset(),
                        assetFileDescriptor.getStartOffset()
                    );
                    assetFileDescriptor.close();
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
            Log.d(TAG, "Stopping playing");
            if (mediaPlayer != null) {
                mediaPlayer.pause();
                progress = mediaPlayer.getCurrentPosition();
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
