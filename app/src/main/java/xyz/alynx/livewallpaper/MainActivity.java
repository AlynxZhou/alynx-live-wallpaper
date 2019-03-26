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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
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

public class MainActivity extends AppCompatActivity
    implements CardAdapter.OnCardClickedListener, AddCardTask.AddCardTaskListener {
    @SuppressWarnings("unused")
    private static final String TAG = "MainActivity";
    private static final String FIRST_START_PREF = "firstStartPref";
    private static final String SHOWED_TIPS_KEY = "showedTipsKey";
    private static final int SELECT_REQUEST_CODE = 3;
    private static final int PREVIEW_REQUEST_CODE = 7;
    private CoordinatorLayout coordinatorLayout = null;
    private CardAdapter cardAdapter = null;
    private AlertDialog addDialog = null;
    private LayoutInflater layoutInflater = null;
    private FloatingActionButton addCardFab = null;
    private FloatingActionButton cancelRemoveCardFab = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        layoutInflater = getLayoutInflater();

        final SharedPreferences pref = getSharedPreferences(
            FIRST_START_PREF, MODE_PRIVATE
        );
        if (!pref.getBoolean(SHOWED_TIPS_KEY, false)) {
            createTipsDialog();
        }

        cardAdapter = new CardAdapter(
            this, LWApplication.getCards(this), this
        );

        final RecyclerView recyclerView = findViewById(R.id.recycler_view);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.setAdapter(cardAdapter);

        coordinatorLayout = findViewById(R.id.coordinator_layout);

        addCardFab = findViewById(R.id.addCardFab);
        addCardFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                createAddDialog();
            }
        });

        cancelRemoveCardFab = findViewById(R.id.cancelRemoveCardFab);
        cancelRemoveCardFab.hide();
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
            Uri uri;
            if (resultData != null) {
                uri = resultData.getData();
                if (uri == null) {
                    return;
                }
                getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                );
                final EditText pathEditText = addDialog.findViewById(R.id.path_edit_text);
                if (pathEditText == null) {
                    return;
                }
                pathEditText.setText(uri.toString());
            }
        } else if (requestCode == PREVIEW_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                LWApplication.setCurrentWallpaperCard(
                    this, LWApplication.getPreviewWallpaperCard()
                );
                // Rebind adapter.
                cardAdapter.notifyDataSetChanged();
            }
            // Don't forget to delete preview card.
            LWApplication.setPreviewWallpaperCard(null);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        final MenuItem toggleSlideMenuItem = menu.findItem(R.id.action_toggle_slide);
        final SharedPreferences pref = getSharedPreferences(
            LWApplication.OPTIONS_PREF, MODE_PRIVATE
        );
        if (pref.getBoolean(LWApplication.SLIDE_WALLPAPER_KEY, false)) {
            toggleSlideMenuItem.setTitle(R.string.action_disallow_slide);
        } else {
            toggleSlideMenuItem.setTitle(R.string.action_allow_slide);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
        case R.id.action_toggle_slide: {
            final SharedPreferences pref = getSharedPreferences(
                LWApplication.OPTIONS_PREF, MODE_PRIVATE
            );
            final SharedPreferences.Editor editor = pref.edit();
            final boolean newValue = !pref.getBoolean(LWApplication.SLIDE_WALLPAPER_KEY, false);
            editor.putBoolean(LWApplication.SLIDE_WALLPAPER_KEY, newValue);
            editor.apply();
            if (newValue) {
                Snackbar.make(
                    coordinatorLayout,
                    R.string.slide_warning,
                    Snackbar.LENGTH_LONG
                ).show();
                item.setTitle(R.string.action_disallow_slide);
            } else {
                item.setTitle(R.string.action_allow_slide);
            }
            break;
        }
        case R.id.action_remove: {
            Snackbar.make(
                coordinatorLayout,
                R.string.remove_tips,
                Snackbar.LENGTH_LONG
            ).show();
            cardAdapter.setRemovable(true);
            showCancelFab();
            break;
        }
        case R.id.action_tips: {
            createTipsDialog();
            break;
        }
        case R.id.action_reward: {
            final Intent intent = new Intent(this, RewardActivity.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                startActivity(
                    intent, ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
                );
            } else {
                startActivity(intent);
            }
            break;
        }
        case R.id.action_about: {
            final Intent intent = new Intent(this, AboutActivity.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                startActivity(
                    intent, ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
                );
            } else {
                startActivity(intent);
            }
            break;
        }
        default:
            break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            final JSONObject json = new JSONObject();
            json.put("cards", LWApplication.getCardsJSONArray());
            final FileOutputStream fos = openFileOutput(LWApplication.JSON_FILE_NAME, Context.MODE_PRIVATE);
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
        Utils.debug(TAG, "Resumed");
        final WallpaperInfo info = WallpaperManager.getInstance(this).getWallpaperInfo();
        if (info == null || !Objects.equals(info.getPackageName(), getPackageName())) {
            LWApplication.setCurrentWallpaperCard(this, null);
            // Rebind adapter.
            cardAdapter.notifyDataSetChanged();
        }
        List<WallpaperCard> cards = LWApplication.getCards(this);
        try {
            final FileInputStream fis = openFileInput(LWApplication.JSON_FILE_NAME);
            final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fis));
            String line;
            final StringBuilder stringBuilder = new StringBuilder();
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line);
                stringBuilder.append('\n');
            }
            final String jsonSource = stringBuilder.toString();
            final JSONObject json = new JSONObject(jsonSource);
            final JSONArray cardsArray = json.getJSONArray("cards");
            for (int i = 0; i < cardsArray.length(); ++i) {
                final String name = cardsArray.getJSONObject(i).getString("name");
                final String path = cardsArray.getJSONObject(i).getString("path");
                boolean found = false;
                for (WallpaperCard card : cards) {
                    if (Objects.equals(card.getPath(), path)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    new AddCardTask(this, this).execute(name, path);
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

    @Override
    public void onCardClicked(@NonNull final WallpaperCard wallpaperCard) {
        // When card is clicked we go to preview mode.
        LWApplication.setPreviewWallpaperCard(wallpaperCard);
        final Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
        intent.putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            new ComponentName(this, GLWallpaperService.class)
        );
        // There is a problem, that after you choose "Set to desktop (and lock screen)",
        // the preview activity does not back to MainActivity directly,
        // instead, it backs to desktop and then back to MainActivity (very quick, invisible),
        // and in this time GLWallpaperService starts with no LWApplication.currentWallpaperCard,
        // no record in SharedPreference if you run app for the first time.
        // To solve this, we let it fallback to internal wallpaper when it gets null from record,
        // and because it will be back to MainActivity very quick,
        // currentWallpaperCard will be set after that, and you'll see it next desktop appearing.
        startActivityForResult(intent, PREVIEW_REQUEST_CODE);
    }

    @Override
    public void onApplyButtonClicked(@NonNull final WallpaperCard wallpaperCard) {
        final WallpaperInfo info = WallpaperManager.getInstance(this).getWallpaperInfo();
        if (info == null || !Objects.equals(info.getPackageName(), getPackageName())) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.choose_wallpaper_title);
            builder.setMessage(R.string.choose_wallpaper);
            builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    // Only after user click OK, we change currentWallpaperCard.
                    LWApplication.setCurrentWallpaperCard(getApplicationContext(), wallpaperCard);
                    cardAdapter.notifyDataSetChanged();
                    LWApplication.setPreviewWallpaperCard(wallpaperCard);
                    Intent intent = new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER);
                    startActivity(intent);
                }
            });
            addDialog = builder.create();
            addDialog.show();
        } else {
            LWApplication.setCurrentWallpaperCard(this, wallpaperCard);
            cardAdapter.notifyDataSetChanged();
            // Display a notice for user.
            Snackbar.make(
                coordinatorLayout,
                String.format(
                    getResources().getString(R.string.applied_wallpaper),
                    wallpaperCard.getName()
                ),
                Snackbar.LENGTH_LONG
            ).show();
        }
    }

    @Override
    public void onCardInvalid(@NonNull final WallpaperCard wallpaperCard) {
        Snackbar.make(
            coordinatorLayout,
            String.format(
                getResources().getString(R.string.removed_invalid_card),
                wallpaperCard.getName()
            ),
            Snackbar.LENGTH_LONG
        ).show();
    }

    @Override
    public void onPreExecute(final String message) {
        if (message != null) {
            Snackbar.make(
                coordinatorLayout,
                message,
                Snackbar.LENGTH_LONG
            ).show();
        }
    }

    @Override
    public void onPostExecute(final String message, @NonNull WallpaperCard card) {
        final List<WallpaperCard> cards = LWApplication.getCards(getApplicationContext());
        for (WallpaperCard wallpaperCard : cards) {
            if (card.equals(wallpaperCard)) {
                Snackbar.make(
                    coordinatorLayout,
                    String.format(
                        getResources().getString(R.string.same_wallpaper),
                        wallpaperCard.getName(), card.getName()
                    ),
                    Snackbar.LENGTH_LONG
                ).show();
                return;
            }
        }
        cardAdapter.addCard(card);
        if (message != null) {
            Snackbar.make(
                coordinatorLayout,
                message,
                Snackbar.LENGTH_LONG
            ).show();
        }
    }

    @Override
    public void onCancelled(final String message) {
        if (message != null) {
            Snackbar.make(
                coordinatorLayout,
                message,
                Snackbar.LENGTH_LONG
            ).show();
        }
    }

    private void showCancelFab() {
        addCardFab.hide();
        cancelRemoveCardFab.show();
    }

    private void hideCancelFab() {
        cancelRemoveCardFab.hide();
        addCardFab.show();
    }

    @SuppressLint("InflateParams")
    private void createAddDialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.add_wallpaper);
        builder.setView(layoutInflater.inflate(R.layout.add_wallpaper_dialog, null));
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                onAddCardConfirmed();
            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });
        addDialog = builder.create();
        addDialog.show();
        final Button button = addDialog.findViewById(R.id.choose_file_button);
        if (button == null) {
            return;
        }
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                // Without those flags some phone won't let you read, for example Huawei.
                intent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION |
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                );
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("video/*");
                startActivityForResult(intent, SELECT_REQUEST_CODE);
            }
        });
    }

    @SuppressLint("InflateParams")
    private void createTipsDialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.action_tips);
        builder.setView(layoutInflater.inflate(R.layout.tips_dialog, null));
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                final SharedPreferences pref = getSharedPreferences(
                    FIRST_START_PREF, MODE_PRIVATE
                );
                final SharedPreferences.Editor prefEditor = pref.edit();
                if (!pref.getBoolean(SHOWED_TIPS_KEY, false)) {
                    prefEditor.putBoolean(SHOWED_TIPS_KEY, true);
                }
                prefEditor.apply();
            }
        });
        addDialog = builder.create();
        addDialog.show();
    }

    private void onAddCardConfirmed() {
        final EditText nameEditText = addDialog.findViewById(R.id.name_edit_text);
        final EditText pathEditText = addDialog.findViewById(R.id.path_edit_text);
        if (nameEditText == null || pathEditText == null) {
            return;
        }
        final String name = nameEditText.getText().toString();
        final String path = pathEditText.getText().toString();
        if (name.length() == 0 || path.length() == 0) {
            Snackbar.make(
                coordinatorLayout,
                R.string.empty_name_or_path,
                Snackbar.LENGTH_LONG
            ).show();
            return;
        }
        new AddCardTask(this, this).execute(name, path);
    }
}
