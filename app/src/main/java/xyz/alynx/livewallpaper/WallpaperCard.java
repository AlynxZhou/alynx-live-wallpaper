package xyz.alynx.livewallpaper;

import android.graphics.Bitmap;

import org.json.JSONException;
import org.json.JSONObject;

public class WallpaperCard {
    private final static String TAG = "WallpaperCard";
    // INTERNAL means this video is bundled into app assets.
    // So it cannot be removed.
    public enum Type {INTERNAL, EXTERNAL};
    private String name = null;
    // It's hard to access file path with Android built-in file chooser.
    // So actually path is a path.
    private String path = null;
    private Bitmap thumbnail = null;
    private Type type = Type.EXTERNAL;

    public WallpaperCard(String name, String path, Type type, Bitmap thumbnail) {
        setName(name);
        setPath(path);
        this.type = type;
        this.thumbnail = thumbnail;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Bitmap getThumbnail() {
        return thumbnail;
    }

    // Only EXTERNAL wallpaper can be removed.
    public Boolean isRemovable() {
        return type == Type.EXTERNAL;
    }

    public Type getType() {
        return type;
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("name", getName());
        json.put("path", getPath());
        return json;
    }
}
