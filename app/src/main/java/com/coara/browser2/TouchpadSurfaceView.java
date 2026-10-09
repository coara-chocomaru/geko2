package com.coara.browser2;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import com.coara.browser2.util.TouchpadController;
import com.coara.browser2.util.TouchpadSettings;

public final class TouchpadSurfaceView extends View {
    public interface Listener {
        void onMove(float dx, float dy);
        void onClick(int button);
        void onButtonDown(int button);
        void onButtonUp(int button);
        void onCancel();
        void onStick(float x, float y);
        void onStickTap();
    }

    private static final int NONE = -1;
    private static final float STICK_SEND_STEP = 0.015f;
    private static final float CURSOR_GAIN = 1.35f;
    private static final long MOUSE_TAP_MS = 400L;
    private static final long STICK_TAP_MS = 220L;
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint surfacePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stickPanelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint buttonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stickBasePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stickRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stickMarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stickKnobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path markPath = new Path();
    private final RectF bounds = new RectF();
    private final RectF padBounds = new RectF();
    private final RectF stickPanel = new RectF();
    private final RectF mouseArea = new RectF();
    private final Listener listener;
    private final float density;
    private int leftButtonId = NONE;
    private int rightButtonId = NONE;
    private int mouseId = NONE;
    private int stickId = NONE;
    private float lastX;
    private float lastY;
    private float mouseDownX;
    private float mouseDownY;
    private long mouseDownTime;
    private boolean mouseMoved;
    private boolean buttonDragStarted;
    private float buttonLastX;
    private float buttonLastY;
    private float stickDownX;
    private float stickDownY;
    private long stickDownTime;
    private boolean stickMoved;
    private boolean stickEnabled = true;
    private boolean stickOnLeft = true;
    private String stickLabel = "";
    private float stickCx;
    private float stickCy;
    private float stickR;
    private float stickTravel;
    private float knobR;
    private float knobDx;
    private float knobDy;
    private float stickSentX;
    private float stickSentY;
    private float splitX;
    private int geometryWidth = -1;
    private int geometryHeight = -1;

