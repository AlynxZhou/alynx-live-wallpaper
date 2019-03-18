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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LWApplication extends Application {
    private static final String TAG = "LWApplication";
    public static final String JSON_FILE_NAME = "data.json";
    public static final String OPTIONS_PREF = "options";
    public static final String SLIDE_WALLPAPER_KEY = "slideWallpaper";
    public static final String INTERNAL_WALLPAPER_IMAGE_PATH = "wallpapers/fire-rain/fire-rain-512x384.jpg";
    public static final String INTERNAL_WALLPAPER_VIDEO_PATH = "wallpapers/fire-rain/fire-rain-720x720.mp4";
    private static List<WallpaperCard> cards = null;
    private static WallpaperCard currentWallpaperCard = null;
    private static WallpaperCard previewWallpaperCard = null;

    @Override
    public void onCreate() {
        super.onCreate();
        cards = new ArrayList<>();
        Bitmap thumbnail = null;
        try {
            thumbnail = BitmapFactory.decodeStream(getAssets().open(INTERNAL_WALLPAPER_IMAGE_PATH));
        } catch (IOException e) {
            e.printStackTrace();
        }
        cards.add(new WallpaperCard(
            getApplicationContext().getResources().getString(R.string.fire_rain),
            INTERNAL_WALLPAPER_VIDEO_PATH, Uri.parse(
                "file:///android_asset/" + INTERNAL_WALLPAPER_VIDEO_PATH
            ), WallpaperCard.Type.INTERNAL, thumbnail
        ));
    }

    public static List<WallpaperCard> getCards() {
        return cards;
    }

    public static WallpaperCard getCurrentWallpaperCard() {
        return currentWallpaperCard;
    }

    public static void setCurrentWallpaperCard(final WallpaperCard wallpaperCard) {
        currentWallpaperCard = wallpaperCard;
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
}
