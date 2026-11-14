package com.bw.cardashboard;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.bw.cardashboard.ui.PedalBarView;
import com.bw.cardashboard.ui.PowerChartView;
import com.bw.cardashboard.ui.RoadKingFaceView;
import com.bw.cardashboard.ui.SteeringWheelView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 「车辆看板」主界面（V2 实车全有效数据版，适配 1920x720 横屏）。
 */
public class MainActivity extends Activity implements BwCarClient.Listener {

    private static final int REQ_STORAGE = 101;
    private static final int REQ_OVERLAY = 102;

    private BwCarClient carClient;
    private final SimpleDateFormat clockFmt = new SimpleDateFormat("HH:mm", Locale.US);

    private TextView tvTopTelemetry, tvClock;
    private TextView tvGearBadge, tvSpeedBig, tvAvgConsumption, tvMaxSpeed;
    private PowerChartView powerChartView;
    private PowerChartView voltChartView;

    private TextView tvTotalRange, tvSocLabel, tvEvRange, tvFuelLabel, tvFuelRange;
    private PedalBarView barSoc, barFuel;

    private TextView tvVoltStatusBadge, tvHvVoltBig, tvHvRangeText;

    private SteeringWheelView steeringWheelView;
    private TextView tvSteerAngle, tvTripTime, tvMaintKm;

    private TextView tvAirBadge, tvPm25In, tvPm25Out, tvOutsideTemp, tvSolar;

    private RoadKingFaceView roadKingFaceView;
    private TextView tvKingStatus, tvKingGValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        applyImmersiveFullscreen();
        setContentView(R.layout.activity_main);

        bindViews();
        requestStorageIfNeeded();

