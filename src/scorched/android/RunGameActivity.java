package scorched.android;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ZoomButton;

import scorched.android.Model;
import android.view.View.OnClickListener;

public class RunGameActivity extends Activity {
    /*================= Constants =================*/
    private static final String TAG = "RunGameActivity";

    /*================= Data =================*/
    private GameControlView mGameControl;
    private Model mModel;

    /*================= Utility =================*/

    /*================= Operations =================*/
    /**
     * Invoked when the Activity is created.
     *
     * @param savedInstanceState a Bundle containing state saved from
     *        a previous execution, or null if this is a new execution
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Player players[] = new Player[5];
        players[0] = new LocalHumanPlayer(0);
        players[1] = new ComputerPlayer(1);
        players[2] = new ComputerPlayer(2);
        players[3] = new ComputerPlayer(3);
        players[4] = new LocalHumanPlayer(4);
        mModel = new Model(players);
        Graphics.instance.initialize(getBaseContext());

        ////////////////// setContentView
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        Log.w(TAG, "setContentView");
        setContentView(R.layout.game);

        ////////////////// Get pointers to stuff
        mGameControl = (GameControlView)findViewById(R.id.game_control_view);
        final SalvoSlider powerSlider =
            (SalvoSlider)findViewById(R.id.PowerSlider);
        final SalvoSlider angleSlider =
            (SalvoSlider)findViewById(R.id.AngleSlider);
        final Button fireButton = (Button)findViewById(R.id.FireButton);
        final ZoomButton zoomIn = (ZoomButton)findViewById(R.id.ZoomIn);
        final ZoomButton zoomOut = (ZoomButton)findViewById(R.id.ZoomOut);

        ////////////////// Initialize stuff
        mGameControl.initialize(mModel, powerSlider, angleSlider);
        fireButton.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                mGameControl.getThread().onButton(GameState.GameButton.FIRE);
            }
        });
        zoomIn.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                mGameControl.getThread().onZoomIn();
            }
        });
        zoomOut.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                mGameControl.getThread().onZoomOut();
            }
        });
    }

    /**
     * Invoked when the Activity loses user focus.
     */
    @Override
    protected void onPause() {
        super.onPause();
        //mGameControl.getThread().pause(); // pause game when Activity pauses
    }

    /**
     * Notification that something is about to happen, to give the Activity a
     * chance to save state.
     *
     * @param outState a Bundle into which this Activity should save its state
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // just have the View's thread save its state into our Bundle
        super.onSaveInstanceState(outState);
        mGameControl.getThread().saveState(outState);
        Log.w(this.getClass().getName(), "onSaveInstanceState called");
    }
}