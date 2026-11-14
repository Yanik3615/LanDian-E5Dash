package com.bw.cardashboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 7天滚动行车日志记录器：
 * 1. 每秒写入一行实车有效 CAN 参数 CSV 到 /sdcard/Download/CarDashboard/YYYY-MM-DD.csv
 * 2. 自动清理 7 天前旧文件
 * 3. 记录每日最高车速 (km/h) 与最低母线电压 (V) 供「7天记录」弹窗展示
 */
public class TelemetryLogger {

    public static final int RETENTION_DAYS = 7;
    private static final String PREFS_NAME = "car_dashboard_daily_v2";
    private static final String DIR_NAME = "CarDashboard";

    public static class DailySummary {
        public final String dateKey;
        public final String displayDate;
        public float maxSpeedKmh;
        public float minHvVoltageV;
        public int maxTripSeconds;

        public DailySummary(String dateKey, String displayDate) {
            this.dateKey = dateKey;
            this.displayDate = displayDate;
        }
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss", Locale.US);
    private final SimpleDateFormat shortFmt = new SimpleDateFormat("MM-dd", Locale.US);

    private String lastCleanedDate = "";
    private final List<String> pendingRows = new ArrayList<>();
    private String pendingDateKey = "";
    private int idleSkipCount = 0;

    public TelemetryLogger(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public File getLogDirectory() {
        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File target = new File(downloadDir, DIR_NAME);
        if (!target.exists() && !target.mkdirs()) {
            File fallback = new File(context.getExternalFilesDir(null), DIR_NAME);
            if (!fallback.exists()) {
                fallback.mkdirs();
            }
            return fallback;
        }
        return target;
    }

    public String getLogDirectoryDisplayPath() {
        return "/sdcard/Download/" + DIR_NAME + "/";
    }

    public void recordTick(VehicleState s) {
        Date now = new Date();
        String todayKey = dateFmt.format(now);
        String timeStr = timeFmt.format(now);

        if (!todayKey.equals(lastCleanedDate)) {
            cleanupOldLogs(now);
            lastCleanedDate = todayKey;
        }

        updateDailySummary(todayKey, s);
        // P 挡静止时每 5 秒记 1 条即可，减少无意义重复写入
        if (s.gear == 4 && s.speedKmh < 0.5f) {
            idleSkipCount++;
            if (idleSkipCount % 5 != 1) return;
        } else {
            idleSkipCount = 0;
        }
        queueCsvRow(todayKey, timeStr, s);
    }

    public synchronized void flush() {
        if (pendingRows.isEmpty() || pendingDateKey.isEmpty()) return;
        File dir = getLogDirectory();
        File csvFile = new File(dir, pendingDateKey + ".csv");
        boolean writeHeader = !csvFile.exists() || csvFile.length() == 0;
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(csvFile, true))) {
            if (writeHeader) {
                bw.write("time,speed_kmh,gear,soc_pct,ev_range_km,fuel_pct,fuel_range_km,"
                        + "hv_v,steer_deg,trip_s,maint_km,pm25_in,pm25_out,outside_c,solar");
                bw.newLine();
            }
            for (String line : pendingRows) {
                bw.write(line);
                bw.newLine();
            }
            pendingRows.clear();
        } catch (IOException ignored) {
        }
    }

    private void updateDailySummary(String dateKey, VehicleState s) {
        String raw = prefs.getString("day_" + dateKey, null);
        JSONObject obj;
        try {
            obj = (raw != null) ? new JSONObject(raw) : new JSONObject();
            double oldMaxSpd = obj.optDouble("maxSpeedKmh", 0.0);
            double oldMinV = obj.optDouble("minHvVoltageV", 999.0);
            int oldTrip = obj.optInt("maxTripSeconds", 0);

            boolean changed = false;
            if (s.speedKmh > oldMaxSpd) {
                obj.put("maxSpeedKmh", s.speedKmh);
                changed = true;
            }
            if (s.hvVoltageV > 50f && s.hvVoltageV < oldMinV) {
                obj.put("minHvVoltageV", s.hvVoltageV);
                changed = true;
            }
            if (s.tripSeconds > oldTrip) {
                obj.put("maxTripSeconds", s.tripSeconds);
                changed = true;
            }
            if (changed || raw == null) {
                prefs.edit().putString("day_" + dateKey, obj.toString()).apply();
            }
        } catch (JSONException ignored) {
        }
    }

    private synchronized void queueCsvRow(String dateKey, String timeStr, VehicleState s) {
        if (!pendingDateKey.isEmpty() && !pendingDateKey.equals(dateKey)) {
            flush();
        }
        pendingDateKey = dateKey;
        pendingRows.add(String.format(Locale.US,
                "%s,%.1f,%s,%.1f,%.0f,%d,%d,%.1f,%.1f,%d,%d,%d,%d,%.1f,%.1f",
                timeStr, s.speedKmh, VehicleState.formatGear(s.gear),
                s.socPercent, s.evRangeKm, s.fuelPercent, s.fuelRangeKm,
                s.hvVoltageV, s.steeringAngleDeg, s.tripSeconds, s.maintRemainKm,
                s.pm25Inside, s.pm25Outside, s.outsideTempC, s.solarIntensity));
        if (pendingRows.size() >= 10) {
            flush();
        }
    }

    private void cleanupOldLogs(Date now) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(now);
        cal.add(Calendar.DAY_OF_YEAR, -RETENTION_DAYS);
        String cutoffDate = dateFmt.format(cal.getTime());

        File dir = getLogDirectory();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                String name = f.getName();
                if (name.endsWith(".csv") && name.length() >= 14) {
                    String fileDate = name.substring(0, 10);
                    if (fileDate.compareTo(cutoffDate) < 0) {
                        //noinspection ResultOfMethodCallIgnored
                        f.delete();
                    }
                }
            }
        }

        SharedPreferences.Editor editor = prefs.edit();
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith("day_")) {
                String d = key.substring(4);
                if (d.compareTo(cutoffDate) < 0) {
                    editor.remove(key);
                }
            }
        }
        editor.apply();
    }

    public List<DailySummary> getLast7DaysSummary() {
        List<DailySummary> list = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        for (int i = RETENTION_DAYS - 1; i >= 0; i--) {
            Calendar c = (Calendar) cal.clone();
            c.add(Calendar.DAY_OF_YEAR, -i);
            String key = dateFmt.format(c.getTime());
            String disp = shortFmt.format(c.getTime());
            DailySummary item = new DailySummary(key, disp);

            String raw = prefs.getString("day_" + key, null);
            if (raw != null) {
                try {
                    JSONObject obj = new JSONObject(raw);
                    item.maxSpeedKmh = (float) obj.optDouble("maxSpeedKmh", 0.0);
                    double minV = obj.optDouble("minHvVoltageV", 999.0);
                    item.minHvVoltageV = minV < 900.0 ? (float) minV : 0f;
                    item.maxTripSeconds = obj.optInt("maxTripSeconds", 0);
                } catch (JSONException ignored) {
                }
            }
            list.add(item);
        }
        return list;
    }

    public long getTotalStorageBytes() {
        File dir = getLogDirectory();
        File[] files = dir.listFiles();
        if (files == null) return 0;
        long total = 0;
        for (File f : files) {
            if (f.isFile()) total += f.length();
        }
        return total;
    }
}
