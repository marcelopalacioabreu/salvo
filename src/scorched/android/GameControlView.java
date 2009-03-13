package scorched.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;


/**
 * Controller for the Scorched Android game.
 *
 * GameControlView gets input from the user, as well as events from other
 * parts of the system, and presents them to mGraphics and mModel.
 */
class GameControlView extends SurfaceView implements SurfaceHolder.Callback {
    /*================= Constants =================*/
    private static final String TAG = "GameControlView";

    public enum GameState {
        INIT_MOVE,
        PLAYER_MOVE,
        BALLISTICS,
        EXPLOSION,
        QUIT,
    };

    /*================= Types =================*/

    /*================= ScorchedThread =================*/
    class ScorchedThread extends Thread {
        /** The semaphore representing user input during a player's turn */
        private Object mUserInputSem = new Object();

        /** The game state we should transition to next. Protected by
          * mUserInputSem */
        private GameState mNextGameState;

        /** Represents the current controller state */
        volatile private GameState mGameState;

        /** Indicate whether or not the game is paused */
        private boolean mPaused = false;

        /** Pointer to the view */
        public Graphics mGraphics = null;

        /** Handle to the surface manager object we interact with */
        private SurfaceHolder mSurfaceHolder;

        /** Handle to the application context;
         *  used to (for example) fetch Drawables. */
        private Context mContext;

        /** Message handler used by thread to interact with TextView */
        private Handler mHandler;

        /** The zoom, pan settings that were in effect when the user started
         * pressing on the screen */
        private Graphics.ViewSettings mTouchViewSettings;

        /** The slider representing power */
        private SalvoSlider mPowerSlider;

        /** The slider representing angle */
        private SalvoSlider mAngleSlider;

        /** Last X coordinate the user touched (in game coordinates) */
        private float mTouchX;

        /** Last Y coordinate the user touched (in game coordinates) */
        private float mTouchY;

        public ScorchedThread(Graphics graphics,
                            SurfaceHolder surfaceHolder,
                            Context context,
                            Handler handler,
                            SalvoSlider powerSlider,
                            SalvoSlider angleSlider) {
            mGameState = GameState.PLAYER_MOVE;
            mGraphics = graphics;
            mSurfaceHolder = surfaceHolder;
            mContext = context;
            mHandler = handler;
            mTouchViewSettings = null;
            mPowerSlider = powerSlider;
            mAngleSlider = angleSlider;
        }

        /*================= Operations =================*/
        /** Shut down the thread */
        public void suicide() {
            mGameState = GameState.QUIT;

            // interrupt anybody in wait()
            this.interrupt();
        }

        /* Callback invoked when the surface dimensions change. */
        public void setSurfaceSize(int width, int height) {
            synchronized (mUserInputSem) {
                mGraphics.setSurfaceSize(width, height);
                mUserInputSem.notify();
            }
        }

        /*================= Main =================*/
        @Override
        public void run() {
            Log.w(TAG, "run(): waiting for surface to be created.");
            synchronized (mSurfaceHasBeenCreatedSem) {
                while (!mSurfaceHasBeenCreated) {
                    try {
                        mSurfaceHasBeenCreatedSem.wait();
                    }
                    catch (InterruptedException e) {
                        Log.w(TAG, "interrupted waiting for " +
                                        "mSurfaceHasBeenCreatedSem");
                        mGameState = GameState.s;
                    }
                }
            }
            Log.w(TAG, "run(): surface has been created.");

            // main loop
            mGameState = GameState.sInitMoveState;
            while (true) {
                mGameState.onEnter(mPowerSlider, mAngleSlider);
                GameState next = mGameState.execute();
                if (next != null) {
                    boolean endRound = 
                        mGameState.onExit(mPowerSlider, mAngleSlider);
                }

                mGameState = mGameState.execute(mModel, mGraphics, 
                                    mPowerSlider, mAngleSlider);

                // redraw canvas if necessary
                Canvas canvas = null;
                try {
                    if (mGraphics.needScreenUpdate()) {
                        canvas = mSurfaceHolder.lockCanvas(null);
                        mGameState
                        mGraphics.drawScreen(canvas);
                        if (weapon != null) {
                            mGraphics.drawWeapon(canvas, weapon, player);
                        }
                    }
                }
                finally {
                    if (canvas != null) {
                        // Don't leave the Surface in an inconsistent state
                        mSurfaceHolder.unlockCanvasAndPost(canvas);
                    }
                }
        }
                //if (mGameState.exitLoop()) {
                //     ... do stuff ...
                //}
            }
            catch (InterruptedException e) {
                Log.w(TAG, "interrupted: quitting.");
                mGameState = GameStateStrategy.sQuitState;
                return;
            }
        }

