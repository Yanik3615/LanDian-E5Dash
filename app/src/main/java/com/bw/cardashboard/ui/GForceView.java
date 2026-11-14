package com.bw.cardashboard.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * 纵/横向 G 值圆盘指示器（量程 ±0.6G）。
 */
public class GForceView extends View {

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float lonG = 0f; // 纵向 (+加速向前/上, -制动向下)
    private float latG = 0f; // 横向 (+右, -左)

    public GForceView(Context context) {
        this(context, null);
    }

    public GForceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(Color.parseColor("#660A0F18"));

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1.5f);
        gridPaint.setColor(Color.parseColor("#33475F"));

        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setColor(Color.parseColor("#55F59E0B"));

        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(Color.parseColor("#F59E0B"));
    }

    public void setGForce(float lonG, float latG) {
        this.lonG = lonG;
        this.latG = latG;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float r = Math.min(w, h) * 0.45f;

        canvas.drawCircle(cx, cy, r, bgPaint);
        canvas.drawCircle(cx, cy, r, gridPaint);
        canvas.drawCircle(cx, cy, r * 0.5f, gridPaint);
        canvas.drawLine(cx - r, cy, cx + r, cy, gridPaint);
        canvas.drawLine(cx, cy - r, cx, cy + r, gridPaint);

        float maxG = 0.6f;
        float nx = Math.max(-1f, Math.min(1f, latG / maxG));
        float ny = Math.max(-1f, Math.min(1f, -lonG / maxG));
        float dx = cx + nx * (r * 0.85f);
        float dy = cy + ny * (r * 0.85f);

        canvas.drawCircle(dx, dy, r * 0.18f, glowPaint);
        canvas.drawCircle(dx, dy, r * 0.10f, dotPaint);
    }
}
