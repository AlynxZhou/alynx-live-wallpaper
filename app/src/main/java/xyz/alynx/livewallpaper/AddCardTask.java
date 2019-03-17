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

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Objects;

public class AddCardTask extends AsyncTask<String, Void, WallpaperCard> {
    private static final String TAG = "AddCardTask";
    private WeakReference<AppCompatActivity> activityRef;
    private AddCardTaskListener listener;
    private String message = null;

    public interface AddCardTaskListener {
        void onPreExecute(String message);
        void onPostExecute(String message, WallpaperCard card);
        void onCancelled(String message);
    }

    AddCardTask(AppCompatActivity activity, @NonNull final AddCardTaskListener listener) {
        this.activityRef = new WeakReference<>(activity);
        this.listener = listener;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        if (activityRef.get() != null) {
            listener.onPreExecute(
                activityRef.get().getResources().getText(R.string.adding_wallpaper).toString()
            );
        }
    }

    @Override
    protected WallpaperCard doInBackground(String... strings) {
        List<WallpaperCard> cards = LWApplication.getCards();
        String name = strings[0];
        if (name.length() > 30) {
            name = name.substring(0, 30);
        }
        String path = strings[1];
        for (WallpaperCard card : cards) {
            if (Objects.equals(card.getPath(), path)) {
                if (activityRef.get() != null) {
                    message = String.format(
                        activityRef.get().getResources()
                            .getText(R.string.same_wallpaper).toString(),
                        name, card.getName()
                    );
                }
                cancel(true);
                return null;
            }
        }
        Uri uri = Uri.parse(path);
        try {
            if (activityRef.get() != null) {
                // Ask for persistable permission.
                activityRef.get().getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                );
            } else {
                cancel(true);
                return null;
            }
        } catch (SecurityException e) {
            e.printStackTrace();
            cancel(true);
            return null;
        }
        Bitmap thumbnail = null;
        if (activityRef.get() != null) {
            thumbnail = Utils.createVideoThumbnailFromUri(
                activityRef.get().getApplicationContext(), uri
            );
        }
        if (thumbnail == null) {
            if (activityRef.get() != null) {
                message = String.format(
                    activityRef.get().getResources().getText(R.string.no_thumbnail).toString(),
                    name
                );
            }
            cancel(true);
            return null;
        }
        return new WallpaperCard(
            name, uri.toString(), uri, WallpaperCard.Type.EXTERNAL, thumbnail
        );
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
        listener.onCancelled(message);
    }

    @Override
    protected void onPostExecute(WallpaperCard card) {
        super.onPostExecute(card);
        if (card == null) {
            return;
        }
        if (activityRef.get() != null) {
            message = String.format(
                activityRef.get().getResources().getText(R.string.added_wallpaper).toString(),
                card.getName()
            );
        }
        listener.onPostExecute(message, card);
    }
}