package com.musifone.powerwatch.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.musifone.powerwatch.PowerWatchApp;
import com.musifone.powerwatch.R;
import com.musifone.powerwatch.data.AppDatabase;
import com.musifone.powerwatch.data.BatterySample;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HistoryActivity extends AppCompatActivity {
    private AppDatabase database;
    private ExecutorService io;
    private TextView summary;
    private HistoryAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        setTitle(R.string.history_title);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        database = ((PowerWatchApp) getApplication()).getDatabase();
        io = Executors.newSingleThreadExecutor();
        summary = findViewById(R.id.historySummary);

        RecyclerView list = findViewById(R.id.historyList);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter();
        list.setAdapter(adapter);

        load();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        if (io != null) {
            io.shutdownNow();
        }
        super.onDestroy();
    }

    private void load() {
        io.execute(() -> {
            List<BatterySample> samples = database.batteryDao().recent(200);
            int total = database.batteryDao().count();
            runOnUiThread(() -> {
                summary.setText(String.format(
                        Locale.getDefault(),
                        "Showing %d of %d samples (kept ~14 days)",
                        samples.size(),
                        total
                ));
                adapter.submit(samples);
            });
        });
    }

    private static final class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.Holder> {
        private final List<BatterySample> items = new ArrayList<>();
        private final DateFormat fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);

        void submit(List<BatterySample> next) {
            items.clear();
            items.addAll(next);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_history, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            BatterySample sample = items.get(position);
            holder.time.setText(fmt.format(new Date(sample.timestampMs)));
            holder.level.setText(sample.levelPercent + "%");
            holder.state.setText(sample.charging ? "charging" : "drain");
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final TextView time;
            final TextView level;
            final TextView state;

            Holder(@NonNull View itemView) {
                super(itemView);
                time = itemView.findViewById(R.id.itemTime);
                level = itemView.findViewById(R.id.itemLevel);
                state = itemView.findViewById(R.id.itemState);
            }
        }
    }
}
