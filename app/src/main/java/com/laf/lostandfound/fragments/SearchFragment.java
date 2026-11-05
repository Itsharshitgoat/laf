package com.laf.lostandfound.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.laf.lostandfound.R;
import com.laf.lostandfound.activities.ItemDetailActivity;
import com.laf.lostandfound.adapters.CategoryAdapter;
import com.laf.lostandfound.adapters.ItemAdapter;
import com.laf.lostandfound.databinding.FragmentSearchBinding;
import com.laf.lostandfound.models.Category;
import com.laf.lostandfound.models.Item;
import com.laf.lostandfound.repositories.ItemRepository;
import com.laf.lostandfound.utils.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Server-side search over the normalized {@code searchText} index. Pages are
 * limited to {@link Constants#SEARCH_PAGE_SIZE} documents and additional pages
 * load as the grid is scrolled.
 */
public class SearchFragment extends Fragment
        implements ItemAdapter.OnItemClickListener, CategoryAdapter.OnCategoryClickListener {

    private static final long DEBOUNCE_MS = 350;

    private FragmentSearchBinding binding;
    private ItemRepository itemRepository;
    private ItemAdapter itemAdapter;
    private CategoryAdapter categoryAdapter;

    private final List<Item> items = new ArrayList<>();
    private final ItemRepository.SearchFilter filter = new ItemRepository.SearchFilter();
    private final Handler debounceHandler = new Handler(Looper.getMainLooper());

    private Runnable searchRunnable;
    private DocumentSnapshot lastVisible;
    private boolean loading;
    private boolean hasMore;
    private int requestToken;
    private String pendingQuery;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSearchBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        itemRepository = ItemRepository.getInstance();

        setupRecyclerView();
        setupCategoryChips();
        setupTypeFilter();
        setupSearchInput();
        setupStates();

        if (pendingQuery != null) {
            String query = pendingQuery;
            pendingQuery = null;
            applyQuery(query);
        } else {
            performSearch(true);
        }
    }

    private void setupRecyclerView() {
        itemAdapter = new ItemAdapter(items, this);
        GridLayoutManager layoutManager = new GridLayoutManager(requireContext(), 2);
        binding.rvSearchResults.setLayoutManager(layoutManager);
        binding.rvSearchResults.setAdapter(itemAdapter);
        binding.rvSearchResults.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy <= 0 || loading || !hasMore) return;
                int lastVisiblePosition = layoutManager.findLastVisibleItemPosition();
                if (lastVisiblePosition >= itemAdapter.getItemCount() - 4) {
                    performSearch(false);
                }
            }
        });
    }

    private void setupCategoryChips() {
        categoryAdapter = new CategoryAdapter(Category.getDefaultCategories(), this);
        binding.rvCategoryChips.setAdapter(categoryAdapter);
    }

    private void setupTypeFilter() {
        binding.cgTypeFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipTypeLost) {
                filter.type = Constants.TYPE_LOST;
            } else if (id == R.id.chipTypeFound) {
                filter.type = Constants.TYPE_FOUND;
            } else {
                filter.type = Constants.TYPE_ALL;
            }
            performSearch(true);
        });
    }

    private void setupSearchInput() {
        binding.etSearchQuery.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter.query = s == null ? "" : s.toString().trim();
                binding.btnClearSearch.setVisibility(
                        filter.query.isEmpty() ? View.GONE : View.VISIBLE);

                scheduleSearch();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        binding.etSearchQuery.setOnEditorActionListener((v, actionId, event) -> {
            scheduleSearchNow();
            return true;
        });

        binding.btnClearSearch.setOnClickListener(v -> {
            binding.etSearchQuery.setText("");
            binding.etSearchQuery.requestFocus();
        });
    }

    private void setupStates() {
        binding.swipeSearch.setColorSchemeColors(requireContext().getColor(R.color.primary));
        binding.swipeSearch.setOnRefreshListener(() -> performSearch(true));
        binding.btnRetrySearch.setOnClickListener(v -> performSearch(true));
        binding.btnResetFilters.setOnClickListener(v -> {
            filter.query = "";
            filter.type = Constants.TYPE_ALL;
            filter.category = Constants.CATEGORY_ALL;
            binding.etSearchQuery.setText("");
            binding.btnClearSearch.setVisibility(View.GONE);
            binding.cgTypeFilter.check(R.id.chipTypeAll);
            categoryAdapter.setSelectedPosition(0);
            performSearch(true);
        });
    }

    private void scheduleSearch() {
        if (searchRunnable != null) {
            debounceHandler.removeCallbacks(searchRunnable);
        }
        searchRunnable = () -> performSearch(true);
        debounceHandler.postDelayed(searchRunnable, DEBOUNCE_MS);
    }

    private void scheduleSearchNow() {
        if (searchRunnable != null) {
            debounceHandler.removeCallbacks(searchRunnable);
            searchRunnable = null;
        }
        performSearch(true);
    }

    /**
     * Entry point used by the home screen when the user submits a search. The
     * fragment may not have a view yet, so the query is cached until
     * {@link #onViewCreated} runs.
     */
    public void applyQuery(String query) {
        String clean = query == null ? "" : query.trim();
        filter.query = clean;
        if (binding == null) {
            pendingQuery = clean;
            return;
        }
        binding.etSearchQuery.setText(clean);
        binding.etSearchQuery.setSelection(binding.etSearchQuery.getText().length());
        binding.btnClearSearch.setVisibility(clean.isEmpty() ? View.GONE : View.VISIBLE);
        performSearch(true);
    }

    private void performSearch(boolean reset) {
        if (binding == null) return;
        if (!reset && (loading || !hasMore)) return;

        final int token = reset ? ++requestToken : requestToken;
        final DocumentSnapshot startAfter = reset ? null : lastVisible;

        loading = true;
        binding.llSearchError.setVisibility(View.GONE);
        binding.swipeSearch.setRefreshing(false);
        if (reset) {
            binding.pbSearching.setVisibility(View.VISIBLE);
            binding.pbLoadMore.setVisibility(View.GONE);
        } else {
            binding.pbLoadMore.setVisibility(View.VISIBLE);
        }

        itemRepository.searchItems(filter.copy(), startAfter,
                new ItemRepository.SearchPageCallback() {
                    @Override
                    public void onSuccess(List<Item> pageItems, DocumentSnapshot pageLastVisible,
                                          boolean pageHasMore) {
                        if (binding == null || token != requestToken) return;

                        loading = false;
                        binding.pbSearching.setVisibility(View.GONE);
                        binding.pbLoadMore.setVisibility(View.GONE);

                        if (reset) {
                            items.clear();
                        }
                        if (pageItems != null) {
                            items.addAll(pageItems);
                        }
                        lastVisible = pageLastVisible;
                        hasMore = pageHasMore;
                        itemAdapter.updateItems(items);

                        // If all items on this page were filtered out but more pages exist,
                        // continue auto-fetching the next page so the user isn't stuck on an empty screen.
                        if (items.isEmpty() && hasMore) {
                            performSearch(false);
                            return;
                        }

                        updateResultState();
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (binding == null || token != requestToken) return;

                        loading = false;
                        binding.pbSearching.setVisibility(View.GONE);
                        binding.pbLoadMore.setVisibility(View.GONE);
                        binding.swipeSearch.setRefreshing(false);

                        if (reset) {
                            items.clear();
                            lastVisible = null;
                            hasMore = false;
                            itemAdapter.updateItems(items);
                            binding.llSearchError.setVisibility(View.VISIBLE);
                            binding.tvSearchError.setText(errorMessage);
                            binding.llEmptyState.setVisibility(View.GONE);
                            binding.tvResultsCount.setText("Search unavailable");
                        } else {
                            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void updateResultState() {
        if (binding == null) return;
        boolean empty = items.isEmpty();
        binding.llEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.llSearchError.setVisibility(View.GONE);
        binding.rvSearchResults.setVisibility(empty ? View.GONE : View.VISIBLE);

        if (empty) {
            binding.tvEmptyMessage.setText(filter.query.isEmpty()
                    ? "No active reports match these filters yet."
                    : "No items matched \"" + filter.query + "\". Try different keywords or clear filters.");
            binding.tvResultsCount.setText("No results");
        } else {
            binding.tvResultsCount.setText(String.format(Locale.getDefault(),
                    "Showing %d%s item%s", items.size(), hasMore ? "+" : "",
                    items.size() == 1 ? "" : "s"));
        }
    }

    @Override
    public void onCategoryClick(Category category, int position) {
        filter.category = category == null ? Constants.CATEGORY_ALL : category.getName();
        performSearch(true);
    }

    @Override
    public void onItemClick(Item item, int position) {
        if (item == null) return;
        String itemId = item.getItemId() != null ? item.getItemId() : item.getId();
        if (itemId == null || itemId.isEmpty()) return;
        Intent intent = new Intent(requireContext(), ItemDetailActivity.class);
        intent.putExtra(Constants.EXTRA_ITEM_ID, itemId);
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        requestToken++;
        if (searchRunnable != null) {
            debounceHandler.removeCallbacks(searchRunnable);
            searchRunnable = null;
        }
        binding = null;
    }
}
