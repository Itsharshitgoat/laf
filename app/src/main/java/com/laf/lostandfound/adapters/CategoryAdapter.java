package com.laf.lostandfound.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.laf.lostandfound.R;
import com.laf.lostandfound.databinding.ItemCategoryChipBinding;
import com.laf.lostandfound.models.Category;

import java.util.ArrayList;
import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    public interface OnCategoryClickListener {
        void onCategoryClick(Category category, int position);
    }

    private final List<Category> categories;
    private final OnCategoryClickListener listener;
    private int selectedPosition = 0;

    public CategoryAdapter(List<Category> categories, OnCategoryClickListener listener) {
        this.categories = categories != null ? categories : new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCategoryChipBinding binding = ItemCategoryChipBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new CategoryViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        Category category = categories.get(position);
        holder.bind(category, position);
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    public void setSelectedPosition(int position) {
        int previousSelected = selectedPosition;
        selectedPosition = position;
        notifyItemChanged(previousSelected);
        notifyItemChanged(selectedPosition);
    }

    class CategoryViewHolder extends RecyclerView.ViewHolder {

        private final ItemCategoryChipBinding binding;

        CategoryViewHolder(@NonNull ItemCategoryChipBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Category category, int position) {
            if (category == null) return;

            binding.tvCategoryName.setText(category.getName());

            if (position == selectedPosition) {
                binding.chipContainer.setBackgroundResource(R.drawable.bg_chip_selected);
                binding.tvCategoryName.setTextColor(
                        ContextCompat.getColor(binding.getRoot().getContext(), R.color.chip_selected_text));
            } else {
                binding.chipContainer.setBackgroundResource(R.drawable.bg_chip_unselected);
                binding.tvCategoryName.setTextColor(
                        ContextCompat.getColor(binding.getRoot().getContext(), R.color.chip_unselected_text));
            }

            binding.getRoot().setOnClickListener(v -> {
                int adapterPosition = getAdapterPosition();
                if (adapterPosition == RecyclerView.NO_POSITION) return;

                int previousSelected = selectedPosition;
                selectedPosition = adapterPosition;
                notifyItemChanged(previousSelected);
                notifyItemChanged(selectedPosition);

                if (listener != null) {
                    listener.onCategoryClick(category, adapterPosition);
                }
            });
        }
    }
}
