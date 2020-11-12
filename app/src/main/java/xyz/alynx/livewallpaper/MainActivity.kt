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
package xyz.alynx.livewallpaper

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import org.json.JSONException
import org.json.JSONObject
import xyz.alynx.livewallpaper.AddCardTask.AddCardTaskListener
import xyz.alynx.livewallpaper.CardAdapter.OnCardClickedListener
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStreamReader

class MainActivity : AppCompatActivity(), OnCardClickedListener, AddCardTaskListener {
    private lateinit var coordinatorLayout: CoordinatorLayout
    private lateinit var cardAdapter: CardAdapter
    private var addDialog: AlertDialog? = null
    private lateinit var addCardFab: FloatingActionButton
    private lateinit var cancelRemoveCardFab: FloatingActionButton
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        val pref = getSharedPreferences(
                FIRST_START_PREF, MODE_PRIVATE
        )
        if (!pref.getBoolean(SHOWED_TIPS_KEY, false)) {
            createTipsDialog()
        }
        cardAdapter = CardAdapter(
                this, LWApplication.getCards(this), this
        )
        val recyclerView = findViewById<RecyclerView>(R.id.recycler_view)
        val layoutManager: RecyclerView.LayoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager
        recyclerView.itemAnimator = DefaultItemAnimator()
        recyclerView.adapter = cardAdapter
        coordinatorLayout = findViewById(R.id.coordinator_layout)
        addCardFab = findViewById(R.id.addCardFab)
        addCardFab.setOnClickListener { createAddDialog() }
        cancelRemoveCardFab = findViewById(R.id.cancelRemoveCardFab)
        cancelRemoveCardFab.hide()
        cancelRemoveCardFab.setOnClickListener {
            if (cardAdapter.isRemovable()) {
                cardAdapter.setRemovable(false)
                hideCancelFab()
            }
        }
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == SELECT_REQUEST_CODE && resultCode == RESULT_OK) {
            val uri: Uri?
            if (resultData != null) {
                uri = resultData.data
                if (uri == null) {
                    return
                }
                contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                val nameEditText = addDialog!!.findViewById<EditText>(R.id.name_edit_text)
                val pathEditText = addDialog!!.findViewById<EditText>(R.id.path_edit_text)
                if (pathEditText == null || nameEditText == null) {
                    return
                }
                pathEditText.setText(uri.toString())
                if (uri.scheme == "file") {
                    nameEditText.setText(uri.lastPathSegment)
                } else {
                    contentResolver.query(
                            uri, null, null, null, null
                    )?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        cursor.moveToFirst()
                        val name = cursor.getString(nameIndex)
                        if (name != null && name.isNotEmpty()) {
                            nameEditText.setText(name)
                        }
                    } ?: return
                }
            }
        } else if (requestCode == PREVIEW_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                LWApplication.setCurrentWallpaperCard(
                        this, LWApplication.previewWallpaperCard
                )
                // Rebind adapter.
                cardAdapter.notifyDataSetChanged()
            }
            // Don't forget to delete preview card.
            LWApplication.previewWallpaperCard = null
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val toggleSlideMenuItem = menu.findItem(R.id.action_toggle_slide)
        val pref = getSharedPreferences(
                LWApplication.OPTIONS_PREF, MODE_PRIVATE
        )
        if (pref.getBoolean(LWApplication.SLIDE_WALLPAPER_KEY, false)) {
            toggleSlideMenuItem.setTitle(R.string.action_disallow_slide)
        } else {
            toggleSlideMenuItem.setTitle(R.string.action_allow_slide)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_toggle_slide -> {
                val pref = getSharedPreferences(
                        LWApplication.OPTIONS_PREF, MODE_PRIVATE
                )
                val editor = pref.edit()
                val newValue = !pref.getBoolean(LWApplication.SLIDE_WALLPAPER_KEY, false)
                editor.putBoolean(LWApplication.SLIDE_WALLPAPER_KEY, newValue)
                editor.apply()
                if (newValue) {
                    Snackbar.make(
                            coordinatorLayout,
                            R.string.slide_warning,
                            Snackbar.LENGTH_LONG
                    ).show()
                    item.setTitle(R.string.action_disallow_slide)
                } else {
                    item.setTitle(R.string.action_allow_slide)
                }
            }
            R.id.action_remove -> {
                Snackbar.make(
                        coordinatorLayout,
                        R.string.remove_tips,
                        Snackbar.LENGTH_LONG
                ).show()
                cardAdapter.setRemovable(true)
                showCancelFab()
            }
            R.id.action_tips -> {
                createTipsDialog()
            }
            R.id.action_about -> {
                val intent = Intent(this, AboutActivity::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    startActivity(
                            intent, ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
                    )
                } else {
                    startActivity(intent)
                }
            }
            else -> {
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onPause() {
        super.onPause()
        try {
            val json = JSONObject()
            json.put("cards", LWApplication.cardsJSONArray)
            val fos = openFileOutput(LWApplication.JSON_FILE_NAME, MODE_PRIVATE)
            fos.write(json.toString(2).toByteArray())
            fos.close()
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        Utils.debug(TAG, "Resumed")
        val info = WallpaperManager.getInstance(this).wallpaperInfo
        if (info == null || info.packageName != packageName) {
            LWApplication.setCurrentWallpaperCard(this, null)
            // Rebind adapter.
            cardAdapter.notifyDataSetChanged()
        }
        val cards: List<WallpaperCard> = LWApplication.getCards(this)
        try {
            val fis = openFileInput(LWApplication.JSON_FILE_NAME)
            val bufferedReader = BufferedReader(InputStreamReader(fis))
            var line: String?
            val stringBuilder = StringBuilder()
            while (bufferedReader.readLine().also { line = it } != null) {
                stringBuilder.append(line)
                stringBuilder.append('\n')
            }
            val jsonSource = stringBuilder.toString()
            val json = JSONObject(jsonSource)
            val cardsArray = json.getJSONArray("cards")
            for (i in 0 until cardsArray.length()) {
                val name = cardsArray.getJSONObject(i).getString("name")
                val path = cardsArray.getJSONObject(i).getString("path")
                var found = false
                for (card in cards) {
                    if (card.path == path) {
                        found = true
                        break
                    }
                }
                if (!found) {
                    AddCardTask(this, this).execute(name, path)
                }
            }
            bufferedReader.close()
            fis.close()
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    override fun onCardClicked(wallpaperCard: WallpaperCard) {
        // When card is clicked we go to preview mode.
        LWApplication.previewWallpaperCard = wallpaperCard
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        intent.putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(this, GLWallpaperService::class.java)
        )
        // There is a problem, that after you choose "Set to desktop (and lock screen)",
        // the preview activity does not back to MainActivity directly,
        // instead, it backs to desktop and then back to MainActivity (very quick, invisible),
        // and in this time GLWallpaperService starts with no LWApplication.currentWallpaperCard,
        // no record in SharedPreference if you run app for the first time.
        // To solve this, we let it fallback to internal wallpaper when it gets null from record,
        // and because it will be back to MainActivity very quick,
        // currentWallpaperCard will be set after that, and you'll see it next desktop appearing.
        startActivityForResult(intent, PREVIEW_REQUEST_CODE)
    }

    override fun onApplyButtonClicked(wallpaperCard: WallpaperCard) {
        val info = WallpaperManager.getInstance(this).wallpaperInfo
        if (info == null || info.packageName != packageName) {
            val builder = AlertDialog.Builder(this)
            builder.setTitle(R.string.choose_wallpaper_title)
            builder.setMessage(R.string.choose_wallpaper)
            builder.setPositiveButton(R.string.ok) { dialog, id -> // Only after user click OK, we change currentWallpaperCard.
                LWApplication.setCurrentWallpaperCard(applicationContext, wallpaperCard)
                cardAdapter.notifyDataSetChanged()
                LWApplication.previewWallpaperCard = wallpaperCard
                val intent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
                startActivity(intent)
            }
            addDialog = builder.create()
            addDialog!!.show()
        } else {
            LWApplication.setCurrentWallpaperCard(this, wallpaperCard)
            cardAdapter.notifyDataSetChanged()
            // Display a notice for user.
            Snackbar.make(
                    coordinatorLayout, String.format(
                    resources.getString(R.string.applied_wallpaper),
                    wallpaperCard.name
            ),
                    Snackbar.LENGTH_LONG
            ).show()
        }
    }

    override fun onCardInvalid(wallpaperCard: WallpaperCard) {
        Snackbar.make(
                coordinatorLayout, String.format(
                resources.getString(R.string.removed_invalid_card),
                wallpaperCard.name
        ),
                Snackbar.LENGTH_LONG
        ).show()
    }

    override fun onPreExecute(message: String?) {
        if (message != null) {
            Snackbar.make(
                    coordinatorLayout,
                    message,
                    Snackbar.LENGTH_LONG
            ).show()
        }
    }

    override fun onPostExecute(message: String?, card: WallpaperCard) {
        val cards: List<WallpaperCard> = LWApplication.getCards(applicationContext)
        for (wallpaperCard in cards) {
            if (card == wallpaperCard) {
                Snackbar.make(
                        coordinatorLayout, String.format(
                        resources.getString(R.string.same_wallpaper),
                        wallpaperCard.name, card.name
                ),
                        Snackbar.LENGTH_LONG
                ).show()
                return
            }
        }
        cardAdapter.addCard(card)
        if (message != null) {
            Snackbar.make(
                    coordinatorLayout,
                    message,
                    Snackbar.LENGTH_LONG
            ).show()
        }
    }

    override fun onCancelled(message: String?) {
        if (message != null) {
            Snackbar.make(
                    coordinatorLayout,
                    message,
                    Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun showCancelFab() {
        addCardFab.hide()
        cancelRemoveCardFab.show()
    }

    private fun hideCancelFab() {
        cancelRemoveCardFab.hide()
        addCardFab.show()
    }

    @SuppressLint("InflateParams")
    private fun createAddDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.add_wallpaper)
        builder.setView(layoutInflater.inflate(R.layout.add_wallpaper_dialog, null))
        builder.setPositiveButton(R.string.ok) { dialog, id -> onAddCardConfirmed() }
        builder.setNegativeButton(R.string.cancel) { dialog, id -> dialog.cancel() }
        addDialog = builder.create()
        addDialog!!.show()
        val button = addDialog!!.findViewById<Button>(R.id.choose_file_button) ?: return
        button.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            // Without those flags some phone won't let you read, for example Huawei.
            intent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "video/*"
            startActivityForResult(intent, SELECT_REQUEST_CODE)
        }
    }

    @SuppressLint("InflateParams")
    private fun createTipsDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.action_tips)
        builder.setView(layoutInflater.inflate(R.layout.tips_dialog, null))
        builder.setPositiveButton(R.string.ok) { dialog, id ->
            val pref = getSharedPreferences(
                    FIRST_START_PREF, MODE_PRIVATE
            )
            val prefEditor = pref.edit()
            if (!pref.getBoolean(SHOWED_TIPS_KEY, false)) {
                prefEditor.putBoolean(SHOWED_TIPS_KEY, true)
            }
            prefEditor.apply()
        }
        addDialog = builder.create()
        addDialog!!.show()
    }

    private fun onAddCardConfirmed() {
        val nameEditText = addDialog!!.findViewById<EditText>(R.id.name_edit_text)
        val pathEditText = addDialog!!.findViewById<EditText>(R.id.path_edit_text)
        if (nameEditText == null || pathEditText == null) {
            return
        }
        val name = nameEditText.text.toString()
        val path = pathEditText.text.toString()
        if (name.isEmpty() || path.isEmpty()) {
            Snackbar.make(
                    coordinatorLayout,
                    R.string.empty_name_or_path,
                    Snackbar.LENGTH_LONG
            ).show()
            return
        }
        AddCardTask(this, this).execute(name, path)
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val FIRST_START_PREF = "firstStartPref"
        private const val SHOWED_TIPS_KEY = "showedTipsKey"
        private const val SELECT_REQUEST_CODE = 3
        private const val PREVIEW_REQUEST_CODE = 7
    }
}
