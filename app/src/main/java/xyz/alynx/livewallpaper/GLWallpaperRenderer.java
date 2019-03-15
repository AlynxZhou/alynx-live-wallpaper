package xyz.alynx.livewallpaper;

import android.content.Context;
import android.opengl.GLSurfaceView;

import com.google.android.exoplayer2.SimpleExoPlayer;

public abstract class GLWallpaperRenderer implements GLSurfaceView.Renderer {
    private final static String TAG = "GLWallpaperRenderer";
    protected Context context = null;

    public GLWallpaperRenderer(Context context) {
        this.context = context;
    }

    public Context getContext() {
        return context;
    }

    public abstract void setSourcePlayer(SimpleExoPlayer exoPlayer);
    public abstract void setScreenSize(int width, int height);
    public abstract void setVideoSizeAndRotation(int width, int height, int rotation);
    public abstract void setOffset(float xOffset, float yOffset);
}