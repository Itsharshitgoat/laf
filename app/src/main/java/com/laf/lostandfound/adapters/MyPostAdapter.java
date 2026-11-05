package com.laf.lostandfound.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.laf.lostandfound.R;
import com.laf.lostandfound.databinding.ItemMyPostBinding;
import com.laf.lostandfound.models.Item;

import java.util.ArrayList;
import java.util.List;

public class MyPostAdapter extends RecyclerView.Adapter<MyPostAdapter.MyPostViewHolder> {

    public interface OnPostClickListener {
        void onPostClick(Item item, int position);
    }

    private List<Item> items;
    private final OnPostClickListener listener;

    public MyPostAdapter(List<Item> items, OnPostClickListener listener) {
        this.items = items != null ? items : new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public MyPostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMyPostBinding binding = ItemMyPostBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new MyPostViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MyPostViewHolder holder, int position) {
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

    class MyPostViewHolder extends RecyclerView.ViewHolder {

        private final ItemMyPostBinding binding;

        MyPostViewHolder(@NonNull ItemMyPostBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Item item, int position) {
            if (item == null) return;

            // Load image
            com.laf.lostandfound.utils.ImageLoader.loadImage(
                    binding.getRoot().getContext(), item.getImageUrl(), binding.ivPostImage, 16);

            // Set title
            if (item.getTitle() != null) {
                binding.tvPostTitle.setText(item.getTitle());
            }

            // Set badge
            String type = item.getType();
            if (type != null) {
                if (type.equalsIgnoreCase("found")) {
                    binding.tvPostBadge.setText("FOUND");
                    binding.tvPostBadge.setBackgroundResource(R.drawable.bg_badge_found);
                } else {
                    binding.tvPostBadge.setText("LOST");
                    binding.tvPostBadge.setBackgroundResource(R.drawable.bg_badge_lost);
                }
                binding.tvPostBadge.setTextColor(0xFFFFFFFF);
            }

            // Click listener
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPostClick(item, position);
                }
            });
        }
    }
}
