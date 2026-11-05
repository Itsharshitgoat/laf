package com.laf.lostandfound.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.laf.lostandfound.R;
import com.laf.lostandfound.databinding.ItemClaimBinding;
import com.laf.lostandfound.models.Claim;
import com.laf.lostandfound.utils.Constants;
import com.laf.lostandfound.utils.TimeUtils;

import java.util.ArrayList;
import java.util.List;

/** Owner-side list of contact requests for one report. */
public class ClaimAdapter extends RecyclerView.Adapter<ClaimAdapter.ClaimViewHolder> {

    public interface ClaimActionListener {
        void onApprove(Claim claim);

        void onReject(Claim claim);
    }

    private final List<Claim> claims = new ArrayList<>();
    private final ClaimActionListener listener;

    public ClaimAdapter(ClaimActionListener listener) {
        this.listener = listener;
    }

    public void updateClaims(List<Claim> newClaims) {
        claims.clear();
        if (newClaims != null) claims.addAll(newClaims);
        notifyDataSetChanged();
    }

    public int getPendingCount() {
        int count = 0;
        for (Claim claim : claims) {
            if (Constants.CLAIM_PENDING.equalsIgnoreCase(claim.getStatus())) count++;
        }
        return count;
    }

    @NonNull
    @Override
    public ClaimViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemClaimBinding binding = ItemClaimBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ClaimViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ClaimViewHolder holder, int position) {
        holder.bind(claims.get(position));
    }

    @Override
    public int getItemCount() {
        return claims.size();
    }

    class ClaimViewHolder extends RecyclerView.ViewHolder {

        private final ItemClaimBinding binding;

        ClaimViewHolder(@NonNull ItemClaimBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Claim claim) {
            if (claim == null) return;

            String name = claim.getClaimantName();
            binding.tvClaimantName.setText(name == null || name.trim().isEmpty()
                    ? "Campus user" : name.trim());
            binding.tvClaimTime.setText(TimeUtils.getTimeAgo(claim.getCreatedAt()));

            String contact = claim.getClaimantContact();
            boolean hasContact = contact != null && !contact.trim().isEmpty();
            binding.tvClaimContact.setText(hasContact ? contact.trim() : "No contact provided");
            binding.tvClaimContact.setVisibility(View.VISIBLE);

            String message = claim.getMessage();
            boolean hasMessage = message != null && !message.trim().isEmpty();
            binding.tvClaimMessage.setText(hasMessage ? message.trim() : "");
            binding.tvClaimMessage.setVisibility(hasMessage ? View.VISIBLE : View.GONE);

            String status = claim.getStatus() == null ? Constants.CLAIM_PENDING : claim.getStatus();
            boolean pending = Constants.CLAIM_PENDING.equalsIgnoreCase(status);
            boolean approved = Constants.CLAIM_APPROVED.equalsIgnoreCase(status);

            binding.tvClaimStatus.setText(approved ? "Approved" : pending ? "Pending" : "Declined");
            binding.tvClaimStatus.setTextColor(binding.getRoot().getContext().getColor(
                    approved ? R.color.primary : pending ? R.color.text_primary : R.color.error));

            binding.layoutClaimButtons.setVisibility(pending ? View.VISIBLE : View.GONE);
            binding.btnApproveClaim.setEnabled(pending);
            binding.btnRejectClaim.setEnabled(pending);

            binding.btnApproveClaim.setOnClickListener(v -> {
                if (listener != null) listener.onApprove(claim);
            });
            binding.btnRejectClaim.setOnClickListener(v -> {
                if (listener != null) listener.onReject(claim);
            });
        }
    }
}
