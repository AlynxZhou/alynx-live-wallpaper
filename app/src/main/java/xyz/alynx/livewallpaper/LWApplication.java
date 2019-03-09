package xyz.alynx.livewallpaper;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LWApplication extends Application {
    private final static String TAG = "LWApplication";
    private static List<WallpaperCard> cards = null;
    private static WallpaperCard activateWallpaperCard = null;
    public final static String JSON_FILE_NAME = "data.json";

    @Override
    public void onCreate() {
        super.onCreate();
        cards = new ArrayList<>();
        Bitmap thumbnail = null;
        try {
            thumbnail = BitmapFactory.decodeStream(getAssets().open(
                "wallpapers/saber-sandwich/saber-sandwich-512x384.jpg"
            ));
        } catch (IOException e) {
            e.printStackTrace();
        }
        cards.add(new WallpaperCard(
            getApplicationContext().getResources().getString(R.string.saber_sandwich),
            "wallpapers/saber-sandwich/saber-sandwich-1080x1080.mp4",
            WallpaperCard.Type.INTERNAL, thumbnail
        ));
    }

    public static List<WallpaperCard> getCards() {
        return cards;
    }

    public static WallpaperCard getActivateWallpaperCard() {
        return activateWallpaperCard;
    }

    public static void setActivateWallpaperCard(WallpaperCard wallpaperCard) {
        activateWallpaperCard = wallpaperCard;
    }

    public static JSONArray getCardsJSONArray() throws JSONException {
        JSONArray jsonArray = new JSONArray();
        for (WallpaperCard card : cards) {
            if (card.getType() != WallpaperCard.Type.INTERNAL) {
                jsonArray.put(card.toJSON());
            }
        }
        return jsonArray;
    }
}
