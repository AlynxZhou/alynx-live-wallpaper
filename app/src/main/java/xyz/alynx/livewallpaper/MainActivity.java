package xyz.alynx.livewallpaper;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
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

public class MainActivity extends AppCompatActivity {
    private final static String TAG = "MainActivity";
    private RecyclerView recyclerView = null;
    private CardAdapter cardAdapter = null;
    private AlertDialog dialog = null;
    private LayoutInflater layoutInflater = null;
    private FloatingActionButton addCardFab = null;
    private FloatingActionButton cancelRemoveCardFab = null;
    public static final int SELECT_REQUEST_CODE = 3;

    private class AddCardTask extends AsyncTask<String, Void, WallpaperCard> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            Snackbar.make(
                findViewById(R.id.coordinator_layout),
                getResources().getText(R.string.adding_wallpaper),
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
                if (card.getPath().equals(path)) {
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
            Bitmap thumbnail = Utils.createVideoThumbnailFromUri(
                getApplicationContext(),
                Uri.parse(path)
            );
            if (thumbnail == null) {
                Snackbar.make(
                    findViewById(R.id.coordinator_layout),
                    String.format(getResources().getText(R.string.no_thumbnail).toString(), name),
                    Snackbar.LENGTH_LONG
                ).show();
                cancel(true);
                return null;
            }
            return new WallpaperCard(name, path, WallpaperCard.Type.EXTERNAL, thumbnail);
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

        recyclerView = findViewById(R.id.recycler_view);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.setAdapter(cardAdapter);

        layoutInflater = getLayoutInflater();

        addCardFab = findViewById(R.id.addCardFab);
        addCardFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder builder = new AlertDialog.Builder(view.getContext());
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
                dialog = builder.create();
                dialog.show();
                Button button = dialog.findViewById(R.id.choose_file_button);
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
        });

        cancelRemoveCardFab = findViewById(R.id.cancelRemoveCardFab);
        cancelRemoveCardFab.setVisibility(View.GONE);
        cancelRemoveCardFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (cardAdapter.isRemovable()) {
                    cardAdapter.setRemovable(false);
                    cancelRemoveCardFab.setVisibility(View.GONE);
                    addCardFab.setVisibility(View.VISIBLE);
                }
            }
        });

        Log.d(TAG, "MainActivity created");
    }

    public void addCard() {
        EditText nameEditText = dialog.findViewById(R.id.name_edit_text);
        EditText pathEditText = dialog.findViewById(R.id.path_edit_text);
        if (nameEditText == null || pathEditText == null) {
            // Add notify.
            return;
        }
        String name = nameEditText.getText().toString();
        String path = pathEditText.getText().toString();
        if (name.length() == 0 || path.length() == 0) {
            return;
        }
        new AddCardTask().execute(name, path);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == SELECT_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            Uri uri = null;
            if (resultData != null) {
                uri = resultData.getData();
                if (uri == null) {
                    return;
                }
                Log.i(TAG, "Uri: " + uri.toString());
                EditText pathEditText = dialog.findViewById(R.id.path_edit_text);
                if (pathEditText == null) {
                    return;
                }
                pathEditText.setText(uri.toString());
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_remove) {
            cardAdapter.setRemovable(true);
            cancelRemoveCardFab.setVisibility(View.VISIBLE);
            addCardFab.setVisibility(View.GONE);
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        super.onPause();
        WallpaperCard activateWallpaperCard = LWApplication.getActivateWallpaperCard();
        try {
            JSONObject json = new JSONObject();
            json.put("cards", LWApplication.getCardsJSONArray());
            if (activateWallpaperCard != null &&
                activateWallpaperCard.getType() != WallpaperCard.Type.INTERNAL) {
                json.put("activateWallpaperPath", activateWallpaperCard.getPath());
            }
             FileOutputStream fos = openFileOutput(LWApplication.JSON_FILE_NAME, Context.MODE_PRIVATE);
            fos.write(json.toString(2).getBytes());
            fos.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return;
        } catch (IOException e) {
            e.printStackTrace();
            return;
        } catch (JSONException e) {
            e.printStackTrace();
            return;
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
            for (WallpaperCard card : cards) {
                if (card.getType() == WallpaperCard.Type.EXTERNAL) {
                    cards.remove(card);
                }
            }
            for (int i = 0; i < cardsArray.length(); ++i) {
                new AddCardTask().execute(
                    cardsArray.getJSONObject(i).getString("name"),
                    cardsArray.getJSONObject(i).getString("path")
                );
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return;
        } catch (IOException e) {
            e.printStackTrace();
            return;
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
    }
}
