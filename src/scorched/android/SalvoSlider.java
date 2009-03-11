package scorched.android;

import java.util.Map;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;

/**
 * A slider widget which the user can slide back and forth.
 * 
 * There are arrows on the ends for fine adjustments.
 *
 * Most functions are synchronized on mState to prevent wackiness.
 * The mState mutex should generally be held a pretty short amount of time.
 */
public class SalvoSlider extends View {
    /*================= Types =================*/
    /** Describes the state of this slider */
    public enum SliderState {
        /** The slider is not in use. 
         * Only a blank space will be drawn.
         * Touch will be disabled.
         */
        DISABLED,

        /** The slider will be drawn using a bar graphic.
         * Touch will be enabled.
         */
        BAR,

        /** The slider will be drawn using the angle graphic.
         * Touch will be enabled.
         */
        ANGLE,
    };

    /** Used to hook up Listeners */
    public static interface Listener {
        void onPositionChange(int val);
    }

    /*================= Constants =================*/
    private static final String TAG = "SalvoSlider";

    private static final int BUTTON_PERCENT = 20;
    
    /*================= Members =================*/
    private SliderState mState;

    ////// User input stuff
    /** Listener to notify when slider value changes */
    private Listener mListener;

    /** Minimum slider value */
    public int mMin;
    
    /** Maximum slider value. */
    public int mMax;

    /** Current slider value */
    private int mVal;

    ////// Current configuration
    /** Current slider color. If mColor == Color.WHITE then the slider is
     * disabled. */
    private int mColor;

    /** Current slider width */
    private int mWidth;
    
    /** Current slider height */
    private int mHeight;
    
    ////// Things computed by cacheStuff()
    /** Current left boundary of slidable area */
    private int mLeftBound;

    /** Current right boundary of slidable area */
    private int mRightBound;

    /** Shader for bar */
    private Shader mBarShader;

    /** Gradient paint for bar */
    private Paint mGradPaint;

    ////// Temporaries
    private Paint mTempPaint; 

    private Rect mTempRect;

    /*================= Access =================*/

    /*================= Operations =================*/
    /** Cache a bunch of stuff that we don't want to have to recalculate on
     * each draw().
     */
    private void cacheStuff() {
        assert Thread.holdsLock(mState);
        mLeftBound = (mWidth * BUTTON_PERCENT) / 100;
        mRightBound = (mWidth * (100 - BUTTON_PERCENT)) / 100;

        int colors[] = new int[2];
        colors[0] = Color.WHITE;
        colors[1] = mColor;
        mBarShader = new LinearGradient(0, 0, 0, mHeight,
                            colors, null, Shader.TileMode.REPEAT);
        mGradPaint = new Paint();
        mGradPaint.setShader(mBarShader);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        synchronized (mState) {
            super.onDraw(canvas);
            switch (mState) {
            case DISABLED:
                mTempPaint.setColor(Color.BLACK);
                mTempRect.set(0, 0, mWidth, mHeight);
                canvas.drawRect(mTempRect, mTempPaint);
                break;

            case BAR:
                drawEndButtons(canvas);

                int x = mLeftBound + 
                    (((mRightBound - mLeftBound) * mVal) / 
                        Math.abs(mMax - mMin));
                mTempRect.set(mLeftBound, 0, x, mHeight);
                canvas.drawRect(mTempRect, mGradPaint);
                mTempPaint.setColor(Color.BLACK);
                mTempRect.set(mLeftBound, mHeight + 1, x, mRightBound);
                canvas.drawRect(mTempRect, mTempPaint);
                break;

            case ANGLE:
                drawEndButtons(canvas);

                mTempPaint.setColor(Color.BLUE);
                mTempRect.set(mLeftBound, 0, mRightBound, mHeight);
                canvas.drawRect(mTempRect, mTempPaint);
                break;
            }
        }
    }

    private void drawEndButtons(Canvas canvas) {
        // Draw end buttons
        mTempPaint.setColor(Color.GRAY);
        mTempRect.set(0, 0, mLeftBound, mHeight);
        canvas.drawRect(mTempRect, mTempPaint);

        mTempPaint.setColor(Color.CYAN);
        mTempRect.set(mRightBound, 0, mWidth, mHeight);
        canvas.drawRect(mTempRect, mTempPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent me) {
        synchronized (mState) {
            if (mState == SliderState.DISABLED) {
                return true;
            }
            /*int action = me.getAction();
            if ((action == MotionEvent.ACTION_DOWN) ||
                (action == MotionEvent.ACTION_MOVE) ||
                (action == MotionEvent.ACTION_UP)) 
            {
                int newVal = me.getX();
            }*/
            return true;
        }
    }

    /** Change the slider state to something else.
     *  This can be called from non-UI threads. */
    public void setState(SliderState state, 
    					 Listener listener,
                         int min, int max, int val, int color)
    {
    	Log.w(TAG, "setState state=" + state + 
    			",min=" + min + ",max=" + max +
    			",color=" + color);
        synchronized (mState) {
            mState = state;
            mListener = listener;

            // user input stuff
            assert(mListener != null);
            mMin = min;
            mMax = max;
            mVal = val;

            // configuration
            mColor = color;
            mWidth = getWidth();
            mHeight = getHeight();
            cacheStuff();
        }

        postInvalidate();
    }

    /*================= Lifecycle =================*/
    private void construct() {
        mState = SliderState.DISABLED;
        setFocusable(true);

        // Temporaries
        mTempPaint = new Paint();
        mTempRect = new Rect();
    }

    /** Constructor for "manual" instantiation */
    public SalvoSlider(Context context) {
        super(context);
        construct();
    }
    
    /** Contructor for layout file */
    public SalvoSlider(Context context, AttributeSet attrs) {
        super(context, attrs);
        construct();
    }
}
