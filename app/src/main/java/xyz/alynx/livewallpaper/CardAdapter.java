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

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
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
    private static final String TAG = "CardAdapter";
    private Context context;
    private List<WallpaperCard> cards;
    private OnCardClickedListener listener;
    private boolean removable = false;

    public interface OnCardClickedListener {
        void onCardClicked(@NonNull final WallpaperCard wallpaperCard);
        void onUseButtonClicked(@NonNull final WallpaperCard wallpaperCard);
        void onCardInvalid(@NonNull final WallpaperCard wallpaperCard);
    }

    CardAdapter(
        @NonNull final Context context,
        @NonNull final List<WallpaperCard> cards,
        @NonNull final OnCardClickedListener listener
    ) {
        super();
        this.context = context;
        this.cards = cards;
        this.listener = listener;
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
        final WallpaperCard card = cards.get(cardViewHolder.getLayoutPosition());
        if (!card.isValid()) {
            listener.onCardInvalid(card);
            removeCard(cardViewHolder.getLayoutPosition());
            return;
        }
        cardViewHolder.name.setText(card.getName());
        cardViewHolder.path.setText(card.getPath());
        if (card.getType() == WallpaperCard.Type.INTERNAL) {
            cardViewHolder.internal.setVisibility(View.VISIBLE);
        } else {
            cardViewHolder.internal.setVisibility(View.GONE);
        }
        cardViewHolder.thumbnail.setImageBitmap(card.getThumbnail());
        if (removable && card.isRemovable() && !card.isCurrent()) {
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
        cardViewHolder.useButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final List<WallpaperCard> cards = LWApplication.getCards();
                listener.onUseButtonClicked(cards.get(cardViewHolder.getLayoutPosition()));
            }
        });
        cardViewHolder.thumbnail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final List<WallpaperCard> cards = LWApplication.getCards();
                listener.onCardClicked(cards.get(cardViewHolder.getLayoutPosition()));
            }
        });
    }

    @Override
    public int getItemCount() {
        return cards.size();
    }

    void addCard(final int position, @NonNull final WallpaperCard card) {
        cards.add(position, card);
        notifyItemInserted(position);
    }

    private void removeCard(final int position) {
        notifyItemRemoved(position);
        context.getContentResolver().releasePersistableUriPermission(
            cards.get(position).getUri(),
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        );
        cards.remove(position);
    }

    void setRemovable(final boolean removable) {
        this.removable = removable;
        notifyDataSetChanged();
    }

    boolean isRemovable() {
        return removable;
    }
}

class CardViewHolder extends RecyclerView.ViewHolder {
    private static final String TAG = "CardViewHolder";
    ImageView thumbnail;
    public TextView name;
    public TextView path;
    Button internal;
    Button removeButton;
    Button useButton;

    CardViewHolder(View view) {
        super(view);
        thumbnail = view.findViewById(R.id.thumbnail);
        name = view.findViewById(R.id.name);
        name.setMovementMethod(new ScrollingMovementMethod());
        path = view.findViewById(R.id.path);
        path.setMovementMethod(new ScrollingMovementMethod());
        internal = view.findViewById(R.id.internal);
        removeButton = view.findViewById(R.id.remove_button);
        useButton = view.findViewById(R.id.use_button);
    }
}
