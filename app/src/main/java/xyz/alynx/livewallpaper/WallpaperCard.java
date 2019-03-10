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
