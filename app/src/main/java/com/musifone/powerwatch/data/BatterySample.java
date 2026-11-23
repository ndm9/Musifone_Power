package com.musifone.powerwatch.data;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "battery_samples",
        indices = {@Index("timestampMs")}
)
public class BatterySample {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long timestampMs;
    public int levelPercent;
    public boolean charging;
    public int status;
    public int temperatureTenthsC;
    public int voltageMv;

    public BatterySample(long timestampMs, int levelPercent, boolean charging,
                         int status, int temperatureTenthsC, int voltageMv) {
        this.timestampMs = timestampMs;
        this.levelPercent = levelPercent;
        this.charging = charging;
        this.status = status;
        this.temperatureTenthsC = temperatureTenthsC;
        this.voltageMv = voltageMv;
    }
}