        carClient = BwCarClient.getInstance(this);
        carClient.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyImmersiveFullscreen();
        stopService(new Intent(this, FloatingService.class));
        carClient.addListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        carClient.removeListener(this);
        carClient.getLogger().flush();
    }

    private void applyImmersiveFullscreen() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void bindViews() {
        tvTopTelemetry = findViewById(R.id.tvTopTelemetry);
        tvClock = findViewById(R.id.tvClock);

        roadKingFaceView = findViewById(R.id.roadKingFaceView);
        tvKingStatus = findViewById(R.id.tvKingStatus);
        tvKingGValue = findViewById(R.id.tvKingGValue);

        findViewById(R.id.btnHistory).setOnClickListener(v ->
                HistoryDialog.show(this, carClient.getLogger()));
        findViewById(R.id.btnFloating).setOnClickListener(v -> switchToFloatingMode());

        tvGearBadge = findViewById(R.id.tvGearBadge);
        tvSpeedBig = findViewById(R.id.tvSpeedBig);
        tvAvgConsumption = findViewById(R.id.tvAvgConsumption);
        tvMaxSpeed = findViewById(R.id.tvMaxSpeed);
        tvMaxSpeed.setOnClickListener(v -> {
            carClient.resetSessionRecords();
            Toast.makeText(this, "本次极速与电压区间记录已清零", Toast.LENGTH_SHORT).show();
        });

        powerChartView = findViewById(R.id.powerChartView);
        powerChartView.setMode(PowerChartView.MODE_SPEED);
        voltChartView = findViewById(R.id.voltChartView);
        voltChartView.setMode(PowerChartView.MODE_VOLTAGE);

        tvTotalRange = findViewById(R.id.tvTotalRange);
        tvSocLabel = findViewById(R.id.tvSocLabel);
        tvEvRange = findViewById(R.id.tvEvRange);
        tvFuelLabel = findViewById(R.id.tvFuelLabel);
        tvFuelRange = findViewById(R.id.tvFuelRange);
        barSoc = findViewById(R.id.barSoc);
        barFuel = findViewById(R.id.barFuel);
        barSoc.setColors(Color.parseColor("#059669"), Color.parseColor("#10B981"));
        barFuel.setColors(Color.parseColor("#D97706"), Color.parseColor("#F59E0B"));

        tvVoltStatusBadge = findViewById(R.id.tvVoltStatusBadge);
        tvHvVoltBig = findViewById(R.id.tvHvVoltBig);
        tvHvRangeText = findViewById(R.id.tvHvRangeText);

        steeringWheelView = findViewById(R.id.steeringWheelView);
        tvSteerAngle = findViewById(R.id.tvSteerAngle);
        tvTripTime = findViewById(R.id.tvTripTime);
        tvMaintKm = findViewById(R.id.tvMaintKm);

        tvAirBadge = findViewById(R.id.tvAirBadge);
        tvPm25In = findViewById(R.id.tvPm25In);
        tvPm25Out = findViewById(R.id.tvPm25Out);
        tvOutsideTemp = findViewById(R.id.tvOutsideTemp);
        tvSolar = findViewById(R.id.tvSolar);
    }

    private void switchToFloatingMode() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请授予「车辆看板」悬浮窗权限", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQ_OVERLAY);
            return;
        }
        startForegroundService(new Intent(this, FloatingService.class));
        moveTaskToBack(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OVERLAY && Settings.canDrawOverlays(this)) {
            startForegroundService(new Intent(this, FloatingService.class));
            moveTaskToBack(true);
        }
    }

    private void requestStorageIfNeeded() {
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            }, REQ_STORAGE);
        }
    }

    @Override
    public void onStateUpdated(VehicleState s) {
        String gearStr = VehicleState.formatGear(s.gear);
        int totalRange = Math.round(s.evRangeKm) + s.fuelRangeKm;

        tvClock.setText(clockFmt.format(new Date()));
        tvTopTelemetry.setText(String.format(Locale.US,
                "车速 %.1f km/h  ·  挡位 %s  ·  SOC %.1f%%  ·  综合续航 %d km",
                s.speedKmh, gearStr, s.socPercent, totalRange));

        // 1. 车速与挡位
        tvGearBadge.setText("挡位 " + gearStr);
        tvSpeedBig.setText(String.format(Locale.US, "%.1f", s.speedKmh));
        tvMaxSpeed.setText(String.format(Locale.US, "本次最高: %.1f km/h", s.sessionMaxSpeedKmh));

        // 2. 60s 纯车速波形 + 60s 高压母线电压波形
        powerChartView.updateData(s.speedHistory, s.voltageHistory, s.historyCount);
        voltChartView.updateData(s.speedHistory, s.voltageHistory, s.historyCount);

        // 3. 能源与双续航
        tvTotalRange.setText(String.format(Locale.US, "综合续航 %d km", totalRange));
        tvSocLabel.setText(String.format(Locale.US, "电量 SOC %.1f%%", s.socPercent));
        tvEvRange.setText(String.format(Locale.US, "纯电 %.0f km", s.evRangeKm));
        barSoc.setProgress(s.socPercent);

        tvFuelLabel.setText(String.format(Locale.US, "剩余油量 %d%%", s.fuelPercent));
        tvFuelRange.setText(String.format(Locale.US, "油续航 %d km", s.fuelRangeKm));
        barFuel.setProgress(s.fuelPercent);

        // 4. 高压母线电压
        if (s.hvVoltageV > 50f) {
            tvHvVoltBig.setText(String.format(Locale.US, "%.0f", s.hvVoltageV));
            if (s.sessionMinHvV < 900f && s.sessionMaxHvV > 0f) {
                tvHvRangeText.setText(String.format(Locale.US,
                        "本次区间: %.0fV ~ %.0fV",
                        s.sessionMinHvV, s.sessionMaxHvV));
            }
            if (s.hvVoltageV < 310f) {
                tvVoltStatusBadge.setText("电压偏低");
                tvVoltStatusBadge.setTextColor(Color.parseColor("#F43F5E"));
            } else {
                tvVoltStatusBadge.setText("母线正常");
                tvVoltStatusBadge.setTextColor(Color.parseColor("#10B981"));
            }
        } else {
            tvHvVoltBig.setText("---");
        }

        // 5. 方向盘与行程/保养
        steeringWheelView.setAngleDeg(s.steeringAngleDeg);
        if (Math.abs(s.steeringAngleDeg) < 1.5f) {
            tvSteerAngle.setText("回正 0.0°");
        } else if (s.steeringAngleDeg < 0) {
            tvSteerAngle.setText(String.format(Locale.US, "左转 %.1f°", Math.abs(s.steeringAngleDeg)));
        } else {
            tvSteerAngle.setText(String.format(Locale.US, "右转 %.1f°", s.steeringAngleDeg));
        }
        int hh = s.tripSeconds / 3600;
        int mm = (s.tripSeconds % 3600) / 60;
        int ss = s.tripSeconds % 60;
        tvTripTime.setText(String.format(Locale.US, "本次行程: %02d:%02d:%02d", hh, mm, ss));
        tvMaintKm.setText(String.format(Locale.US, "保养余: %,d km", s.maintRemainKm));

        // 6. 座舱空气与外界环境
        tvPm25In.setText(s.pm25Inside + " ug/m³");
        tvPm25Out.setText(s.pm25Outside + " ug/m³");
        if (s.pm25Inside <= 35) {
            tvAirBadge.setText("车内优");
            tvAirBadge.setTextColor(Color.parseColor("#10B981"));
            tvPm25In.setTextColor(Color.parseColor("#10B981"));
        } else if (s.pm25Inside <= 75) {
            tvAirBadge.setText("车内良");
            tvAirBadge.setTextColor(Color.parseColor("#22D3EE"));
            tvPm25In.setTextColor(Color.parseColor("#22D3EE"));
        } else {
            tvAirBadge.setText("建议净化");
            tvAirBadge.setTextColor(Color.parseColor("#F59E0B"));
            tvPm25In.setTextColor(Color.parseColor("#F59E0B"));
        }
        tvOutsideTemp.setText(String.format(Locale.US, "车外温度: %.1f℃", s.outsideTempC));
        tvSolar.setText(String.format(Locale.US, "%.1f W/m²", s.solarIntensity));

        // 7. 中央 C 位「公路之王」动态变脸
        RoadKingFaceView.Mood mood = roadKingFaceView.updateVehicle(
                s.speedKmh, s.longitudinalG, s.steeringAngleDeg, s.gear);
        tvKingStatus.setText(mood.label);
        tvKingStatus.setTextColor(mood.color);
        tvKingGValue.setText(String.format(Locale.US, "推背 %+.2f G", s.longitudinalG));
        tvKingGValue.setTextColor(mood.color);
    }
}
