package com.bw.cardashboard.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import com.bw.cardashboard.VehicleState;

/**
 * 60 秒实时波形图：支持纯车速 (青)、纯母线电压 (橙) 或双线模式。
 */
public class PowerChartView extends View {

    public static final int MODE_BOTH = 0;
    public static final int MODE_SPEED = 1;
    public static final int MODE_VOLTAGE = 2;

    private int mode = MODE_SPEED;

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint speedLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint speedFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint voltLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint voltFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path speedPath = new Path();
    private final Path speedFillPath = new Path();
    private final Path voltPath = new Path();
    private final Path voltFillPath = new Path();

    private final float[] speedPts = new float[VehicleState.HISTORY_SIZE];
    private final float[] voltPts = new float[VehicleState.HISTORY_SIZE];
    private int count = 0;

    public PowerChartView(Context context) {
        this(context, null);
    }

    public PowerChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f);
        gridPaint.setColor(Color.parseColor("#1E293B"));

        labelPaint.setColor(Color.parseColor("#64748B"));
        labelPaint.setTextSize(15f);

        speedLinePaint.setStyle(Paint.Style.STROKE);
        speedLinePaint.setStrokeWidth(3.2f);
        speedLinePaint.setStrokeJoin(Paint.Join.ROUND);
        speedLinePaint.setStrokeCap(Paint.Cap.ROUND);
        speedLinePaint.setColor(Color.parseColor("#22D3EE"));

        speedFillPaint.setStyle(Paint.Style.FILL);

        voltLinePaint.setStyle(Paint.Style.STROKE);
        voltLinePaint.setStrokeWidth(3.0f);
        voltLinePaint.setStrokeJoin(Paint.Join.ROUND);
        voltLinePaint.setStrokeCap(Paint.Cap.ROUND);
        voltLinePaint.setColor(Color.parseColor("#F59E0B"));

        voltFillPaint.setStyle(Paint.Style.FILL);
    }

    public void setMode(int mode) {
        this.mode = mode;
        invalidate();
    }

    public void updateData(float[] speedHist, float[] voltHist, int count) {
        this.count = Math.min(count, VehicleState.HISTORY_SIZE);
        if (this.count > 0) {
            System.arraycopy(speedHist, 0, this.speedPts, 0, this.count);
            System.arraycopy(voltHist, 0, this.voltPts, 0, this.count);
        }
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        speedFillPaint.setShader(new LinearGradient(
                0, 0, 0, h,
                Color.parseColor("#4422D3EE"),
                Color.parseColor("#0022D3EE"),
                Shader.TileMode.CLAMP));
        voltFillPaint.setShader(new LinearGradient(
                0, 0, 0, h,
                Color.parseColor("#40F59E0B"),
                Color.parseColor("#00F59E0B"),
                Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float padV = 8f;
        float plotH = Math.max(1f, h - padV * 2);

        canvas.drawLine(0, padV + plotH * 0.25f, w, padV + plotH * 0.25f, gridPaint);
        canvas.drawLine(0, padV + plotH * 0.50f, w, padV + plotH * 0.50f, gridPaint);
        canvas.drawLine(0, padV + plotH * 0.75f, w, padV + plotH * 0.75f, gridPaint);

        if (count < 2) return;

        float minSpeedRaw = 999f;
        float peakSpeed = 0f;
        float minV = 999f;
        float maxV = 0f;
        for (int i = 0; i < count; i++) {
            if (speedPts[i] < minSpeedRaw) minSpeedRaw = speedPts[i];
            if (speedPts[i] > peakSpeed) peakSpeed = speedPts[i];
            if (voltPts[i] > 50f) {
                if (voltPts[i] < minV) minV = voltPts[i];
                if (voltPts[i] > maxV) maxV = voltPts[i];
            }
        }
        if (minV > maxV) {
            minV = 342f;
            maxV = 352f;
        } else {
            minV = (float) Math.floor(minV - 1.5f);
            maxV = (float) Math.ceil(maxV + 1.5f);
        }
        float vSpan = Math.max(6f, maxV - minV);

        int scaleBottom = Math.max(0, ((int) Math.floor((minSpeedRaw - 5f) / 10.0)) * 10);
        int scaleTop = Math.max(scaleBottom + 20, ((int) Math.ceil((peakSpeed + 4f) / 10.0)) * 10);
        float speedSpan = Math.max(20f, (float) (scaleTop - scaleBottom));

        if (mode == MODE_SPEED || mode == MODE_BOTH) {
            canvas.drawText(String.valueOf(scaleTop), 4f, padV + 13f, labelPaint);
            canvas.drawText(String.valueOf((scaleTop + scaleBottom) / 2), 4f, padV + plotH * 0.50f - 4f, labelPaint);
            canvas.drawText(String.valueOf(scaleBottom), 4f, h - padV - 2f, labelPaint);
        } else {
            canvas.drawText(String.valueOf((int) (minV + vSpan)), 4f, padV + 13f, labelPaint);
            canvas.drawText(String.valueOf((int) (minV + vSpan * 0.5f)), 4f, padV + plotH * 0.50f - 4f, labelPaint);
            canvas.drawText(String.valueOf((int) minV), 4f, h - padV - 2f, labelPaint);
        }

        speedPath.reset();
        speedFillPath.reset();
        voltPath.reset();
        voltFillPath.reset();

        float leftInset = 34f;
        float usableW = Math.max(10f, w - leftInset - 4f);
        float stepX = usableW / (float) (VehicleState.HISTORY_SIZE - 1);
        float startX = leftInset + (VehicleState.HISTORY_SIZE - count) * stepX;

        for (int i = 0; i < count; i++) {
            float x = startX + i * stepX;
            float sy = (h - padV) - ((speedPts[i] - scaleBottom) / speedSpan) * (plotH * 0.90f);
            sy = Math.max(padV, Math.min(h - padV, sy));

            float vVal = voltPts[i] > 50f ? voltPts[i] : minV;
            float vy = (h - padV) - ((vVal - minV) / vSpan) * (plotH * 0.88f);
            vy = Math.max(padV, Math.min(h - padV, vy));

            if (i == 0) {
                speedPath.moveTo(x, sy);
                speedFillPath.moveTo(x, h - padV);
                speedFillPath.lineTo(x, sy);

                voltPath.moveTo(x, vy);
                voltFillPath.moveTo(x, h - padV);
                voltFillPath.lineTo(x, vy);
            } else {
                speedPath.lineTo(x, sy);
                speedFillPath.lineTo(x, sy);

                voltPath.lineTo(x, vy);
                voltFillPath.lineTo(x, vy);
            }
            if (i == count - 1) {
                speedFillPath.lineTo(x, h - padV);
                speedFillPath.close();

                voltFillPath.lineTo(x, h - padV);
                voltFillPath.close();
            }
        }

        if (mode == MODE_SPEED || mode == MODE_BOTH) {
            canvas.drawPath(speedFillPath, speedFillPaint);
            canvas.drawPath(speedPath, speedLinePaint);
        }
        if (mode == MODE_VOLTAGE || mode == MODE_BOTH) {
            if (mode == MODE_VOLTAGE) {
                canvas.drawPath(voltFillPath, voltFillPaint);
            }
            canvas.drawPath(voltPath, voltLinePaint);
        }
    }
}
