package com.musifone.powerwatch.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface BatteryDao {
    @Insert
    long insert(BatterySample sample);

    @Query("SELECT * FROM battery_samples WHERE timestampMs >= :sinceMs ORDER BY timestampMs ASC")
    List<BatterySample> samplesSince(long sinceMs);

    @Query("SELECT * FROM battery_samples ORDER BY timestampMs DESC LIMIT :limit")
    List<BatterySample> recent(int limit);

    @Query("SELECT * FROM battery_samples ORDER BY timestampMs DESC LIMIT 1")
    BatterySample latest();

    @Query("DELETE FROM battery_samples WHERE timestampMs < :beforeMs")
    int deleteOlderThan(long beforeMs);

    @Query("SELECT COUNT(*) FROM battery_samples")
    int count();
}
