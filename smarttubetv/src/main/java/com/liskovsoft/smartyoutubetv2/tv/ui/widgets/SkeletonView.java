package com.liskovsoft.smartyoutubetv2.tv.ui.widgets;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * HomeTube: loading placeholder in the shape of the page (row titles and cards) with a soft shimmer,
 * instead of a spinning circle. Shown only while a page has no content yet.
 */
public class SkeletonView extends View {
    private static final int BASE_COLOR = 0xFF1E1E1E;
    private static final int HIGHLIGHT_COLOR = 0xFF2B2B2B;
    private static final long SHIMMER_DURATION_MS = 1400;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix mShaderMatrix = new Matrix();
    private final RectF mRect = new RectF();
    private final float mDensity;
    private final int mStartPx;
    private final int mTopPx;
    private final int mCardWidthPx;
    private final int mCardGapPx;
    private ValueAnimator mAnimator;
    private float mShimmerX;

    /**
     * @param startPx left edge of the content (sidebar rail + padding)
     * @param topPx top of the first row title
     * @param cardWidthPx card width in rows
     * @param cardGapPx space between cards
     */
    public SkeletonView(Context context, int startPx, int topPx, int cardWidthPx, int cardGapPx) {
        super(context);
        mDensity = context.getResources().getDisplayMetrics().density;
        mStartPx = startPx;
        mTopPx = topPx;
        mCardWidthPx = cardWidthPx;
        mCardGapPx = cardGapPx;
        setAlpha(0f);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animate().alpha(1f).setDuration(200).start();

        mAnimator = ValueAnimator.ofFloat(0f, 1f);
        mAnimator.setDuration(SHIMMER_DURATION_MS);
        mAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mAnimator.setInterpolator(new LinearInterpolator());
        mAnimator.addUpdateListener(animation -> {
            float width = getWidth();
            mShimmerX = -width / 2f + (float) animation.getAnimatedValue() * width * 2f;
            invalidate();
        });
        mAnimator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mAnimator != null) {
            mAnimator.cancel();
            mAnimator = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float band = Math.max(w / 3f, 1f);
        mPaint.setShader(new LinearGradient(0, 0, band, 0,
                new int[] {BASE_COLOR, HIGHLIGHT_COLOR, BASE_COLOR}, new float[] {0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mPaint.getShader() != null) {
            mShaderMatrix.setTranslate(mShimmerX, 0);
            mPaint.getShader().setLocalMatrix(mShaderMatrix);
        }

        float radius = 12 * mDensity;
        float thumbHeight = mCardWidthPx * 9f / 16f;
        float rowHeight = 30 * mDensity + thumbHeight + 70 * mDensity + 28 * mDensity;
        float y = mTopPx;

        while (y < getHeight()) {
            // row title
            drawRect(canvas, mStartPx, y, mStartPx + 160 * mDensity, y + 18 * mDensity, 6 * mDensity);
            float cardTop = y + 34 * mDensity;

            float x = mStartPx;
            while (x < getWidth()) {
                drawRect(canvas, x, cardTop, x + mCardWidthPx, cardTop + thumbHeight, radius);
                float textTop = cardTop + thumbHeight + 12 * mDensity;
                drawRect(canvas, x, textTop, x + mCardWidthPx * 0.85f, textTop + 14 * mDensity, 5 * mDensity);
                drawRect(canvas, x, textTop + 24 * mDensity, x + mCardWidthPx * 0.5f, textTop + 36 * mDensity, 5 * mDensity);
                x += mCardWidthPx + mCardGapPx;
            }

            y += rowHeight;
        }
    }

    private void drawRect(Canvas canvas, float left, float top, float right, float bottom, float radius) {
        mRect.set(left, top, right, bottom);
        canvas.drawRoundRect(mRect, radius, radius, mPaint);
    }
}
