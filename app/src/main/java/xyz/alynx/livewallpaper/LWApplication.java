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

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.support.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@SuppressWarnings("WeakerAccess")
public class LWApplication extends Application {
    @SuppressWarnings("unused")
    private static final String TAG = "LWApplication";
    public static final String JSON_FILE_NAME = "data.json";
    private static final String CURRENT_CARD_PREF = "currentWallpaperCard";
    public static final String OPTIONS_PREF = "options";
    public static final String SLIDE_WALLPAPER_KEY = "slideWallpaper";
    private static final String INTERNAL_WALLPAPER_IMAGE_PATH = "wallpapers/fire-rain/fire-rain-512x384.webp";
    private static final String INTERNAL_WALLPAPER_VIDEO_PATH = "wallpapers/fire-rain/fire-rain-720x720.mp4";
    private static List<WallpaperCard> cards = null;
    private static WallpaperCard currentWallpaperCard = null;
    private static WallpaperCard previewWallpaperCard = null;
    private static boolean isChange = false;

    @Override
    public void onCreate() {
        super.onCreate();

        initConfig();
    }

    private void initConfig(){
        SharedPreferences preferences = getSharedPreferences(OPTIONS_PREF,MODE_PRIVATE);
        AppConfig.setAllowAutoSwitch(preferences.getBoolean(AppConfig.ALLOW_AUTO_SWITCH,false));
        AppConfig.setAllowVolume(preferences.getBoolean(AppConfig.ALLOW_VOLUME,false));
        AppConfig.setDoubleSwitch(preferences.getBoolean(AppConfig.DOUBLE_SWITCH,true));
    }

    @NonNull
    public static List<WallpaperCard> getCards(@NonNull final Context context) {
        if (cards == null) {
            initCards(context);
        }
        return cards;
    }

    public static WallpaperCard getCurrentWallpaperCard(@NonNull final Context context) {
        if (currentWallpaperCard == null) {
            currentWallpaperCard = loadWallpaperCardPreference(context);
        }
        return currentWallpaperCard;
    }

    public static void setCurrentWallpaperCard(@NonNull final Context context, final WallpaperCard wallpaperCard) {
        currentWallpaperCard = wallpaperCard;
        if (wallpaperCard != null) {
            saveWallpaperCardPreference(context, wallpaperCard);
        }
    }

    public static boolean isCurrentWallpaperCard(WallpaperCard wallpaperCard) {
        // Only check variable, no SharedPreference.
        // If wallpaper is not this app, Preference should not be cleared,
        // only currentWallpaperCard is set to null.
        // Because when we getCurrentWallpaperCard(), it loads Preference,
        // and set currentWallpaperCard to non-null.
        // We want to detect whether service is selected
        // by checking whether currentWallpaperCard is null.
        // If we use getCurrentWallpaperCard(), we cannot archive this.
        return currentWallpaperCard != null && wallpaperCard.equals(currentWallpaperCard);
    }

    public static WallpaperCard getPreviewWallpaperCard() {
        return previewWallpaperCard;
    }

    public static void setPreviewWallpaperCard(final WallpaperCard wallpaperCard) {
        previewWallpaperCard = wallpaperCard;
    }

    public static JSONArray getCardsJSONArray() throws JSONException {
        JSONArray jsonArray = new JSONArray();
        if (cards != null) {
            for (WallpaperCard card : cards) {
                // INTERNAL WallpaperCard don't need to save.
                if (card.getType() != WallpaperCard.Type.INTERNAL) {
                    jsonArray.put(card.toJSON());
                }
            }
        }
        return jsonArray;
    }

    private static void saveWallpaperCardPreference(@NonNull final Context context, final WallpaperCard wallpaperCard) {
        final SharedPreferences pref = context.getSharedPreferences(CURRENT_CARD_PREF, MODE_PRIVATE);
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

    private static WallpaperCard loadWallpaperCardPreference(@NonNull final Context context) {
        final SharedPreferences pref = context.getSharedPreferences(CURRENT_CARD_PREF, MODE_PRIVATE);
        final String name = pref.getString("name", null);
        final String path = pref.getString("path", null);
        if (name == null || path == null) {
            return null;
        }
        WallpaperCard.Type type = WallpaperCard.Type.EXTERNAL;
        Uri uri;
        if (Objects.equals(pref.getString("type", null), "INTERNAL")) {
            type = WallpaperCard.Type.INTERNAL;
            uri = Uri.parse("file:///android_asset/" + path);
        } else {
            uri = Uri.parse(path);
        }
        return new WallpaperCard(name, path, uri, type, null);
    }

    private static void initCards(@NonNull final Context context) {
        cards = new ArrayList<>();
        Bitmap thumbnail = null;
        try {
            thumbnail = BitmapFactory.decodeStream(
                context.getAssets().open(INTERNAL_WALLPAPER_IMAGE_PATH)
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
        cards.add(new WallpaperCard(
            context.getResources().getString(R.string.fire_rain),
            INTERNAL_WALLPAPER_VIDEO_PATH, Uri.parse(
                "file:///android_asset/" + INTERNAL_WALLPAPER_VIDEO_PATH
            ), WallpaperCard.Type.INTERNAL, thumbnail
        ));
    }
}
