package com.coara.browser2;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import java.util.List;

public class SettingsAdapter extends RecyclerView.Adapter<SettingsAdapter.ViewHolder> {

    private List<SettingItem> settingItems;
    private Context context;

    public SettingsAdapter(List<SettingItem> settingItems) {
        this.settingItems = settingItems;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.item_setting, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SettingItem item = settingItems.get(position);
        holder.titleTextView.setText(item.getTitle());
        holder.descriptionTextView.setText(item.getDescription());
        if (item.getAction() != null) {
            holder.actionButton.setVisibility(View.VISIBLE);
            holder.actionButton.setOnClickListener(v -> item.getAction().run());
        } else {
            holder.actionButton.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return settingItems.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView titleTextView;
        TextView descriptionTextView;
        MaterialButton actionButton;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.title);
            descriptionTextView = itemView.findViewById(R.id.description);
            actionButton = itemView.findViewById(R.id.actionButton);
        }
    }
}
