package com.bw.cardashboard.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.bw.cardashboard.R;

/**
 * 「公路之王」实时变脸主视觉：
 * 使用提取的 3 张真实表情贴图（平静 / 狂飙怒视 / 咬牙制动），
 * 叠加实时呼吸微动、转向倾角、狂飙脉冲与制动抖动效果。
 */
public class RoadKingFaceView extends View {

    public enum Mood {
        PARK("P挡摸鱼中", 0xFF64748B),
        CRUISE("稳健巡航", 0xFF22D3EE),
        BOOST("狂飙", 0xFFF59E0B),
        DRIFT("弯道进攻", 0xFF38BDF8),
        REGEN("滑行回充", 0xFF10B981),
        BRAKE("全力制动!", 0xFFF43F5E);

        public final String label;
        public final int color;

        Mood(String label, int color) {
            this.label = label;
            this.color = color;
        }
    }

    private Mood mood = Mood.PARK;
    private long lastMoodChangeMs = 0L;
    private float accelG = 0f;
    private float steerDeg = 0f;
    private float animPhase = 0f;
    private boolean attached = false;

    private Bitmap bmpCalm;
    private Bitmap bmpFierce;
    private Bitmap bmpStrain;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF dstRect = new RectF();

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!attached) return;
            animPhase += 0.16f;
            if (animPhase > 62.83f) animPhase -= 62.83f;
            invalidate();
            postDelayed(this, 50);
        }
    };

    public RoadKingFaceView(Context context) {
        this(context, null);
    }

    public RoadKingFaceView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RoadKingFaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        bmpCalm = BitmapFactory.decodeResource(context.getResources(), R.drawable.king_calm);
        bmpFierce = BitmapFactory.decodeResource(context.getResources(), R.drawable.king_fierce);
        bmpStrain = BitmapFactory.decodeResource(context.getResources(), R.drawable.king_strain);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        removeCallbacks(tickRunnable);
        post(tickRunnable);
    }

    @Override
    protected void onDetachedFromWindow() {
        attached = false;
        removeCallbacks(tickRunnable);
        super.onDetachedFromWindow();
    }

    public Mood updateVehicle(float speedKmh, float accelG, float steerDeg, int gear) {
        this.accelG = accelG;
        this.steerDeg = steerDeg;

        Mood next;
        if ((gear == 4 || gear == 1) && speedKmh < 1.5f) {
            next = Mood.PARK;
        } else if (accelG <= -0.15f) {
            next = Mood.BRAKE;
        } else if (accelG >= 0.10f || speedKmh >= 105f) {
            next = Mood.BOOST;
        } else if (Math.abs(steerDeg) >= 30f && speedKmh >= 15f) {
            next = Mood.DRIFT;
        } else if (accelG <= -0.04f && speedKmh >= 15f) {
            next = Mood.REGEN;
        } else {
            next = Mood.CRUISE;
        }

        long now = System.currentTimeMillis();
        if (next != mood) {
            // 刹车与驻车立即响应；其余状态至少保持 600ms 防抖
            if (next == Mood.BRAKE || next == Mood.PARK || (now - lastMoodChangeMs >= 600L)) {
                mood = next;
                lastMoodChangeMs = now;
            }
        }
        return mood;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float cx = w * 0.5f;
        float cy = h * 0.52f;
        float size = Math.min(w, h) * 0.72f;
        float r = size * 0.52f;

        // 1. 底部动态光环与速度线
        float pulse = 1.0f + 0.035f * (float) Math.sin(animPhase * 1.5f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2.5f, r * 0.04f));
        paint.setColor((mood.color & 0x00FFFFFF) | 0x48000000);
        canvas.drawCircle(cx, cy, r * 1.12f * pulse, paint);

        paint.setColor((mood.color & 0x00FFFFFF) | 0x20000000);
        canvas.drawCircle(cx, cy, r * 1.26f * pulse, paint);

        if (mood == Mood.BOOST || mood == Mood.DRIFT) {
            paint.setStrokeWidth(Math.max(2f, r * 0.04f));
            paint.setColor((mood.color & 0x00FFFFFF) | 0x88000000);
            for (int i = 0; i < 8; i++) {
                double ang = (Math.PI * 2.0 * i / 8.0) + animPhase * 0.25;
                float r1 = r * 1.16f;
                float r2 = r * 1.38f;
                canvas.drawLine(
                        cx + (float) Math.cos(ang) * r1,
                        cy + (float) Math.sin(ang) * r1,
                        cx + (float) Math.cos(ang) * r2,
                        cy + (float) Math.sin(ang) * r2,
                        paint);
            }
        }

        // 2. 选择对应表情贴图
        Bitmap bmp;
        if (mood == Mood.BRAKE) {
            bmp = bmpStrain;
        } else if (mood == Mood.BOOST || mood == Mood.DRIFT) {
            bmp = bmpFierce;
        } else {
            bmp = bmpCalm;
        }
        if (bmp == null) return;

        // 3. 微动计算：呼吸起伏 + 狂飙前冲缩放 + 制动抖动 + 转向微倾
        float bob = (float) Math.sin(animPhase) * (mood == Mood.BOOST ? 4.5f : 2.5f);
        float shakeX = (mood == Mood.BRAKE)
                ? (float) Math.sin(animPhase * 4.5f) * 4.5f
                : (mood == Mood.BOOST ? (float) Math.sin(animPhase * 3.5f) * 1.5f : 0f);
        float scale = (mood == Mood.BOOST) ? (1.03f + 0.02f * (float) Math.sin(animPhase * 2f)) : 1.0f;
        float tilt = Math.max(-12f, Math.min(12f, steerDeg * 0.25f));

        float drawW = size * scale;
        float drawH = drawW * ((float) bmp.getHeight() / Math.max(1f, (float) bmp.getWidth()));
        if (drawH > h * 0.86f) {
            drawH = h * 0.86f;
            drawW = drawH * ((float) bmp.getWidth() / Math.max(1f, (float) bmp.getHeight()));
        }

        canvas.save();
        canvas.translate(cx + shakeX, cy + bob);
        canvas.rotate(tilt);
        dstRect.set(-drawW * 0.5f, -drawH * 0.5f, drawW * 0.5f, drawH * 0.5f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFFFFF);
        canvas.drawBitmap(bmp, null, dstRect, paint);
        canvas.restore();

        // 4. P 挡飘浮 Zzz
        if (mood == Mood.PARK) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xAA94A3B8);
            paint.setTextSize(r * 0.25f);
            float zOff = (animPhase % 3.14f) * 6f;
            canvas.drawText("Z", cx + r * 0.72f, cy - r * 0.62f - zOff, paint);
            paint.setTextSize(r * 0.18f);
            canvas.drawText("z", cx + r * 0.96f, cy - r * 0.84f - zOff * 1.2f, paint);
        }
    }
}
