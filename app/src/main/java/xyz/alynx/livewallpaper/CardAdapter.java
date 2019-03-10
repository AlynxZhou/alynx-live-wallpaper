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

import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

public class CardAdapter extends RecyclerView.Adapter<CardViewHolder> {
    private final static String TAG = "CardAdapter";
    private Context context = null;
    private List<WallpaperCard> cards = null;
    private boolean removable = false;

    public CardAdapter(Context context, List<WallpaperCard> cards) {
        super();
        this.context = context;
        this.cards = cards;
    }

    @NonNull
    @Override
    public CardViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View itemView = LayoutInflater.from(viewGroup.getContext()).inflate(
            R.layout.wallpaper_card, viewGroup, false
        );
        return new CardViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull final CardViewHolder cardViewHolder, int i) {
        WallpaperCard card = cards.get(cardViewHolder.getLayoutPosition());
        cardViewHolder.name.setText(card.getName());
        cardViewHolder.path.setText(card.getPath());
        if (card.getType() == WallpaperCard.Type.INTERNAL) {
            cardViewHolder.internal.setVisibility(View.VISIBLE);
        } else {
            cardViewHolder.internal.setVisibility(View.GONE);
        }
        cardViewHolder.thumbnail.setImageBitmap(card.getThumbnail());
        if (removable && card.isRemovable()) {
            cardViewHolder.removeButton.setVisibility(View.VISIBLE);
        } else {
            cardViewHolder.removeButton.setVisibility(View.GONE);
        }
        cardViewHolder.removeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                removeCard(cardViewHolder.getLayoutPosition());
            }
        });
        cardViewHolder.thumbnail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                List<WallpaperCard> cards = LWApplication.getCards();
                LWApplication.setActivateWallpaperCard(
                    cards.get(cardViewHolder.getLayoutPosition())
                );
                Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
                intent.putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    new ComponentName(view.getContext(), GLWallpaperService.class)
                );
                view.getContext().startActivity(intent);
            }
        });
    }

    @Override
    public int getItemCount() {
        return cards.size();
    }

    public void addCard(int position, WallpaperCard card) {
        cards.add(position, card);
        notifyItemInserted(position);
    }

    public void removeCard(int position) {
        notifyItemRemoved(position);
        context.getContentResolver().releasePersistableUriPermission(
                Uri.parse(cards.get(position).getPath()),
                Intent.FLAG_GRANT_READ_URI_PERMISSION
        );
        cards.remove(position);
    }

    public void setRemovable(boolean removable) {
        this.removable = removable;
        notifyDataSetChanged();
    }

    public boolean isRemovable() {
        return removable;
    }
}

class CardViewHolder extends RecyclerView.ViewHolder {
    private final static String TAG = "CardViewHolder";
    public CardView cardView = null;
    public ImageView thumbnail = null;
    public TextView name = null;
    public TextView path = null;
    public Button internal = null;
    public Button removeButton = null;

    public CardViewHolder(View view) {
        super(view);
        cardView = view.findViewById(R.id.wallpaper_card);
        thumbnail = view.findViewById(R.id.thumbnail);
        name = view.findViewById(R.id.name);
        name.setMovementMethod(new ScrollingMovementMethod());
        path = view.findViewById(R.id.path);
        path.setMovementMethod(new ScrollingMovementMethod());
        internal = view.findViewById(R.id.internal);
        removeButton = view.findViewById(R.id.remove_button);
    }
}