        private void redraw(Player player, Weapon weapon) {
            // redraw canvas if necessary
            Canvas canvas = null;
            try {
                if (mGraphics.needScreenUpdate()) {
                    canvas = mSurfaceHolder.lockCanvas(null);
                    mGameState
                    mGraphics.drawScreen(canvas);
                    if (weapon != null) {
                        mGraphics.drawWeapon(canvas, weapon, player);
                    }
                }
            }
            finally {
                if (canvas != null) {
                    // Don't leave the Surface in an inconsistent state
                    mSurfaceHolder.unlockCanvasAndPost(canvas);
                }
            }
        }

        /*================= Save / Restore =================*/
        /**
         * Dump game state to the provided Bundle. Typically called when the
         * Activity is being suspended.
         *
         * @return Bundle with this view's state
         */
        public Bundle saveState(Bundle map) {
            synchronized (mSurfaceHolder) {
                mModel.saveState(map);
            }
            return map;
        }

        /**
         * Restores game state from the indicated Bundle. Typically called
         * when the Activity is being restored after having been previously
         * destroyed.
         *
         * @param savedState Bundle containing the game state
         */
        public synchronized void restoreState(Bundle map) {
            synchronized (mSurfaceHolder) {
                mModel.restoreState(map);
                mPaused = false;
            }
        }

        /** Called when the user presses the fire button.
         *  Note: must not block in GUI thread */
        public void onFireButton() {
            synchronized (mUserInputSem) {
                mGameState.fireButton();
            }
        }

        /** Called when the user presses the zoom in button.
         *  Note: must not block in GUI thread */
        public void onZoomIn() {
            synchronized (mUserInputSem) {
                mGraphics.zoomIn();
                mUserInputSem.notify();
            }
        }

        /** Called when the user presses the zoom out button.
         *  Note: must not block in GUI thread */
        public void onZoomOut() {
            synchronized (mUserInputSem) {
                mGraphics.zoomOut();
                mUserInputSem.notify();
            }
        }

        /** Called (from the GUI thread) when the user moves the power
         * slider */
        public void onPowerChange(int val) {
            if (mGameState == GameState.PLAYER_MOVE) {
                synchronized (mUserInputSem) {
                    Player curPlayer = mModel.getCurPlayer();
                    curPlayer.setPower(val);
                    mGraphics.setNeedScreenRedraw();
                    mUserInputSem.notify();
                }
            }
        }

        /** Called when the user moves the angle slider
         *  Note: must not block in GUI thread
         *  Note: angle is given in degrees and must be converted to radians. */
        public void onAngleChange(int val) {
            if (mGameState == GameState.PLAYER_MOVE) {
                synchronized (mUserInputSem) {
                    Player curPlayer = mModel.getCurPlayer();
                    curPlayer.setAngleDeg(val);
                    mGraphics.setNeedScreenRedraw();
                    mUserInputSem.notify();
                }
            }
        }

