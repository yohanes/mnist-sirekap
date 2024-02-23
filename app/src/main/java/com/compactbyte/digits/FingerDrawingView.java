package com.compactbyte.digits;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.nio.ByteBuffer;

public class FingerDrawingView extends View {
    private Bitmap mBitmap;
    private Canvas mCanvas;
    private Path mPath;
    private Paint mBitmapPaint;
    Context context;


    private Paint mPaint;
    private Paint bgPaint;

    View.OnClickListener touchUpListener;

    public void addTouchUpListener(View.OnClickListener listener) {
        touchUpListener = listener;
    }

    public FingerDrawingView(Context c, AttributeSet attrs) {
        super(c, attrs);
        bgPaint = new Paint();
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(Color.BLACK);

        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setDither(true);
        mPaint.setColor(Color.WHITE);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeWidth(80);

        context = c;
        mPath = new Path();
        mBitmapPaint = new Paint(Paint.DITHER_FLAG);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (mBitmap == null) {
            mBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            mCanvas = new Canvas(mBitmap);
            clearCanvas();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawBitmap(mBitmap, 0, 0, mBitmapPaint);
        canvas.drawPath(mPath, mPaint);
    }

    private float mX, mY;
    private static final float TOUCH_TOLERANCE = 4;

    private void touch_start(float x, float y) {
        mPath.reset();
        mPath.moveTo(x, y);
        mX = x;
        mY = y;
    }
    private void touch_move(float x, float y) {
        float dx = Math.abs(x - mX);
        float dy = Math.abs(y - mY);
        if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
            mPath.quadTo(mX, mY, (x + mX)/2, (y + mY)/2);
            mX = x;
            mY = y;
        }
    }
    private void touch_up() {
        mPath.lineTo(mX, mY);
        // commit the path to our offscreen
        mCanvas.drawPath(mPath, mPaint);
        // kill this so we don't double draw
        mPath.reset();
    }

    public Bitmap getContent(int sx, int sy) {
        //get bitmap from mCanvas
        Bitmap thumbnail = Bitmap.createBitmap(mBitmap, rect_left, rect_top, rect_right - rect_left, rect_bottom - rect_top);
        //resize the bitmap to 28x28
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(thumbnail, sx, sy, true);
        return scaledBitmap;
    }


    public ByteBuffer getAsByteBuffer(int sx, int sy) {
        Bitmap scaledBitmap = getContent(sx, sy);
        //convert to bytebuffer
        int [] intValues = new int[scaledBitmap.getWidth() * scaledBitmap.getHeight()];
        float [] floatValues = new float[intValues.length];
        scaledBitmap.getPixels(intValues, 0, scaledBitmap.getWidth(), 0, 0, scaledBitmap.getWidth(), scaledBitmap.getHeight());
        //invert the bitmap color

        ByteBuffer imgData = ByteBuffer.allocateDirect(1 * scaledBitmap.getWidth() * scaledBitmap.getHeight() * 1 * 4);
        imgData.order(java.nio.ByteOrder.nativeOrder());
        imgData.rewind();

        for (int i = 0; i < intValues.length; ++i) {
            final int val = intValues[i];
            float normalizedPixelValue = ((val >> 16 & 0xFF) + (val >> 8 & 0xFF) + (val & 0xFF)) / 3.0f / 255.0f;
            floatValues[i] = normalizedPixelValue;
            imgData.putFloat(normalizedPixelValue);
        }

        return imgData;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touch_start(x, y);
                invalidate();
                break;
            case MotionEvent.ACTION_MOVE:
                touch_move(x, y);
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
                touch_up();
                invalidate();
                if (touchUpListener != null) {
                    touchUpListener.onClick(this);
                }
                break;
        }
        return true;
    }

    //rectangle inside the canvas
    int rect_left = 0;
    int rect_top = 0;
    int rect_right = 0;
    int rect_bottom = 0;

    public void clearCanvas() {
        if (mCanvas == null) {
            return;
        }
        mCanvas.drawColor(Color.WHITE, PorterDuff.Mode.CLEAR);
        mCanvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);

        //draw a rectangle at center of the screen, around 75% of the screen
        int size = (int) (Math.min(getWidth(), getHeight()) * 0.75);
        int left = (getWidth() - size) / 2;
        rect_left = left;
        int top = (getHeight() - size) / 2;
        rect_top = top;

        int right = left + size;
        rect_right = right;
        int bottom = top + size;
        rect_bottom = bottom;
        //extend a little bit accounting for stroke width
        left -= mPaint.getStrokeWidth();
        top -= mPaint.getStrokeWidth();
        right += mPaint.getStrokeWidth();
        bottom += mPaint.getStrokeWidth();

        mCanvas.drawRect(left, top, right, bottom, mPaint);
        invalidate();

    }
}
