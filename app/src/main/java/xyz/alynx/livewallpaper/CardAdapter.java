package xyz.alynx.livewallpaper;

import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

public class CardAdapter extends RecyclerView.Adapter<CardViewHolder> {
    private final static String TAG = "CardAdapter";
    private List<WallpaperCard> cards = null;
    private boolean removable = false;

    public CardAdapter(Context context, List<WallpaperCard> cards) {
        super();
        this.cards = cards;
    }

    @NonNull
    @Override
    public CardViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View itemView = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.wallpaper_card, viewGroup, false);
        return new CardViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull final CardViewHolder cardViewHolder, int i) {
        WallpaperCard card = cards.get(cardViewHolder.getLayoutPosition());
        Log.d(TAG, "Bound card: " + cardViewHolder.getLayoutPosition());
        cardViewHolder.name.setText(card.getName());
        cardViewHolder.path.setText(card.getPath());
        if (card.getType() == WallpaperCard.Type.INTERNAL) {
            cardViewHolder.internal.setVisibility(View.VISIBLE);
        } else {
            cardViewHolder.internal.setVisibility(View.INVISIBLE);
        }
        cardViewHolder.thumbnail.setImageBitmap(card.getThumbnail());
        if (removable && card.getType() != WallpaperCard.Type.INTERNAL) {
            cardViewHolder.removeButton.setVisibility(View.VISIBLE);
        } else {
            cardViewHolder.removeButton.setVisibility(View.INVISIBLE);
        }
        cardViewHolder.removeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                removeCard(cardViewHolder.getLayoutPosition());
            }
        });
        cardViewHolder.cardView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "Card " + cardViewHolder.getLayoutPosition() + " clicked");
                List<WallpaperCard> cards = LWApplication.getCards();
                LWApplication.setActivateWallpaperCard(cards.get(cardViewHolder.getLayoutPosition()));
                Log.d(TAG, "Set activate WallpaperCard to " + cardViewHolder.getLayoutPosition());
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
        notifyDataSetChanged();
    }

    public void removeCard(int position) {
        cards.remove(position);
        notifyDataSetChanged();
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
    public TextView name = null;
    public TextView path = null;
    public TextView internal = null;
    public ImageView thumbnail = null;
    public Button removeButton = null;

    public CardViewHolder(View view) {
        super(view);
        cardView = view.findViewById(R.id.wallpaper_card);
        name = view.findViewById(R.id.name);
        path = view.findViewById(R.id.path);
        internal = view.findViewById(R.id.internal);
        thumbnail = view.findViewById(R.id.thumbnail);
        removeButton = view.findViewById(R.id.remove_button);
    }
}
