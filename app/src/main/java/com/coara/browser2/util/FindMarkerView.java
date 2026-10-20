package com.coara.browser2.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import java.util.Arrays;

public final class FindMarkerView extends View {

    public interface Listener {
        void onTickSelected(int index);
    }

    private static final long DRAG_THROTTLE_MS = 50L;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activeEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private final float trackWidth;
    private final float trackInset;
    private final float tickHeight;
    private final float activeHeight;
    private final float padding;
    private final float hitSlop;

    private float[] sortedFractions = new float[0];
    private int[] sortedIndices = new int[0];
    private int activeSorted = -1;
    private Listener listener;
    private long lastDispatch;
    private int lastDispatched = -1;

    public FindMarkerView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        trackWidth = 8f * density;
        trackInset = 2f * density;
        tickHeight = 3f * density;
        activeHeight = 5f * density;
        padding = 6f * density;
        hitSlop = 32f * density;
        trackPaint.setColor(0xB3FFFFFF);
        trackEdgePaint.setStyle(Paint.Style.STROKE);
        trackEdgePaint.setStrokeWidth(density);
        trackEdgePaint.setColor(0x40000000);
        tickPaint.setColor(0xFFF9AB00);
        activePaint.setColor(0xFFE8710A);
        activeEdgePaint.setStyle(Paint.Style.STROKE);
        activeEdgePaint.setStrokeWidth(density);
        activeEdgePaint.setColor(0xFFFFFFFF);
        setVisibility(GONE);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public int preferredWidthPx() {
        return Math.round(28f * density);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public boolean hasTicks() {
        return sortedFractions.length > 0;
    }

    public void setTicks(float[] fractions) {
        int n = fractions == null ? 0 : fractions.length;
        long[] keys = new long[n];
        for (int i = 0; i < n; i++) {
            float f = Math.max(0f, Math.min(1f, fractions[i]));
            keys[i] = (((long) Math.round(f * 1000000f)) << 20) | i;
        }
        Arrays.sort(keys);
        float[] sf = new float[n];
        int[] si = new int[n];
        for (int i = 0; i < n; i++) {
            si[i] = (int) (keys[i] & 0xFFFFF);
            sf[i] = (keys[i] >> 20) / 1000000f;
        }
        sortedFractions = sf;
        sortedIndices = si;
        activeSorted = -1;
        lastDispatched = -1;
        setVisibility(n > 0 ? VISIBLE : GONE);
        invalidate();
    }

    public void clear() {
        if (sortedFractions.length == 0 && getVisibility() == GONE) {
            return;
        }
        sortedFractions = new float[0];
        sortedIndices = new int[0];
        activeSorted = -1;
        lastDispatched = -1;
        setVisibility(GONE);
        invalidate();
    }

    public void setActiveIndex(int index) {
        int found = -1;
        for (int i = 0; i < sortedIndices.length; i++) {
            if (sortedIndices[i] == index) {
                found = i;
                break;
            }
        }
        if (found != activeSorted) {
            activeSorted = found;
            lastDispatched = index;
            invalidate();
        }
    }

    public void setActiveFraction(float fraction) {
        if (fraction < 0f || sortedFractions.length == 0) {
            return;
        }
        int nearest = nearestSorted(fraction);
        if (nearest >= 0 && nearest != activeSorted) {
            activeSorted = nearest;
            lastDispatched = sortedIndices[nearest];
            invalidate();
        }
    }

    private float span() {
        return Math.max(1f, getHeight() - 2f * padding - tickHeight);
    }

    private int nearestSorted(float fraction) {
        int n = sortedFractions.length;
        if (n == 0) {
            return -1;
        }
        int lo = 0;
        int hi = n - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (sortedFractions[mid] < fraction) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        int best = lo;
        if (lo > 0 && Math.abs(sortedFractions[lo - 1] - fraction) <= Math.abs(sortedFractions[lo] - fraction)) {
            best = lo - 1;
        }
        return best;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int n = sortedFractions.length;
        if (n == 0) {
            return;
        }
        float w = getWidth();
        float h = getHeight();
        float right = w - trackInset;
        float left = right - trackWidth;
        rect.set(left, padding * 0.5f, right, h - padding * 0.5f);
        float radius = trackWidth / 2f;
        canvas.drawRoundRect(rect, radius, radius, trackPaint);
        canvas.drawRoundRect(rect, radius, radius, trackEdgePaint);
        float span = span();
        int lastRow = Integer.MIN_VALUE;
        float inset = density;
        for (int i = 0; i < n; i++) {
            float y = padding + sortedFractions[i] * span;
            int row = (int) (y / tickHeight);
            if (row == lastRow) {
                continue;
            }
            lastRow = row;
            rect.set(left + inset, y, right - inset, y + tickHeight);
            canvas.drawRect(rect, tickPaint);
        }
        if (activeSorted >= 0 && activeSorted < n) {
            float y = padding + sortedFractions[activeSorted] * span + tickHeight / 2f;
            rect.set(left - density, y - activeHeight / 2f, right + density, y + activeHeight / 2f);
            canvas.drawRect(rect, activePaint);
            canvas.drawRect(rect, activeEdgePaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (sortedFractions.length == 0) {
            return false;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            getParent().requestDisallowInterceptTouchEvent(true);
            dispatchAt(event.getY(), true);
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            dispatchAt(event.getY(), false);
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            dispatchAt(event.getY(), true);
            performClick();
            return true;
        }
        return action == MotionEvent.ACTION_CANCEL;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void dispatchAt(float y, boolean force) {
        long now = SystemClock.uptimeMillis();
        if (!force && now - lastDispatch < DRAG_THROTTLE_MS) {
            return;
        }
        float fraction = (y - padding) / span();
        int sorted = nearestSorted(Math.max(0f, Math.min(1f, fraction)));
        if (sorted < 0) {
            return;
        }
        float tickY = padding + sortedFractions[sorted] * span();
        if (Math.abs(tickY - y) > hitSlop) {
            return;
        }
        int index = sortedIndices[sorted];
        if (!force && index == lastDispatched && sorted == activeSorted) {
            return;
        }
        lastDispatch = now;
        lastDispatched = index;
        activeSorted = sorted;
        invalidate();
        Listener l = listener;
        if (l != null) {
            l.onTickSelected(index);
        }
    }
}
