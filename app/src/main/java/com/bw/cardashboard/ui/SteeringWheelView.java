package com.bw.cardashboard.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * 方向盘实时转角矢量视图。
 */
public class SteeringWheelView extends View {

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float angleDeg = 0f;

    public SteeringWheelView(Context context) {
        this(context, null);
    }

    public SteeringWheelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setColor(Color.parseColor("#22D3EE"));

        spokePaint.setStyle(Paint.Style.STROKE);
        spokePaint.setStrokeCap(Paint.Cap.ROUND);
        spokePaint.setColor(Color.parseColor("#22D3EE"));

        centerPaint.setStyle(Paint.Style.FILL);
        centerPaint.setColor(Color.parseColor("#22D3EE"));
    }

    public void setAngleDeg(float deg) {
        if (Math.abs(this.angleDeg - deg) > 0.2f) {
            this.angleDeg = deg;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float r = Math.min(w, h) * 0.40f;

        ringPaint.setStrokeWidth(Math.max(4f, r * 0.16f));
        spokePaint.setStrokeWidth(Math.max(3.5f, r * 0.14f));

        canvas.save();
        canvas.rotate(angleDeg, cx, cy);

        // 外圈
        canvas.drawCircle(cx, cy, r, ringPaint);
        // 三辐条（T字形）
        canvas.drawLine(cx - r * 0.85f, cy, cx + r * 0.85f, cy, spokePaint);
        canvas.drawLine(cx, cy, cx, cy + r * 0.85f, spokePaint);
        // 中心气囊盖圆点
        canvas.drawCircle(cx, cy, r * 0.22f, centerPaint);

        canvas.restore();
    }
}
