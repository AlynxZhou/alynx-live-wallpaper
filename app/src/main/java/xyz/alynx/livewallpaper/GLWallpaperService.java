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
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ConfigurationInfo;
import android.content.res.AssetFileDescriptor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.service.wallpaper.WallpaperService;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.MotionEvent;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
public class GLWallpaperService extends WallpaperService{
    class GLWallpaperEngine extends Engine {
        private static final String TAG = "GLWallpaperEngine";
        private final Context context;
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

        private Player.EventListener mListener;
        private List<WallpaperCard> mExternalCards;
        private GestureDetector mGestureDetector;
        private int mIndex = 0;
        private boolean mVisibility;
        private boolean playing;
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

            void onDestroy() {
                super.onDetachedFromWindow();
            }
        }

        GLWallpaperEngine(@NonNull final Context context) {
            this.context = context;
            setTouchEventsEnabled(false);
        }

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            final SharedPreferences pref = getSharedPreferences(
                LWApplication.OPTIONS_PREF, MODE_PRIVATE
            );
            allowSlide = pref.getBoolean(LWApplication.SLIDE_WALLPAPER_KEY, false);
            loadExternalWallpapers();
        }

        private void initGestureDetector(){
            if(AppConfig.isDoubleSwitch()){
                if(mGestureDetector == null){
                    mGestureDetector = new GestureDetector(GLWallpaperService.this,new GestureDetector.SimpleOnGestureListener(){
                        @Override
                        public boolean onDoubleTap(MotionEvent e) {
                            playing = false;
                            startPlayer(mExternalCards.get(getIndex(mExternalCards.size())));
                            return super.onDoubleTap(e);
                        }
                    });
                }
            }else{
                mGestureDetector = null;
            }
        }

        private void loadExternalWallpapers(){
            Utils.debug(TAG,"load external wallpapers to externalCards...");
            File file = new File(getFilesDir() + File.separator + "data.json");
            if(!file.exists()){
                return;
            }
            String jsonStr = read(file);

            if(TextUtils.isEmpty(jsonStr)){
                return;
            }
            mExternalCards = new ArrayList<>(10);
            try {
                JSONObject dataJsonObj = new JSONObject(jsonStr);
                JSONArray dataArray = dataJsonObj.getJSONArray("cards");
                int size = dataArray.length();
                for(int i = 0;i < size;i++){
                    JSONObject object = dataArray.getJSONObject(i);
                    WallpaperCard card = new WallpaperCard(
                            object.getString("name"),
                            object.getString("path"),
                            Uri.parse(object.getString("path")),
                            WallpaperCard.Type.EXTERNAL,
                            null
                    );
                    mExternalCards.add(card);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        String read(File file){
            if(file == null){
                Utils.debug("FileUtils","要读取的file为空!");
                return null;
            }
            try{
                BufferedReader in = new BufferedReader(new FileReader(file));
                String str;
                StringBuilder sb = new StringBuilder();
                while ((str = in.readLine()) != null){
                    sb.append(str);
                }
                return sb.toString();
            }catch (Exception e){
                Utils.debug("FileUtils",e.getMessage());
            }
            return null;
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder surfaceHolder) {
            super.onSurfaceCreated(surfaceHolder);
            createGLSurfaceView();
            int width = surfaceHolder.getSurfaceFrame().width();
            int height = surfaceHolder.getSurfaceFrame().height();
            renderer.setScreenSize(width, height);
            startPlayer(wallpaperCard);
        }

        @Override
        public void onTouchEvent(MotionEvent event) {
            if(mGestureDetector != null){
                mGestureDetector.onTouchEvent(event);
            }else{
                super.onTouchEvent(event);
            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            initGestureDetector();
            if (renderer != null) {
                if (visible) {
                    final SharedPreferences pref = getSharedPreferences(
                        LWApplication.OPTIONS_PREF, MODE_PRIVATE
                    );
                    allowSlide = pref.getBoolean(LWApplication.SLIDE_WALLPAPER_KEY, false);
                    glSurfaceView.onResume();
                    mVisibility = true;
                    startPlayer(null);
                } else {
                    mVisibility = false;
                    stopPlayer();
                    glSurfaceView.onPause();
                    // Prevent useless renderer calculating.
                    allowSlide = false;
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
            if(mListener != null && exoPlayer != null){
                exoPlayer.removeListener(mListener);
            }
            stopPlayer();
            glSurfaceView.onDestroy();
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

        private void getVideoMetadata() throws IOException {
            final MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            if(wallpaperCard.getType() == WallpaperCard.Type.INTERNAL){
                final AssetFileDescriptor afd = getAssets().openFd(wallpaperCard.getPath());
                mmr.setDataSource(afd.getFileDescriptor(),afd.getStartOffset(),afd.getDeclaredLength());
                afd.close();
            }else if(wallpaperCard.getType() == WallpaperCard.Type.EXTERNAL){
                mmr.setDataSource(context,wallpaperCard.getUri());
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
            videoRotation = Integer.parseInt(rotation);
            videoWidth = Integer.parseInt(width);
            videoHeight = Integer.parseInt(height);

            mmr.release();
        }

        private void startPlayer(WallpaperCard card){
            Utils.debug(TAG,"Player starting...");
            //Prevent reloading after visibility changes
            if(mVisibility && playing && !AppConfig.isIsChange()){
                exoPlayer.setPlayWhenReady(true);
                return;
            }
            if(isPreview()){
                wallpaperCard = LWApplication.getPreviewWallpaperCard();
            }else{
                wallpaperCard = LWApplication.getCurrentWallpaperCard(GLWallpaperService.this);
            }
            if(card != null){
                wallpaperCard = card;
                wallpaperCard.setVideoRotation(0);
                wallpaperCard.setVideoWith(0);
                wallpaperCard.setVideoHeight(0);
                if(card.getPath().equals(oldWallpaperCard.getPath())){
                    Utils.debug(TAG,"要切换的壁纸一致,跳过...");
                    return;
                }
            }
            if(wallpaperCard == null){
                return;
            }
            oldWallpaperCard = wallpaperCard;
            if(wallpaperCard.getVideoHeight() == 0 || wallpaperCard.getVideoWith() == 0){
                try {
                    getVideoMetadata();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                wallpaperCard.setVideoHeight(videoHeight);
                wallpaperCard.setVideoWith(videoWidth);
                wallpaperCard.setVideoRotation(videoRotation);
            }

            initExoPlayer();
            setVideoSource();
        }

        private void initExoPlayer(){
            if(exoPlayer == null){
                trackSelector = new DefaultTrackSelector();
                exoPlayer = ExoPlayerFactory.newSimpleInstance(context, trackSelector);
                exoPlayer.setRepeatMode(Player.REPEAT_MODE_ALL);
                mListener = new Player.EventListener() {
                    @Override
                    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                        if(playbackState == Player.STATE_ENDED){
                            Utils.debug(TAG,"the end...");
                            playing = false;
                            initExoPlayer();
                            startPlayer(mExternalCards.get(getIndex(mExternalCards.size())));
                        }
                    }
                };
                exoPlayer.addListener(mListener);
            }
            if(!AppConfig.isAllowVolume()){
                exoPlayer.setVolume(0.0f);
                // Disable audio decoder.
                final int count = exoPlayer.getRendererCount();
                for (int i = 0; i < count; ++i) {
                    if (exoPlayer.getRendererType(i) == C.TRACK_TYPE_AUDIO) {
                        trackSelector.setParameters(
                                trackSelector.buildUponParameters().setRendererDisabled(i, true)
                        );
                    }
                }
            }
            else
            {
                if(exoPlayer.getVolume() <= 0){
                   exoPlayer.setVolume(1.0f);
                }
                final int count = exoPlayer.getRendererCount();
                for (int i = 0; i < count; ++i) {
                    if (exoPlayer.getRendererType(i) == C.TRACK_TYPE_AUDIO) {
                        trackSelector.setParameters(
                                trackSelector.buildUponParameters().setRendererDisabled(i, false)
                        );
                    }
                }
            }
            if(AppConfig.isAllowAutoSwitch()){
                exoPlayer.setRepeatMode(Player.REPEAT_MODE_OFF);
            }else{
                exoPlayer.setRepeatMode(Player.REPEAT_MODE_ONE);
            }
        }

        private void setVideoSource(){
            //Utils.debug(TAG,"getVideoMetadata()所消耗的时间:" + (endTime - startTime) + "ms");

            final DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(
                    context, Util.getUserAgent(context, "xyz.alynx.livewallpaper")
            );
            // ExoPlayer can load file:///android_asset/ uri correctly.
            videoSource = new ExtractorMediaSource.Factory(
                    dataSourceFactory
            ).createMediaSource(wallpaperCard.getUri());
            // Let we assume video has correct info in metadata, or user should fix it.
            renderer.setVideoSizeAndRotation(wallpaperCard.getVideoWith(), wallpaperCard.getVideoHeight(), wallpaperCard.getVideoRotation());
            // This must be set after getting video info.
            renderer.setSourcePlayer(exoPlayer);
            exoPlayer.prepare(videoSource);
            exoPlayer.setPlayWhenReady(true);
            playing = true;
            AppConfig.setIsChange(false);
            //Utils.debug(TAG,"setVideoSource..所消耗的时间:" + (endTime - startTime) + "ms");
        }

        private int getIndex(int range){
            if(mExternalCards.size() == 1){
                return 0;
            }
            Random random = new Random();
            int num = random.nextInt(range);
            if(num == mIndex){
                if(num + 1 == range){
                    num = 0;
                }else{
                    num++;
                }
            }
            mIndex = num;
            return num;
        }

        private void stopPlayer() {
            if(!mVisibility){
                exoPlayer.setPlayWhenReady(false);
                return;
            }
            if (exoPlayer != null) {
                if (exoPlayer.getPlayWhenReady()) {
                    Utils.debug(TAG, "Player stopping");
                    exoPlayer.setPlayWhenReady(false);
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
