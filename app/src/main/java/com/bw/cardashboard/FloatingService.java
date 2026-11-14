package com.bw.cardashboard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.IBinder;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * 精简悬浮窗胶囊服务（100% 实车有效实时数据）：
 * 🚗 车速 · 挡位 ｜ 🔋 SOC% · 母线V ｜ ⛽ 油量% (油续航) ｜ 🌿 PM2.5 内/外
 */
public class FloatingService extends Service implements BwCarClient.Listener {

    public static final String ACTION_STOP = "com.bw.cardashboard.STOP_FLOATING";
    private static final String CHANNEL_ID = "car_dashboard_floating";
    private static final int NOTIF_ID = 1001;

    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private LinearLayout pillView;
    private TextView tvPillText;
    private BwCarClient carClient;

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundNotification();
        carClient = BwCarClient.getInstance(this);
        carClient.start();
        createFloatingPill();
        carClient.addListener(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (carClient != null) {
            carClient.removeListener(this);
        }
        if (windowManager != null && pillView != null) {
            try {
                windowManager.removeView(pillView);
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startForegroundNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notif = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_text))
                .setContentIntent(pi)
                .build();
        startForeground(NOTIF_ID, notif);
    }

    private void createFloatingPill() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        pillView = new LinearLayout(this);
        pillView.setOrientation(LinearLayout.HORIZONTAL);
        pillView.setGravity(Gravity.CENTER_VERTICAL);
        pillView.setBackgroundResource(R.drawable.bg_floating_pill);
        pillView.setPadding(dp(14), dp(7), dp(10), dp(7));

        tvPillText = new TextView(this);
        tvPillText.setTextColor(Color.parseColor("#F8FAFC"));
        tvPillText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvPillText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tvPillText.setSingleLine(true);
        tvPillText.setText("回正 0.0°  ｜  G值 +0.00 G  ｜  母线 -- V");
        pillView.addView(tvPillText);

        TextView btnClose = new TextView(this);
        btnClose.setText("  ✕");
        btnClose.setTextColor(Color.parseColor("#94A3B8"));
        btnClose.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btnClose.setPadding(dp(6), 0, dp(2), 0);
        btnClose.setOnClickListener(v -> stopSelf());
        pillView.addView(btnClose);

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = dp(18);

        pillView.setOnTouchListener(new View.OnTouchListener() {
            private int initX, initY;
            private float downX, downY;
            private boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initX = params.x;
                        initY = params.y;
                        downX = event.getRawX();
                        downY = event.getRawY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = Math.round(event.getRawX() - downX);
                        int dy = Math.round(event.getRawY() - downY);
                        if (Math.abs(dx) > 6 || Math.abs(dy) > 6) {
                            moved = true;
                            params.x = initX + dx;
                            params.y = initY + dy;
                            windowManager.updateViewLayout(pillView, params);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) {
                            Intent i = new Intent(FloatingService.this, MainActivity.class);
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                            startActivity(i);
                            stopSelf();
                        }
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(pillView, params);
    }

    @Override
    public void onStateUpdated(VehicleState s) {
        if (tvPillText == null) return;
        String steerStr;
        if (Math.abs(s.steeringAngleDeg) < 1.5f) {
            steerStr = "回正 0.0°";
        } else if (s.steeringAngleDeg < 0f) {
            steerStr = String.format(Locale.US, "左转 %.1f°", Math.abs(s.steeringAngleDeg));
        } else {
            steerStr = String.format(Locale.US, "右转 %.1f°", s.steeringAngleDeg);
        }
        String voltStr = (s.hvVoltageV > 50f) ? String.format(Locale.US, "%.0f V", s.hvVoltageV) : "-- V";
        String text = String.format(Locale.US,
                "%s  ｜  G值 %+.2f G  ｜  母线 %s",
                steerStr, s.longitudinalG, voltStr);
        tvPillText.post(() -> tvPillText.setText(text));
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
