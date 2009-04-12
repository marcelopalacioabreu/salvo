package scorched.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.Spinner;

public class GameSetupAct extends Activity {
    /*================= Constants =================*/

    /*================= Types =================*/

    /*================= Data =================*/
    private ModelFactory mModelFactory;

    private volatile boolean mInitialized = false;

    /*================= Operations =================*/
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
        WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.game_setup_act);

        if (savedInstanceState == null)
            mModelFactory = new ModelFactory();
        else
            mModelFactory = new ModelFactory(savedInstanceState);
    }

    /** Called when the views are ready to be displayed */
    @Override
    public void onStart() {
        super.onStart();
        if (mInitialized)
            return;
        else
            mInitialized = true;

        ////////////////// Get pointers to stuff
        final Spinner terrainSpinner =
            (Spinner)findViewById(R.id.terrain_spinner);
        final Spinner numRoundsSpinner =
            (Spinner)findViewById(R.id.num_rounds_spinner);
        final Spinner cashAmountSpinner =
            (Spinner)findViewById(R.id.starting_cash_spinner);
        final CheckBox randPlayer =
            (CheckBox)findViewById(R.id.randomize_player_positions);
        final Button choosePlayers =
            (Button)findViewById(R.id.choose_players);

        ////////////////// Initialize stuff
        ArrayAdapter< Model.TerrainType > terrainSpinnerA =
            new ArrayAdapter < Model.TerrainType >
                (getApplicationContext(),
                R.layout.game_setup_spinner_item,
                R.id.game_setup_spinner_item_text,
                Model.TerrainType.values());
        terrainSpinner.setAdapter(terrainSpinnerA);
        terrainSpinner.setOnItemSelectedListener(
            new Spinner.OnItemSelectedListener(){
                public void onItemSelected(AdapterView<?> parent,
                                    View v, int position, long id) {
                    Model.TerrainType t[] = Model.TerrainType.values();
                    Model.TerrainType ty = t[position];
                    mModelFactory.setTerrainType(ty);
                }
                public void onNothingSelected(AdapterView<?> arg0) { }
            });
        terrainSpinner.setSelection
            (mModelFactory.getDesiredTerrainType().ordinal());

        ArrayAdapter < ModelFactory.NumRounds > numRoundsA =
            new ArrayAdapter < ModelFactory.NumRounds >
                (getApplicationContext(),
                R.layout.game_setup_spinner_item,
                R.id.game_setup_spinner_item_text,
                ModelFactory.NumRounds.values());
        numRoundsSpinner.setAdapter(numRoundsA);
        numRoundsSpinner.setOnItemSelectedListener(
            new Spinner.OnItemSelectedListener(){
                public void onItemSelected(AdapterView<?> parent,
                                    View v, int position, long id) {
                    ModelFactory.NumRounds t[] =
                        ModelFactory.NumRounds.values();
                    ModelFactory.NumRounds ty = t[position];
                    mModelFactory.setNumRounds(ty.toShort());
                }
                public void onNothingSelected(AdapterView<?> arg0) { }
            });
        numRoundsSpinner.setSelection
            (ModelFactory.NumRounds.fromShort
                (mModelFactory.getNumRounds())
                    .ordinal());

        ArrayAdapter < ModelFactory.StartingCash > cashAmountSpinnerA =
            new ArrayAdapter < ModelFactory.StartingCash >
                (getApplicationContext(),
                R.layout.game_setup_spinner_item,
                R.id.game_setup_spinner_item_text,
                ModelFactory.StartingCash.values());
        cashAmountSpinner.setAdapter(cashAmountSpinnerA);
        cashAmountSpinner.setOnItemSelectedListener(
            new Spinner.OnItemSelectedListener(){
                public void onItemSelected(AdapterView<?> parent,
                                    View v, int position, long id) {
                    ModelFactory.StartingCash c[] =
                        ModelFactory.StartingCash.values();
                    ModelFactory.StartingCash cash = c[position];
                    mModelFactory.setStartingCash(cash.toShort());
                }
                public void onNothingSelected(AdapterView<?> arg0) { }
            });
        cashAmountSpinner.setSelection
            (ModelFactory.StartingCash.fromShort
                (mModelFactory.getStartingCash())
                    .ordinal());

        randPlayer.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mModelFactory.modifyRandomPlayerPlacement
                    (randPlayer.isChecked());
            }
        });
        randPlayer.setChecked(mModelFactory.getRandomPlayerPlacement());

        choosePlayers.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                final Activity thisAct = GameSetupAct.this;
                Intent myIntent =
                    new Intent().setClass(thisAct, PlayerSetupAct.class);
                startActivity(myIntent);
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle b) {
        mModelFactory.saveState(b);
    }
}
