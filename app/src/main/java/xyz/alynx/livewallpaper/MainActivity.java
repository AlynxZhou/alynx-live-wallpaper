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

import android.Manifest;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {
    private final static String TAG = "MainActivity";
    public static final int SELECT_REQUEST_CODE = 3;
    private static final int EXTERNAL_STORAGE_REQUEST_CODE = 7;
    private CardAdapter cardAdapter = null;
    private AlertDialog addDialog = null;
    private LayoutInflater layoutInflater = null;
    private FloatingActionButton addCardFab = null;
    private FloatingActionButton cancelRemoveCardFab = null;

    private class AddCardTask extends AsyncTask<String, Void, WallpaperCard> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            Snackbar.make(
                findViewById(R.id.coordinator_layout),
                R.string.adding_wallpaper,
                Snackbar.LENGTH_LONG
            ).show();
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
                    Snackbar.make(
                        findViewById(R.id.coordinator_layout),
                        String.format(
                            getResources().getText(R.string.same_wallpaper).toString(),
                            name, card.getName()
                        ),
                        Snackbar.LENGTH_LONG
                    ).show();
                    cancel(true);
                    return null;
                }
            }
            Uri uri = Uri.parse(path);
            try {
                // Ask for persistable permission.
                getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                );
            } catch (SecurityException e) {
                e.printStackTrace();
                cancel(true);
                return null;
            }
            Bitmap thumbnail = Utils.createVideoThumbnailFromUri(getApplicationContext(), uri);
            if (thumbnail == null) {
                Snackbar.make(
                    findViewById(R.id.coordinator_layout),
                    String.format(getResources().getText(R.string.no_thumbnail).toString(), name),
                    Snackbar.LENGTH_LONG
                ).show();
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
        }

        @Override
        protected void onPostExecute(WallpaperCard card) {
            cardAdapter.addCard(cardAdapter.getItemCount(), card);
            Snackbar.make(
                findViewById(R.id.coordinator_layout),
                String.format(
                    getResources().getText(R.string.added_wallpaper).toString(),
                    card.getName()
                ),
                Snackbar.LENGTH_LONG
            ).show();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        cardAdapter = new CardAdapter(this, LWApplication.getCards());

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.setAdapter(cardAdapter);

        layoutInflater = getLayoutInflater();

        addCardFab = findViewById(R.id.addCardFab);
        addCardFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                createAddDialog();
            }
        });

        cancelRemoveCardFab = findViewById(R.id.cancelRemoveCardFab);
        cancelRemoveCardFab.setVisibility(View.GONE);
        cancelRemoveCardFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (cardAdapter.isRemovable()) {
                    cardAdapter.setRemovable(false);
                    hideCancelFab();
                }
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        if (requestCode == SELECT_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            Uri uri = null;
            if (resultData != null) {
                uri = resultData.getData();
                if (uri == null) {
                    return;
                }
                EditText pathEditText = addDialog.findViewById(R.id.path_edit_text);
                if (pathEditText == null) {
                    return;
                }
                pathEditText.setText(uri.toString());
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(
        int requestCode,
        @NonNull String[] permissions,
        @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
        case EXTERNAL_STORAGE_REQUEST_CODE:
            // If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Snackbar.make(
                    findViewById(R.id.coordinator_layout),
                    getResources().getText(R.string.got_read_storage_permission).toString(),
                    Snackbar.LENGTH_LONG
                ).show();
                createAddDialog();
            } else {
                Snackbar.make(
                    findViewById(R.id.coordinator_layout),
                    getResources().getText(R.string.not_got_read_storage_permission).toString(),
                    Snackbar.LENGTH_LONG
                ).show();
                // Show a dialog to tell user we need permission.
                createReadStorageDialog();
            }
            break;
        default:
            break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        MenuItem toogleSlideMenu = menu.findItem(R.id.action_toggle_slide);
        SharedPreferences pref = getSharedPreferences(LWApplication.OPTIONS_PREF, MODE_PRIVATE);
        if (pref.getBoolean(LWApplication.SLIDE_WALLPAPER_KEY, false)) {
            toogleSlideMenu.setTitle(R.string.action_disallow_slide);
        } else {
            toogleSlideMenu.setTitle(R.string.action_allow_slide);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
        case R.id.action_remove:
            cardAdapter.setRemovable(true);
            showCancelFab();
            break;
        case R.id.action_about:
            Intent intent = new Intent(this, AboutActivity.class);
            startActivity(
                intent, ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
            );
            break;
        case R.id.action_toggle_slide:
            SharedPreferences pref = getSharedPreferences(LWApplication.OPTIONS_PREF, MODE_PRIVATE);
            SharedPreferences.Editor editor = pref.edit();
            boolean newValue = !pref.getBoolean(LWApplication.SLIDE_WALLPAPER_KEY, false);
            editor.putBoolean(LWApplication.SLIDE_WALLPAPER_KEY, newValue);
            editor.apply();
            if (newValue) {
                Snackbar.make(
                    findViewById(R.id.coordinator_layout),
                    R.string.slide_warning,
                    Snackbar.LENGTH_LONG
                ).show();
                item.setTitle(R.string.action_disallow_slide);
            } else {
                item.setTitle(R.string.action_allow_slide);
            }
            break;
        default:
            break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        super.onPause();
        WallpaperCard currentWallpaperCard = LWApplication.getCurrentWallpaperCard();
        try {
            JSONObject json = new JSONObject();
            json.put("cards", LWApplication.getCardsJSONArray());
            FileOutputStream fos = openFileOutput(LWApplication.JSON_FILE_NAME, Context.MODE_PRIVATE);
            fos.write(json.toString(2).getBytes());
            fos.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        List<WallpaperCard> cards = LWApplication.getCards();
        try {
            FileInputStream fis = openFileInput(LWApplication.JSON_FILE_NAME);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fis));
            String line = null;
            StringBuilder stringBuilder = new StringBuilder();
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line);
                stringBuilder.append('\n');
            }
            String jsonSource = stringBuilder.toString();
            JSONObject json = new JSONObject(jsonSource);
            JSONArray cardsArray = json.getJSONArray("cards");
            for (int i = 0; i < cardsArray.length(); ++i) {
                String name = cardsArray.getJSONObject(i).getString("name");
                String path = cardsArray.getJSONObject(i).getString("path");
                boolean found = false;
                for (WallpaperCard card : cards) {
                    if (Objects.equals(card.getPath(), path)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    new AddCardTask().execute(name, path);
                }
            }
            bufferedReader.close();
            fis.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void showCancelFab() {
        Animation scaleDown = AnimationUtils.loadAnimation(this, R.anim.scale_down);
        addCardFab.startAnimation(scaleDown);
        addCardFab.setVisibility(View.GONE);
        cancelRemoveCardFab.setVisibility(View.VISIBLE);
        Animation scaleUp = AnimationUtils.loadAnimation(this, R.anim.scale_up);
        cancelRemoveCardFab.startAnimation(scaleUp);
    }

    private void hideCancelFab() {
        Animation scaleDown = AnimationUtils.loadAnimation(this, R.anim.scale_down);
        cancelRemoveCardFab.startAnimation(scaleDown);
        cancelRemoveCardFab.setVisibility(View.GONE);
        addCardFab.setVisibility(View.VISIBLE);
        Animation scaleUp = AnimationUtils.loadAnimation(this, R.anim.scale_up);
        addCardFab.startAnimation(scaleUp);
    }

    private void createAddDialog() {
        if (!checkReadStoragePermissions()) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.add_wallpaper);
        builder.setView(layoutInflater.inflate(R.layout.add_wallpaper_dialog, null));
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                addCard();
            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });
        addDialog = builder.create();
        addDialog.show();
        Button button = addDialog.findViewById(R.id.choose_file_button);
        if (button == null) {
            return;
        }
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("video/*");
                startActivityForResult(intent, SELECT_REQUEST_CODE);
            }
        });
    }

    private void createReadStorageDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.read_storage_title);
        builder.setMessage(R.string.read_storage_info);
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
            }
        });
        AlertDialog readStorageDialog = builder.create();
        readStorageDialog.show();
    }

    private void addCard() {
        EditText nameEditText = addDialog.findViewById(R.id.name_edit_text);
        EditText pathEditText = addDialog.findViewById(R.id.path_edit_text);
        if (nameEditText == null || pathEditText == null) {
            return;
        }
        String name = nameEditText.getText().toString();
        String path = pathEditText.getText().toString();
        if (name.length() == 0 || path.length() == 0) {
            Snackbar.make(
                findViewById(R.id.coordinator_layout),
                R.string.empty_name_or_path,
                Snackbar.LENGTH_LONG
            ).show();
            return;
        }
        new AddCardTask().execute(name, path);
    }

    private boolean checkReadStoragePermissions() {
        if (ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_EXTERNAL_STORAGE
        ) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                EXTERNAL_STORAGE_REQUEST_CODE
            );
            return false;
        }
        return true;
    }
}
