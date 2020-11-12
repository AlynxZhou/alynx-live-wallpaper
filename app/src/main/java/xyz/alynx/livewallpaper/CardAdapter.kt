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

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

internal class CardAdapter(
        private val context: Context,
        private val cards: MutableList<WallpaperCard>,
        private val listener: OnCardClickedListener
) : RecyclerView.Adapter<CardViewHolder>() {
    private var removable = false

    interface OnCardClickedListener {
        fun onCardClicked(wallpaperCard: WallpaperCard)
        fun onApplyButtonClicked(wallpaperCard: WallpaperCard)
        fun onCardInvalid(wallpaperCard: WallpaperCard)
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): CardViewHolder {
        val itemView = LayoutInflater.from(viewGroup.context).inflate(
                R.layout.wallpaper_card, viewGroup, false
        )
        return CardViewHolder(itemView)
    }

    override fun onBindViewHolder(cardViewHolder: CardViewHolder, i: Int) {
        val card = cards[cardViewHolder.layoutPosition]
        if (!card.isValid) {
            listener.onCardInvalid(card)
            removeCard(cardViewHolder.layoutPosition)
            return
        }
        cardViewHolder.name.text = card.name
        cardViewHolder.path.text = card.path
        if (card.type == WallpaperCard.Type.INTERNAL) {
            cardViewHolder.internal.visibility = View.VISIBLE
        } else {
            cardViewHolder.internal.visibility = View.GONE
        }
        if (card.isCurrent) {
            cardViewHolder.current.visibility = View.VISIBLE
            cardViewHolder.applyButton.visibility = View.GONE
        } else {
            cardViewHolder.current.visibility = View.GONE
            cardViewHolder.applyButton.visibility = View.VISIBLE
        }
        cardViewHolder.thumbnail.setImageBitmap(card.thumbnail)
        if (removable && card.isRemovable && !card.isCurrent) {
            cardViewHolder.removeButton.visibility = View.VISIBLE
        } else {
            cardViewHolder.removeButton.visibility = View.GONE
        }
        cardViewHolder.removeButton.setOnClickListener { removeCard(cardViewHolder.layoutPosition) }
        cardViewHolder.applyButton.setOnClickListener { listener.onApplyButtonClicked(card) }
        cardViewHolder.thumbnail.setOnClickListener { listener.onCardClicked(card) }
    }

    override fun getItemCount(): Int {
        return cards.size
    }

    fun addCard(wallpaperCard: WallpaperCard) {
        val position = cards.size
        cards.add(position, wallpaperCard)
        notifyItemInserted(position)
    }

    private fun removeCard(position: Int) {
        notifyItemRemoved(position)
        context.contentResolver.releasePersistableUriPermission(
                cards[position].uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        cards.removeAt(position)
    }

    fun setRemovable(removable: Boolean) {
        this.removable = removable
        notifyDataSetChanged()
    }

    fun isRemovable(): Boolean {
        return removable
    }
}

internal class CardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val thumbnail: ImageView = view.findViewById(R.id.thumbnail)
    val name: TextView = view.findViewById(R.id.name)
    val path: TextView = view.findViewById(R.id.path)
    val internal: Button = view.findViewById(R.id.internal)
    val current: Button = view.findViewById(R.id.current)
    val removeButton: Button = view.findViewById(R.id.remove_button)
    val applyButton: Button = view.findViewById(R.id.apply_button)
}
