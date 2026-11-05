package com.laf.lostandfound.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.laf.lostandfound.R;
import com.laf.lostandfound.databinding.ItemCardBinding;
import com.laf.lostandfound.models.Item;
import com.laf.lostandfound.utils.TimeUtils;

import java.util.ArrayList;
import java.util.List;

public class ItemAdapter extends RecyclerView.Adapter<ItemAdapter.ItemViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(Item item, int position);
    }

    private List<Item> items;
    private final OnItemClickListener listener;

    public ItemAdapter(List<Item> items, OnItemClickListener listener) {
        this.items = items != null ? items : new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCardBinding binding = ItemCardBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ItemViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ItemViewHolder holder, int position) {
        Item item = items.get(position);
        holder.bind(item, position);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void updateItems(List<Item> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    class ItemViewHolder extends RecyclerView.ViewHolder {

        private final ItemCardBinding binding;

        ItemViewHolder(@NonNull ItemCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Item item, int position) {
            if (item == null) return;

            // Load image
            com.laf.lostandfound.utils.ImageLoader.loadImage(
                    binding.getRoot().getContext(), item.getImageUrl(), binding.ivImage, 24);

            // Set title
            if (item.getTitle() != null) {
                binding.tvTitle.setText(item.getTitle());
            }

            // Set badge (FOUND / LOST)
            String type = item.getType();
            if (type != null) {
                if (type.equalsIgnoreCase("found")) {
                    binding.tvBadge.setText("FOUND");
                    binding.tvBadge.setBackgroundResource(R.drawable.bg_badge_found);
                } else {
                    binding.tvBadge.setText("LOST");
                    binding.tvBadge.setBackgroundResource(R.drawable.bg_badge_lost);
                }
                binding.tvBadge.setTextColor(0xFFFFFFFF);
            }

            // Set location
            if (item.getLocation() != null && !item.getLocation().isEmpty()) {
                binding.tvLocation.setText(item.getLocation());
                binding.tvLocation.setCompoundDrawablesWithIntrinsicBounds(
                        R.drawable.ic_location_pin, 0, 0, 0);
            }

            // Set time ago
            if (item.getCreatedAt() > 0) {
                binding.tvTime.setText(TimeUtils.getTimeAgo(item.getCreatedAt()));
            }

            // Click listener
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(item, position);
                }
            });
        }
    }
}
