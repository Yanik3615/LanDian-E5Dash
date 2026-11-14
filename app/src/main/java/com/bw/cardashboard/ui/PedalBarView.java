package com.bw.cardashboard.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * 圆角渐变踏板开度进度条。
 */
public class PedalBarView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private float progress = 0f; // 0..100
    private int startColor = Color.parseColor("#22D3EE");
    private int endColor = Color.parseColor("#F59E0B");

    public PedalBarView(Context context) {
        this(context, null);
    }

    public PedalBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        trackPaint.setStyle(Paint.Style.FILL);
        trackPaint.setColor(Color.parseColor("#1E293B"));
    }

    public void setColors(int startColor, int endColor) {
        this.startColor = startColor;
        this.endColor = endColor;
        updateShader();
        invalidate();
    }

    public void setProgress(float pct) {
        float clamped = Math.max(0f, Math.min(100f, pct));
        if (Math.abs(this.progress - clamped) > 0.4f) {
            this.progress = clamped;
            invalidate();
        }
    }

    private void updateShader() {
        int w = getWidth();
        if (w > 0) {
            fillPaint.setShader(new LinearGradient(
                    0, 0, w, 0, startColor, endColor, Shader.TileMode.CLAMP));
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateShader();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float r = h / 2f;

        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, r, r, trackPaint);

        if (progress > 0.5f) {
            float fw = Math.max(h, w * (progress / 100f));
            rect.set(0, 0, Math.min(w, fw), h);
            canvas.drawRoundRect(rect, r, r, fillPaint);
        }
    }
}