        /** Handles a touchscreen event */
        public boolean onTouchEvent(MotionEvent me) {
            int action = me.getAction();
            boolean notify = false;
            synchronized (mUserInputSem) {
                if ((action == MotionEvent.ACTION_DOWN) ||
                    (action == MotionEvent.ACTION_MOVE) ||
                    (action == MotionEvent.ACTION_UP))
                {
                    if (mTouchViewSettings == null) {
                        mTouchViewSettings = mGraphics.getViewSettings();
                        mTouchX = mGraphics.onscreenXtoGameX(me.getX(),
                                    mTouchViewSettings);
                        mTouchY = mGraphics.onscreenYtoGameY(me.getY(),
                                    mTouchViewSettings);
                    }
                    else {
                        float x = mGraphics.onscreenXtoGameX
                            (me.getX(), mTouchViewSettings);
                        float y = mGraphics.onscreenYtoGameY
                            (me.getY(), mTouchViewSettings);
                        mGraphics.scrollBy(mTouchX - x, -(mTouchY - y));
                        notify = true;
                        mTouchX = x;
                        mTouchY = y;
                    }
                }
                // TODO: do edgeflags?

                if (action == MotionEvent.ACTION_UP) {
                    mTouchViewSettings = null;
                }
                if (notify == true) {
                    mUserInputSem.notify();
                }
            }
            return true;
        }
    }

    /*================= Members =================*/
    /** The thread that draws the animation */
    private ScorchedThread mThread;

    private Object mSurfaceHasBeenCreatedSem = new Object();

    /** True only once the Surface has been created and is ready to
     * be used */
    private boolean mSurfaceHasBeenCreated = false;

    /** Pointer to the model */
    public Model mModel = null;

    SalvoSlider.Listener mPowerAdaptor;
    SalvoSlider.Listener mAngleAdaptor;

    /*================= Accessors =================*/
    /** Fetches the animation thread for this GameControlView. */
    public ScorchedThread getThread() {
        return mThread;
    }

    /*================= User Input Operations =================*/
    /** Pan the game board */
    @Override
    public boolean onTouchEvent(MotionEvent me) {
        return mThread.onTouchEvent(me);
    }

    /**
     * Standard window-focus override. Notice focus lost so we can pause on
     * focus lost. e.g. user switches to take a call.
     */
    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        //if (!hasWindowFocus)
            //mThread.pause();
    }

    /** Callback invoked when the surface dimensions change. */
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
            int height) {
        mThread.setSurfaceSize(width, height);
    }

    /** Callback invoked when the Surface has been created and is
     * ready to be used. */
    public void surfaceCreated(SurfaceHolder holder) {
        synchronized (mSurfaceHasBeenCreatedSem) {
            // Wake up mThread.run() if it's waiting for the surface to have
            // ben created
            mSurfaceHasBeenCreated = true;
            mSurfaceHasBeenCreatedSem.notify();
        }
        Log.w(TAG, "surfaceCreated(): set mSurfaceHasBeenCreated");
    }

    /** Callback invoked when the Surface has been destroyed and must
     * no longer be touched.
     * WARNING: after this method returns, the Surface/Canvas must
     * never be touched again! */
    public void surfaceDestroyed(SurfaceHolder holder) {
        // we have to tell thread to shut down & wait for it to finish,
        // or else it might touch the Surface after we return and explode
        mThread.suicide();

        while (true) {
            try {
                mThread.join();
                break;
            }
            catch (InterruptedException e) {
            }
        }
    }

    /*================= Lifecycle =================*/
    public GameControlView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Try to get hardware acceleration
        /*try {
            getHolder().setType(
                android.view.SurfaceHolder.SURFACE_TYPE_HARDWARE);
            Log.w(TAG, "GameControlView: activated hardware acceleration");
        }
        catch(Exception e2) {
            getHolder().setType(
                android.view.SurfaceHolder.SURFACE_TYPE_NORMAL);
            Log.w(TAG, "GameControlView: no acceleration");
        }*/
    }

    public void initialize(Model model, Graphics graphics,
                            SalvoSlider powerSlider, SalvoSlider angleSlider)
    {
        mModel = model;

        // register our interest in hearing about changes to our surface
        SurfaceHolder holder = getHolder();
        holder.addCallback(this);

        // Create game controller thread
        mThread = new ScorchedThread(graphics, holder, getContext(),
            new Handler() {
                @Override
                public void handleMessage(Message m) {
                    //mStatusText.setVisibility(m.getData().getInt("viz"));
                    //mStatusText.setText(m.getData().getString("text"));
                }
            },
            powerSlider,
            angleSlider);

        mPowerAdaptor = new SalvoSlider.Listener() {
            public void onPositionChange(int val) {
                mThread.onPowerChange(val);
            }
        };
        mAngleAdaptor = new SalvoSlider.Listener() {
            public void onPositionChange(int val) {
                mThread.onAngleChange(val);
            }
        };
        setFocusable(false); // make sure we get key events

        // Start the animation thread
        mThread.start();
    }
}
