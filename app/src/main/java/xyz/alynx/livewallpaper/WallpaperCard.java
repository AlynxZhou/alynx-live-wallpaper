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

import android.graphics.Bitmap;
import android.net.Uri;
import android.support.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

/**
 *
 * WallpaperCard: saving wallpaper info (name, path, type, thumbnail) to display or play.
 *
 */

public class WallpaperCard {
    @SuppressWarnings("unused")
    private static final String TAG = "WallpaperCard";
    private String name;
    // It's hard to access file path with Android built-in file chooser.
    // So actually path is a path.
    private String path;
    private Uri uri;
    private final Bitmap thumbnail;
    private boolean valid = true;
    private final Type type;

    // INTERNAL means this video is bundled into app assets.
    // So it cannot be removed.
    public enum Type {INTERNAL, EXTERNAL}

    WallpaperCard(
        @NonNull final String name,
        @NonNull final String path,
        @NonNull final Uri uri,
        @NonNull final Type type,
        final Bitmap thumbnail
    ) {
        setName(name);
        setPath(path);
        setUri(uri);
        this.type = type;
        this.thumbnail = thumbnail;
    }

    @NonNull
    public String getName() {
        return name;
    }

    private void setName(@NonNull final String name) {
        this.name = name;
    }

    @NonNull
    public String getPath() {
        return path;
    }

    private void setPath(@NonNull final String path) {
        this.path = path;
    }

    @NonNull
    Uri getUri() {
        return uri;
    }

    private void setUri(@NonNull final Uri uri) {
        this.uri = uri;
    }

    Bitmap getThumbnail() {
        return thumbnail;
    }

    /**
     * @param card WallpaperCard to compare.
     * @return boolean true for equal, false for not.
     *
     * Only compare path, other parts are not matter.
     */
    boolean equals(final WallpaperCard card) {
        return Objects.equals(getPath(), card.getPath());
    }

    /**
     * @return boolean true for removable, false for not.
     *
     * Only EXTERNAL wallpaper can be removed.
     */
    boolean isRemovable() {
        return type == Type.EXTERNAL;
    }

    /**
     * @return boolean true for current, false for not.
     *
     * Compare this to LWApplication's currentWallpaperCard.
     */
    boolean isCurrent() {
        return LWApplication.isCurrentWallpaperCard(this);
    }

    boolean isValid() {
        return valid;
    }

    void setInvalid () {
        valid = false;
    }

    @NonNull
    Type getType() {
        return type;
    }

    JSONObject toJSON() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("name", getName());
        json.put("path", getPath());
        switch (getType()) {
        case INTERNAL:
            json.put("type", "INTERNAL");
            break;
        case EXTERNAL:
            json.put("type", "EXTERNAL");
            break;
        }
        return json;
    }
}
