package com.laf.lostandfound.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;
import com.laf.lostandfound.R;
import com.laf.lostandfound.activities.ItemDetailActivity;
import com.laf.lostandfound.activities.MainActivity;
import com.laf.lostandfound.activities.ReportWizardActivity;
import com.laf.lostandfound.adapters.CategoryAdapter;
import com.laf.lostandfound.adapters.ItemAdapter;
import com.laf.lostandfound.databinding.FragmentHomeBinding;
import com.laf.lostandfound.models.Category;
import com.laf.lostandfound.models.Item;
import com.laf.lostandfound.repositories.AuthRepository;
import com.laf.lostandfound.repositories.ItemRepository;
import com.laf.lostandfound.utils.Constants;
import com.laf.lostandfound.utils.TimeUtils;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private ItemRepository itemRepository;
    private AuthRepository authRepository;

    private ItemAdapter itemAdapter;
    private CategoryAdapter categoryAdapter;
    private final List<Item> itemList = new ArrayList<>();
    private String currentCategory = Constants.CATEGORY_ALL;
    private ListenerRegistration recentItemsRegistration;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        itemRepository = ItemRepository.getInstance();
        authRepository = AuthRepository.getInstance();

        setupGreeting();
        setupCategories();
        setupItemsGrid();
        setupSearchAndActions();
        attachRealtimeFeed();
    }

    private void setupGreeting() {
        String greeting = TimeUtils.getGreeting();
        FirebaseUser user = authRepository.getCurrentUser();
        String name = "Friend";
        if (user != null) {
            if (user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
                name = user.getDisplayName();
            } else if (user.getEmail() != null) {
                name = user.getEmail().split("@")[0];
            }
        }
        binding.tvGreeting.setText(greeting + ", " + name);

        binding.ivNotification.setOnClickListener(v -> {
            if (getActivity() != null) {
                BottomNavigationView nav = getActivity().findViewById(R.id.bottomNavigation);
                if (nav != null) {
                    nav.setSelectedItemId(R.id.nav_notifications);
                }
            }
        });
    }

    private void setupCategories() {
        List<Category> categories = Category.getDefaultCategories();
        categoryAdapter = new CategoryAdapter(categories, (category, position) -> {
            currentCategory = category.getName();
            loadItems();
        });

        binding.rvCategories.setLayoutManager(
                new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.rvCategories.setAdapter(categoryAdapter);
    }

    private void setupItemsGrid() {
        itemAdapter = new ItemAdapter(itemList, (item, position) -> {
            Intent intent = new Intent(requireContext(), ItemDetailActivity.class);
            intent.putExtra(Constants.EXTRA_ITEM_ID, item.getId());
            startActivity(intent);
        });

        binding.rvItems.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        binding.rvItems.setAdapter(itemAdapter);

        binding.swipeRefresh.setColorSchemeColors(
                requireContext().getColor(R.color.primary));
        binding.swipeRefresh.setOnRefreshListener(this::loadItems);
    }

    private void setupSearchAndActions() {
        binding.etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                String query = binding.etSearch.getText().toString().trim();
                if (!query.isEmpty() && getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).openSearch(query);
                    binding.etSearch.setText("");
                }
                return true;
            }
            return false;
        });

        binding.btnFilter.setOnClickListener(v -> {
            if (getActivity() != null) {
                BottomNavigationView nav = getActivity().findViewById(R.id.bottomNavigation);
                if (nav != null) {
                    nav.setSelectedItemId(R.id.nav_search);
                }
            }
        });

        // Launch the 7-Step Guided Report Wizard
        binding.fabAdd.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), ReportWizardActivity.class);
            startActivity(intent);
        });
    }

    private void attachRealtimeFeed() {
        binding.progressBar.setVisibility(View.VISIBLE);
        if (recentItemsRegistration != null) {
            recentItemsRegistration.remove();
        }

        recentItemsRegistration = itemRepository.listenToRecentItems(new ItemRepository.ItemListCallback() {
            @Override
            public void onSuccess(List<Item> items) {
                if (binding == null) return;
                binding.progressBar.setVisibility(View.GONE);
                binding.swipeRefresh.setRefreshing(false);

                itemList.clear();
                if (items != null) {
                    itemList.addAll(items);
                }
                itemAdapter.updateItems(itemList);
                binding.tvEmptyItems.setVisibility(itemList.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onFailure(String errorMessage) {
                if (binding == null) return;
                binding.progressBar.setVisibility(View.GONE);
                binding.swipeRefresh.setRefreshing(false);
            }
        });
    }

    private void loadItems() {
        if (currentCategory == null || currentCategory.equalsIgnoreCase(Constants.CATEGORY_ALL)) {
            attachRealtimeFeed();
            return;
        }

        if (recentItemsRegistration != null) {
            recentItemsRegistration.remove();
            recentItemsRegistration = null;
        }

        binding.progressBar.setVisibility(View.VISIBLE);
        itemRepository.getItemsByCategory(currentCategory, new ItemRepository.ItemListCallback() {
            @Override
            public void onSuccess(List<Item> items) {
                if (binding == null) return;
                binding.progressBar.setVisibility(View.GONE);
                binding.swipeRefresh.setRefreshing(false);

                itemList.clear();
                if (items != null) {
                    itemList.addAll(items);
                }
                itemAdapter.updateItems(itemList);
                binding.tvEmptyItems.setVisibility(itemList.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onFailure(String errorMessage) {
                if (binding == null) return;
                binding.progressBar.setVisibility(View.GONE);
                binding.swipeRefresh.setRefreshing(false);
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (recentItemsRegistration != null) {
            recentItemsRegistration.remove();
            recentItemsRegistration = null;
        }
        binding = null;
    }
}