    public TouchpadSurfaceView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        density = getResources().getDisplayMetrics().density;
        setFocusable(false);
        setClickable(true);
        backgroundPaint.setColor(0xE81A1A1E);
        surfacePaint.setColor(0xFF25252A);
        stickPanelPaint.setColor(0xFF2C2C34);
        buttonPaint.setColor(0xFF303036);
        dividerPaint.setColor(0xFF4A4A51);
        dividerPaint.setStrokeWidth(Math.max(1f, density));
        textPaint.setColor(0xFFEDEDF0);
        textPaint.setTextSize(Math.max(12f, 14f * density));
        textPaint.setTextAlign(Paint.Align.CENTER);
        stickBasePaint.setStyle(Paint.Style.FILL);
        stickBasePaint.setColor(0xFF31313A);
        stickRingPaint.setStyle(Paint.Style.STROKE);
        stickRingPaint.setStrokeWidth(Math.max(1.5f, 1.5f * density));
        stickRingPaint.setColor(0xFF5A5A66);
        stickMarkPaint.setStyle(Paint.Style.FILL);
        stickMarkPaint.setColor(0xFF7C7C89);
        stickKnobPaint.setStyle(Paint.Style.FILL);
        setContentDescription("タッチパッド");
    }

    public void applyStickSettings(SharedPreferences preferences) {
        boolean enabled = TouchpadSettings.isStickEnabled(preferences);
        int mode = TouchpadSettings.getStickMode(preferences);
        boolean onLeft = TouchpadSettings.getStickSide(preferences) == TouchpadSettings.SIDE_LEFT;
        stickLabel = TouchpadSettings.stickShortLabel(mode);
        if ((!enabled || onLeft != stickOnLeft) && stickId != NONE) {
            releaseStick();
        }
        stickEnabled = enabled;
        stickOnLeft = onLeft;
        geometryWidth = -1;
        TouchpadController.setStickMode(mode);
        invalidate();
    }

    private float dp(float value) {
        return value * density;
    }

    private float buttonHeight() {
        return Math.max(dp(52), getHeight() * 0.22f);
    }

    private boolean inButtonRow(float y) {
        return y >= getHeight() - buttonHeight();
    }

    private boolean isLeftButton(float x) {
        return x < getWidth() * 0.5f;
    }

    private int findPointerIndex(MotionEvent event, int id) {
        for (int i = 0; i < event.getPointerCount(); i++) {
            if (event.getPointerId(i) == id) {
                return i;
            }
        }
        return NONE;
    }

    private void ensureGeometry() {
        if (geometryWidth == getWidth() && geometryHeight == getHeight()) {
            return;
        }
        geometryWidth = getWidth();
        geometryHeight = getHeight();
        float gap = dp(8);
        padBounds.set(gap, gap, getWidth() - gap, getHeight() - buttonHeight() - gap);
        float padH = padBounds.height();
        float padW = padBounds.width();
        float margin = dp(10);
        float r = Math.min(dp(60), Math.min(padH * 0.5f - dp(8), (padW * 0.45f - margin * 2f) * 0.5f));
        stickR = Math.max(dp(24), r);
        knobR = stickR * 0.42f;
        stickTravel = stickR - knobR * 0.45f;
        float panelW = stickR * 2f + margin * 2f;
        if (stickOnLeft) {
            stickPanel.set(padBounds.left, padBounds.top, padBounds.left + panelW, padBounds.bottom);
            mouseArea.set(stickPanel.right, padBounds.top, padBounds.right, padBounds.bottom);
            splitX = stickPanel.right;
        } else {
            stickPanel.set(padBounds.right - panelW, padBounds.top, padBounds.right, padBounds.bottom);
            mouseArea.set(padBounds.left, padBounds.top, stickPanel.left, padBounds.bottom);
            splitX = stickPanel.left;
        }
        stickCx = stickPanel.centerX();
        stickCy = stickPanel.centerY();
    }

    private boolean inStickZone(float x) {
        if (!stickEnabled) {
            return false;
        }
        ensureGeometry();
        return stickOnLeft ? x < splitX : x >= splitX;
    }

    private void updateStick(float x, float y) {
        float dx = x - stickCx;
        float dy = y - stickCy;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist > stickTravel && dist > 0f) {
            float scale = stickTravel / dist;
            dx *= scale;
            dy *= scale;
        }
        knobDx = dx;
        knobDy = dy;
        float vx = stickTravel > 0f ? dx / stickTravel : 0f;
        float vy = stickTravel > 0f ? dy / stickTravel : 0f;
        if (Math.abs(vx - stickSentX) >= STICK_SEND_STEP || Math.abs(vy - stickSentY) >= STICK_SEND_STEP) {
            stickSentX = vx;
            stickSentY = vy;
            listener.onStick(vx, vy);
        }
        invalidate();
    }

    private void releaseStick() {
        boolean wasActive = stickId != NONE;
        stickId = NONE;
        knobDx = 0f;
        knobDy = 0f;
        if (wasActive || stickSentX != 0f || stickSentY != 0f) {
            stickSentX = 0f;
            stickSentY = 0f;
            listener.onStick(0f, 0f);
        }
        invalidate();
    }

    private void reset() {
        leftButtonId = NONE;
        rightButtonId = NONE;
        mouseId = NONE;
        stickId = NONE;
        mouseMoved = false;
        stickMoved = false;
        buttonDragStarted = false;
        buttonLastX = 0f;
        buttonLastY = 0f;
        knobDx = 0f;
        knobDy = 0f;
    }

    private void releaseAll(boolean cancel) {
        if (stickId != NONE) {
            releaseStick();
        }
        if (leftButtonId != NONE) {
            listener.onButtonUp(0);
        }
        if (rightButtonId != NONE) {
            listener.onButtonUp(2);
        }
        if (cancel) {
            listener.onCancel();
        }
        reset();
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (listener != null && (stickId != NONE || leftButtonId != NONE || rightButtonId != NONE)) {
            releaseAll(true);
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility != View.VISIBLE && listener != null && (stickId != NONE || leftButtonId != NONE || rightButtonId != NONE)) {
            releaseAll(true);
        }
    }

    private void pointerDown(MotionEvent event, int index) {
        int id = event.getPointerId(index);
        float x = event.getX(index);
        float y = event.getY(index);
        if (inButtonRow(y)) {
            if (isLeftButton(x)) {
                if (leftButtonId != NONE) {
                    return;
                }
                leftButtonId = id;
                listener.onButtonDown(0);
            } else {
                if (rightButtonId != NONE) {
                    return;
                }
                rightButtonId = id;
                listener.onButtonDown(2);
            }
            buttonLastX = x;
            buttonLastY = y;
            buttonDragStarted = false;
            invalidate();
            return;
        }
        if (inStickZone(x)) {
            if (stickId != NONE) {
                return;
            }
            stickId = id;
            stickSentX = 0f;
            stickSentY = 0f;
            stickDownX = x;
            stickDownY = y;
            stickDownTime = event.getEventTime();
            stickMoved = false;
            updateStick(x, y);
            return;
        }
        if (mouseId != NONE) {
            return;
        }
        mouseId = id;
        lastX = x;
        lastY = y;
        mouseDownX = x;
        mouseDownY = y;
        mouseDownTime = event.getEventTime();
        mouseMoved = false;
    }

    private void pointerMove(MotionEvent event) {
        if (stickId != NONE) {
            int index = findPointerIndex(event, stickId);
            if (index >= 0) {
                float x = event.getX(index);
                float y = event.getY(index);
                if (!stickMoved) {
                    float ox = x - stickDownX;
                    float oy = y - stickDownY;
                    if (ox * ox + oy * oy > dp(10) * dp(10)) {
                        stickMoved = true;
                    }
                }
                updateStick(x, y);
            }
        }
        if (mouseId != NONE) {
            int index = findPointerIndex(event, mouseId);
            if (index >= 0) {
                float x = event.getX(index);
                float y = event.getY(index);
                float dx = x - lastX;
                float dy = y - lastY;
                lastX = x;
                lastY = y;
                if (!mouseMoved) {
                    float ox = x - mouseDownX;
                    float oy = y - mouseDownY;
                    if (ox * ox + oy * oy > dp(10) * dp(10)) {
                        mouseMoved = true;
                    }
                }
                if (dx != 0f || dy != 0f) {
                    listener.onMove(dx * CURSOR_GAIN, dy * CURSOR_GAIN);
                }
            }
        } else if (leftButtonId != NONE || rightButtonId != NONE) {
            int buttonId = leftButtonId != NONE ? leftButtonId : rightButtonId;
            int index = findPointerIndex(event, buttonId);
            if (index >= 0) {
                float x = event.getX(index);
                float y = event.getY(index);
                float dx = x - buttonLastX;
                float dy = y - buttonLastY;
                buttonLastX = x;
                buttonLastY = y;
                boolean inPad = !inButtonRow(y);
                if (buttonDragStarted || (inPad && Math.abs(dx) + Math.abs(dy) >= dp(1.5f))) {
                    buttonDragStarted = true;
                    listener.onMove(dx * CURSOR_GAIN, dy * CURSOR_GAIN);
                }
            }
        }
    }

    private void pointerUp(MotionEvent event, int index) {
        int id = event.getPointerId(index);
        long now = event.getEventTime();
        if (id == stickId) {
            boolean tap = !stickMoved && now - stickDownTime <= STICK_TAP_MS;
            releaseStick();
            if (tap) {
                listener.onStickTap();
            }
            return;
        }
        if (id == leftButtonId) {
            leftButtonId = NONE;
            listener.onButtonUp(0);
            if (rightButtonId == NONE) {
                buttonDragStarted = false;
            }
            invalidate();
            return;
        }
        if (id == rightButtonId) {
            rightButtonId = NONE;
            listener.onButtonUp(2);
            if (leftButtonId == NONE) {
                buttonDragStarted = false;
            }
            invalidate();
            return;
        }
        if (id == mouseId) {
            boolean tap = !mouseMoved && now - mouseDownTime <= MOUSE_TAP_MS
                    && leftButtonId == NONE && rightButtonId == NONE;
            mouseId = NONE;
            if (tap) {
                listener.onClick(0);
            }
        }
    }

    private void drawStick(Canvas canvas) {
        ensureGeometry();
        canvas.drawRoundRect(stickPanel, dp(14), dp(14), stickPanelPaint);
        float cx = stickCx;
        float cy = stickCy;
        canvas.drawCircle(cx, cy, stickR, stickBasePaint);
        canvas.drawCircle(cx, cy, stickR, stickRingPaint);
        float m = stickR * 0.80f;
        float s = stickR * 0.11f;
        drawMark(canvas, cx, cy - m, 0f, -1f, s);
        drawMark(canvas, cx, cy + m, 0f, 1f, s);
        drawMark(canvas, cx - m, cy, -1f, 0f, s);
        drawMark(canvas, cx + m, cy, 1f, 0f, s);
        boolean active = stickId != NONE;
        stickKnobPaint.setColor(active ? 0xFF6E9BFF : 0xFF565664);
        canvas.drawCircle(cx + knobDx, cy + knobDy, knobR, stickKnobPaint);
        stickRingPaint.setColor(active ? 0xFFB8CEFF : 0xFF8A8A98);
        canvas.drawCircle(cx + knobDx, cy + knobDy, knobR, stickRingPaint);
        stickRingPaint.setColor(0xFF5A5A66);
    }

    private void drawMark(Canvas canvas, float x, float y, float ux, float uy, float s) {
        markPath.rewind();
        markPath.moveTo(x + ux * s, y + uy * s);
        markPath.lineTo(x - ux * s + uy * s, y - uy * s + ux * s);
        markPath.lineTo(x - ux * s - uy * s, y - uy * s - ux * s);
        markPath.close();
        canvas.drawPath(markPath, stickMarkPaint);
    }

    private void drawHint(Canvas canvas) {
        float base = Math.max(12f, 14f * density);
        RectF area = stickEnabled ? mouseArea : padBounds;
        String line1 = "1本指: カーソル移動";
        String line2 = "タップ: 左クリック";
        String line3 = stickEnabled ? "スティック: " + stickLabel : null;
        float available = Math.max(dp(40), area.width() - dp(16));
        textPaint.setTextSize(base);
        float widest = Math.max(textPaint.measureText(line1), textPaint.measureText(line2));
        if (line3 != null) {
            widest = Math.max(widest, textPaint.measureText(line3));
        }
        float size = widest > available ? Math.max(dp(9), base * available / widest) : base;
        textPaint.setTextSize(size);
        float lineHeight = size * 1.55f;
        int lines = line3 != null ? 3 : 2;
        float firstCenter = area.centerY() - lineHeight * (lines - 1) * 0.5f;
        float offset = -(textPaint.ascent() + textPaint.descent()) * 0.5f;
        float centerX = area.centerX();
        textPaint.setAlpha(150);
        canvas.drawText(line1, centerX, firstCenter + offset, textPaint);
        canvas.drawText(line2, centerX, firstCenter + lineHeight + offset, textPaint);
        if (line3 != null) {
            textPaint.setAlpha(210);
            canvas.drawText(line3, centerX, firstCenter + lineHeight * 2f + offset, textPaint);
        }
        textPaint.setAlpha(255);
        textPaint.setTextSize(base);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        ensureGeometry();
        float r = dp(18);
        bounds.set(0, 0, getWidth(), getHeight());
        canvas.drawRoundRect(bounds, r, r, backgroundPaint);

        float buttons = buttonHeight();
        canvas.drawRoundRect(padBounds, dp(14), dp(14), surfacePaint);

        float y = getHeight() - buttons;
        buttonPaint.setColor(leftButtonId != NONE ? 0xFF4A4A52 : 0xFF303036);
        canvas.drawRect(0, y, getWidth() * 0.5f - dp(0.5f), getHeight(), buttonPaint);
        buttonPaint.setColor(rightButtonId != NONE ? 0xFF4A4A52 : 0xFF303036);
        canvas.drawRect(getWidth() * 0.5f + dp(0.5f), y, getWidth(), getHeight(), buttonPaint);
        dividerPaint.setColor(0xFF4A4A51);
        canvas.drawLine(getWidth() * 0.5f, y, getWidth() * 0.5f, getHeight(), dividerPaint);

        if (stickEnabled) {
            drawStick(canvas);
        }
        drawHint(canvas);
        float by = y + buttons * 0.5f - (textPaint.ascent() + textPaint.descent()) * 0.5f;
        canvas.drawText("左クリック", getWidth() * 0.25f, by, textPaint);
        canvas.drawText("右クリック", getWidth() * 0.75f, by, textPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event == null) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                reset();
                pointerDown(event, 0);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                pointerDown(event, event.getActionIndex());
                return true;
            case MotionEvent.ACTION_MOVE:
                pointerMove(event);
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                pointerUp(event, event.getActionIndex());
                return true;
            case MotionEvent.ACTION_UP:
                pointerUp(event, event.getActionIndex());
                releaseAll(false);
                return true;
            case MotionEvent.ACTION_CANCEL:
                releaseAll(true);
                return true;
            default:
                return true;
        }
    }
}
