package com.bw.cardashboard;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/**
 * 「7天记录」弹窗：展示最近 7 天每日最高车速 (km/h) 与最低母线电压 (V)，
 * 以及本地 CSV 日志路径与总占用空间。
 */
public class HistoryDialog {

    public static void show(Context context, TelemetryLogger logger) {
        List<TelemetryLogger.DailySummary> days = logger.getLast7DaysSummary();
        long totalBytes = logger.getTotalStorageBytes();

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(context, 18);
        root.setPadding(pad, pad, pad, pad);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#0F172A"));
        bg.setCornerRadius(dp(context, 14));
        bg.setStroke(dp(context, 1), Color.parseColor("#334155"));
        root.setBackground(bg);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(context);
        title.setText("近 7 天行车与母线电压日志（滚动自动清理）");
        title.setTextColor(Color.parseColor("#F8FAFC"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(title, titleLp);

        TextView btnClose = new TextView(context);
        btnClose.setText("关闭");
        btnClose.setTextColor(Color.parseColor("#94A3B8"));
        btnClose.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btnClose.setPadding(dp(context, 12), dp(context, 4), dp(context, 12), dp(context, 4));
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(Color.parseColor("#1E293B"));
        closeBg.setCornerRadius(dp(context, 6));
        btnClose.setBackground(closeBg);
        header.addView(btnClose);
        root.addView(header);

        TextView sub = new TextView(context);
        sub.setText("本地存储路径: " + logger.getLogDirectoryDisplayPath() + "（每秒一行完整 CAN 参数 CSV）");
        sub.setTextColor(Color.parseColor("#64748B"));
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        sub.setPadding(0, dp(context, 4), 0, dp(context, 12));
        root.addView(sub);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);

        for (int i = 0; i < days.size(); i++) {
            TelemetryLogger.DailySummary d = days.get(i);
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setPadding(dp(context, 8), dp(context, 12), dp(context, 8), dp(context, 12));

            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setColor(Color.parseColor("#131D2E"));
            cardBg.setCornerRadius(dp(context, 8));
            cardBg.setStroke(dp(context, 1), Color.parseColor("#24344D"));
            card.setBackground(cardBg);

            TextView tvDate = new TextView(context);
            tvDate.setText(d.displayDate);
            tvDate.setTextColor(Color.parseColor("#94A3B8"));
            tvDate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tvDate.setTypeface(Typeface.MONOSPACE);
            card.addView(tvDate);

            TextView tvMaxSpd = new TextView(context);
            if (d.maxSpeedKmh > 0) {
                tvMaxSpd.setText(String.format(Locale.US, "极速 %.0f", d.maxSpeedKmh));
                tvMaxSpd.setTextColor(Color.parseColor("#22D3EE"));
            } else {
                tvMaxSpd.setText("极速 --");
                tvMaxSpd.setTextColor(Color.parseColor("#475569"));
            }
            tvMaxSpd.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            tvMaxSpd.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            tvMaxSpd.setPadding(0, dp(context, 6), 0, dp(context, 4));
            card.addView(tvMaxSpd);

            TextView tvMinV = new TextView(context);
            if (d.minHvVoltageV > 50f) {
                tvMinV.setText(String.format(Locale.US, "最低 %.0fV", d.minHvVoltageV));
                tvMinV.setTextColor(Color.parseColor("#F59E0B"));
            } else {
                tvMinV.setText("最低 --");
                tvMinV.setTextColor(Color.parseColor("#475569"));
            }
            tvMinV.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tvMinV.setTypeface(Typeface.MONOSPACE);
            card.addView(tvMinV);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i < days.size() - 1) {
                lp.rightMargin = dp(context, 8);
            }
            row.addView(card, lp);
        }
        root.addView(row);

        TextView footer = new TextView(context);
        String sizeStr = totalBytes >= 1024 * 1024
                ? String.format(Locale.US, "%.2f MB", totalBytes / (1024f * 1024f))
                : String.format(Locale.US, "%.1f KB", totalBytes / 1024f);
        footer.setText("当前已占用空间: " + sizeStr + "（超过 7 天的旧文件将自动删除）");
        footer.setTextColor(Color.parseColor("#64748B"));
        footer.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        footer.setPadding(0, dp(context, 12), 0, 0);
        root.addView(footer);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(root)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private static int dp(Context ctx, int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, ctx.getResources().getDisplayMetrics()));
    }
}
