package com.senchas.salvo;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Bundle;
import java.lang.System;
import java.util.Collection;
import java.util.Iterator;
import java.util.SortedMap;
import java.util.TreeMap;

import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import com.senchas.salvo.RunGameAct.RunGameActAccessor;
import com.senchas.salvo.SalvoSlider.Listener;
import com.senchas.salvo.WeaponType.Armory;


/** Represents a state that the game can be in.
 *
 * Purpose
 * -------
 * Each GameState represents a state that the game can be in. GameState
 * objects all implement methods to handle user input and system events.
 *
 * GameState objects also implement much of the core game logic, like
 * deciding what to do next in the game.
 *
 * Each GameState object must be able to store itself in a Bundle with
 * saveState() and unpack itself with fromBundle().
 *
 * Locking
 * -------
 * Everything that GameState objects do is done under the RunGameThread lock.
 *
 * Memory management
 * -----------------
 * It would be easiest simply to use new() to create a new GameState each
 * time we transitioned to a new state. However, we want to minimize the
 * amount of times the garbage collector runs, because it is rather slow and
 * high-latency.
 *
 * So, GameState classes are singletons, stored in static data.
 * Instead of new(), each time we enter a new GameState we call initialize()
 * to set up the relevant private data.
 *
 * In android, it sometimes happens that an Activity is destroyed, but the
 * containing Application (and associated static data) is preserved.
 * If that static data holds references to the destroyed Activity,
 * the garbage collector will never finalize it.
 *
 * So, you must never hold a reference to an Activity in a GameState's
 * private data-- or else you will create a potential memory leak.
 *
 */
public abstract class GameState {
    /*================= Constants =================*/
    private static final String GAME_STATE_ID = "GAME_STATE_ID";
    private static final String EMPTY_STRING = "";

    /*================= Types =================*/
    public static enum GameButton {
        ARMORY_LEFT,
        ARMORY_RIGHT,
        OK,
        PRESS_FIRE,
        RELEASE_FIRE
    }

    public static class DomainException extends RuntimeException {
        public static final long serialVersionUID = 1;
        public DomainException(String message) {
           super(message);
        }
    }

    /** Runnable which sets a text view to a specified string. */
    private static class SetTextView implements Runnable {
        /*================= Operations =================*/
        public void run() {
            mTextView.setText(mStr);
        }
        /*================= Data =================*/
        private TextView mTextView;
        private String mStr;

        /*================= Lifecycle =================*/
        SetTextView(TextView textView, String str) {
            mTextView = textView;
            mStr = str;
        }
    }

    /*================= Operations =================*/
    /** Pack this GameState into a Bundle.
     *
     * This method must set GAME_STATE_ID
     */
    public abstract void saveState(Bundle map);

    /** Called when we enter the state.
     *
     * Any "side effects" to entering the state should be performed here,
     * rather than in the initialization function.
     */
    public void onEnter(RunGameActAccessor game) { }

    /** The function that will be executed for this state in the main event
     *  loop.
     *
     * @return          the next state, or null if we want to stay in this
     *                  state
     */
    public abstract GameState main(RunGameActAccessor game);

    /** Called when we exit the state.
     */
    public void onExit(RunGameActAccessor game) { }

    /** Returns the minimum of time that should elapse between calls to
     *  main(). If this is 0, we just block forever waiting for user input.
      */
    public abstract int getBlockingDelay();

    /** Called when the user presses a button
     *
     * @return  true if the main thread needs to be notified of a change
     */
    public boolean onButton(RunGameActAccessor game, GameButton b) {
        return false;
    }

    /** Handles a touchscreen event in the GameControlView part of the screen
     *
     * @return  true if the main thread needs to be notified of a change
     */
    public boolean onTouchEvent(RunGameActAccessor game, MotionEvent event) {
        return false;
    }

    /*================= Game States =================*/
    //         +-------------------------+        no more rounds left
    //         | LeaderboardState        |--------------> game over
    //  force  |                         |
    //  draw   | show the leaderboard    |<---------+
    //  -----> |                         |          |
    //         +-------------------------+          |
    //                     | next round button      |
    //                     V                        |
    //         +------------------------------+     |
    //  new    | BuyWeaponsState              |     |
    //  game   |                              |     |
    //  -----> | allow humans to buy          |     |
    //         | weapons (1 at a time)        |     |
    //         | (comps buy too, silently)    |     |
    //         +------------------------------+     |
    //                     |                        |
    //                     V                        |
    //         +------------------------------+     |
    //         | TurnStartState               |     |
    //         |                              |     | No more valid players
    //   +---->| find next valid player       |-----+
    //   |     | (if any)                     |
    //   |  +->|                              |-----+
    //   |  |  +------------------------------+     |
    //   |  |              | next player is human   | next player is AI
    //   |  |              V                        V
    //   |  |  +------------------------------+  +------------------------+
    //   |  |  | HumanMoveState               |  | ComputerMoveState      |
    //   |  |  |                              |  |                        |
    //   |  |  | allow human to move his tank |  | control comes from     |
    //   |  +--| accept input from buttons +  |  | AI                     |
    //   | give| slider                       |  |                        |
    //   |  up +------------------------------+  +------------------------+
    //   |                 |                        |
    //   |                 V                        |
    //   |     +------------------------------+     |
    //   |     | BallisticsState              |     |
    //   |     |                              |<----+
    //   |     | display missles flying       |
    //   |     | through the air, draw        |
    //   |     | explosions                   |
    //   |     |                              |
    //   |     +------------------------------+
    //   |                 |
    //   +-----------------+
    //

    ///** Displays the leaderboard */
    public static class LeaderboardState extends GameState {
        /*================= Constants =================*/
        public static final byte ID = 0;

        /*================= Static =================*/
        private static LeaderboardState sMe = new LeaderboardState();

        /*================= Data =================*/
        private boolean mFinished;

        /*================= Operations =================*/
        @Override
        public void saveState(Bundle map) {
            map.putByte(GAME_STATE_ID, ID);
        }

        @Override
        public void onEnter(RunGameActAccessor game) {
            // TODO: give a reward to the 'surviving' player
            // use: int newPlayer = model.getCurPlayerId();, etc.

            // TODO: set leaderboard layout...
            // TODO: set leaderboard menus...
        }

        @Override
        public GameState main(RunGameActAccessor game) {
            Util.DoToast doToast = new Util.DoToast(
                game.getGameControlView().getContext(),
                "LeaderboardState!");
            game.getRunGameAct().runOnUiThread(doToast);
            return TurnStartState.create();

            /*
            if (mFinished) {
                return BuyWeaponsState.create();
            }
            else {
                return null;
            }
            */
        }

        @Override
        public int getBlockingDelay() {
            return 0;
        }

        @Override
        public boolean onButton(RunGameActAccessor game, GameButton b) {
            if (b == GameButton.OK) {
                mFinished = true;
                return true;
            }
            else {
                return false;
            }
        }

        /*================= Lifecycle =================*/
        private void initialize() {
            mFinished = false;
        }

        public static LeaderboardState create() {
            sMe.initialize();
            return sMe;
        }

        public static LeaderboardState createFromBundle(Bundle map) {
            sMe.initialize();
            return sMe;
        }

        private LeaderboardState() { }
    }

    /** Allows the user to buy weapons */
    public static class BuyWeaponsState extends GameState {
        /*================= Constants =================*/
        public static final byte ID = 5;

        /*================= Static =================*/
        private static BuyWeaponsState sMe = new BuyWeaponsState();

        /*================= Data =================*/
        private boolean mFinished;

        /*================= Operations =================*/
        @Override
        public void saveState(Bundle map) {
            map.putByte(GAME_STATE_ID, ID);
        }

        @Override
        public void onEnter(RunGameActAccessor game) {
            mFinished = false;
            // TODO: implement weapons layout...
            // TODO: implement weapons menus...
        }

        @Override
        public GameState main(RunGameActAccessor game) {
            if (mFinished) {
                return BuyWeaponsState.create();
            }
            else {
                return null;
            }
        }

        @Override
        public void onExit(RunGameActAccessor game) {
            // TODO: implement game layout
            // TODO: implement game menus
        }

        @Override
        public int getBlockingDelay() {
            return 0;
        }

        @Override
        public boolean onButton(RunGameActAccessor game, GameButton b) {
            if (b == GameButton.OK) {
                mFinished = true;
                return true;
            }
            else {
                return false;
            }
        }

        /*================= Lifecycle =================*/
        private void initialize() {
            mFinished = false;
        }

        public static BuyWeaponsState create() {
            sMe.initialize();
            return sMe;
        }

        public static BuyWeaponsState createFromBundle(Bundle map) {
            sMe.initialize();
            return sMe;
        }

        private BuyWeaponsState() { }
    }

    /** The start of a turn. */
    public static class TurnStartState extends GameState {
        /*================= Constants =================*/
        public static final byte ID = 10;

        /*================= Static =================*/
        private static TurnStartState sMe = new TurnStartState();

        /*================= Data =================*/
        private Model.NextTurnInfo mInfo;

        /*================= Operations =================*/
        @Override
        public void saveState(Bundle map) {
            map.putByte(GAME_STATE_ID, ID);
        }

        @Override
        public void onEnter(RunGameActAccessor game) {
            game.getModel().getNextPlayerInfo(mInfo);
            // TODO: set location of floating arrow thing to current player
        }

        @Override
        public GameState main(RunGameActAccessor game) {
            if (mInfo.isDraw()) {
                // TODO: display "it was a draw!" or similar
                return LeaderboardState.create();
            }
            else if (mInfo.curPlayerHasWon()) {
                // Someone won the round.
                // TODO: display "foo wins" or similar
                // TODO: add gold to account, or whatever
                return LeaderboardState.create();
            }
            else {
                Model model = game.getModel();
                int nextPlayerId = mInfo.getNextPlayerId();
                Player play = model.getPlayers()[nextPlayerId];
                Util.DoToast doToast = new Util.DoToast(
                    game.getGameControlView().getContext(),
                    play.getIntroductionString());
                game.getRunGameAct().runOnUiThread(doToast);

                model.setCurPlayerId(mInfo.getNextPlayerId());
                return play.getBrain().getMoveState();
            }
        }

        @Override
        public int getBlockingDelay() {
            throw new RuntimeException("unreachable");
        }

        /*================= Lifecycle =================*/
        private void initialize() {
        }

        public static TurnStartState create() {
            sMe.initialize();
            return sMe;
        }

        public static TurnStartState createFromBundle(Bundle map) {
            sMe.initialize();
            return sMe;
        }

        private TurnStartState() {
            mInfo = new Model.NextTurnInfo();
        }
    }

    /** A human turn. We will accept input from the touchscreen and do all
     * that stuff. */
    public static class HumanMoveState extends GameState {
        /*================= Constants =================*/
        public static final byte ID = 15;

        private static final long MAX_FIRE_TIME = 2400;

        /*================= Static =================*/
        private static HumanMoveState sMe = new HumanMoveState();

        /*================= Data =================*/
        /** True only if the user pressed the fire button */
        private boolean mFiring;

        /** The time at which the user pressed the fire button */
        private long mFireTime;

        /** The time at which the user released the fire button, or 0 if
         * the user has not yet released the fire button. */
        private long mFireReleaseTime;

        /*================= Operations =================*/
        @Override
        public void saveState(Bundle map) {
            map.putByte(GAME_STATE_ID, ID);
        }

        @Override
        public void onEnter(RunGameActAccessor game) {
            GameState.setCurPlayerAngleText(game);
            game.getGameControlView().cacheTerrain(game);
            GameState.setCurPlayerArmoryText(game);
        }

        /** Calculate what the power should be, given the current time, and
         * the time that the user pressed the fire button.
         */
        private int timeToPower(long time) {
            long diff = time - mFireTime;
            if (diff > MAX_FIRE_TIME)
                return Player.MAX_POWER;
            else {
                return (int)((diff * Player.MAX_POWER) / MAX_FIRE_TIME);
            }
        }

        @Override
        public GameState main(RunGameActAccessor game) {
            int power = 0;
            if (mFireTime == 0) {
                game.getGameControlView().
                    drawScreen(game, Player.INVALID_POWER, null, null);
            }
            else {
                power = timeToPower(System.currentTimeMillis());
                game.getGameControlView().drawScreen(game, power, null, null);
                if (power == Player.MAX_POWER)
                    doReleaseFire(game);
            }
            if (mFireReleaseTime == 0) {
                // The user hasn't released the fire button yet. Continue in
                // this state.
                return null;
            }
            else {
                // The user released the fire button
                Player curPlayer = game.getModel().getCurPlayer();
                WeaponType weapon = curPlayer.getCurWeaponType();
                curPlayer.setCurWeaponType(
                    curPlayer.getArmory().useWeapon(weapon));
                return BallisticsState.create(power, weapon);
            }
        }

        @Override
        public void onExit(RunGameActAccessor game) {
            // TODO: grey out buttons and whatnot
            GameState.setCustomAngleText(game, EMPTY_STRING);
            GameState.clearCurPlayerArmoryText(game);
        }

        @Override
        public int getBlockingDelay() {
            if (mFireTime == 0) {
                // If the user hasn't pressed the fire button yet, block
                // until we get some input.
                return 0;
            }
            else {
                // If the user has already pressed the fire button, update
                // every 1 ms or so
                return 1;
            }
        }

        class DoShowArmory implements Runnable {
            private RunGameActAccessor mGame;
            public void run() {
                showArmory(mGame);
            }
            DoShowArmory(RunGameActAccessor game) {
                mGame = game;
            }
        }

        private void doReleaseFire(RunGameActAccessor game) {
            mFireReleaseTime = System.currentTimeMillis();
            game.getRunGameAct().runOnUiThread(new DoShowArmory(game));
        }

        @Override
        public boolean onButton(RunGameActAccessor game, GameButton b) {
            switch (b) {
                case ARMORY_LEFT: {
                    Player curPlayer = game.getModel().getCurPlayer();
                    curPlayer.setCurWeaponType(
                        curPlayer.getArmory().getPrevWeapon(
                            curPlayer.getCurWeaponType()));
                    GameState.setCurPlayerArmoryText(game);
                    return true;
                }
                case ARMORY_RIGHT: {
                    Player curPlayer = game.getModel().getCurPlayer();
                    curPlayer.setCurWeaponType(
                        curPlayer.getArmory().getNextWeapon(
                            curPlayer.getCurWeaponType()));
                    GameState.setCurPlayerArmoryText(game);
                    return true;
                }
                case PRESS_FIRE:
                    hideArmory(game);
                    mFireTime = System.currentTimeMillis();
                    return true;
                case RELEASE_FIRE:
                    doReleaseFire(game);
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public boolean onTouchEvent(
                RunGameActAccessor game, MotionEvent event) {

            Player curPlayer = game.getModel().getCurPlayer();
            int startAngle = curPlayer.getAngleDeg();
            int finishAngle = startAngle;

            int act = event.getAction();
            if ((act == MotionEvent.ACTION_DOWN) ||
                (act == MotionEvent.ACTION_MOVE) ||
                (act == MotionEvent.ACTION_UP))
            {
                float x = event.getX();
                float y = event.getY();

                float tx = curPlayer.getX();
                float ty = curPlayer.getTurretCenterY();

                float y_diff = ty - y;
                if (y_diff <= 0) {
                    if (x >= tx)
                        finishAngle = Player.MIN_TURRET_ANGLE;
                    else
                        finishAngle = Player.MAX_TURRET_ANGLE;
                }
                else {
                    float x_diff = tx - x;
                    float angleRad = (float)Math.atan2(y_diff, x_diff);
                    int angleDeg = (int)Math.toDegrees(angleRad);
                    finishAngle = Player.MAX_TURRET_ANGLE - angleDeg;
                }
            }
            if (finishAngle != startAngle) {
                curPlayer.setAngleDeg(finishAngle);
                GameState.setCurPlayerAngleText(game);
                return true;
            }
            else
                return false;
        }

        /*================= Lifecycle =================*/
        private void initialize() {
            mFireTime = 0;
            mFireReleaseTime = 0;
        }

        public static HumanMoveState create() {
            sMe.initialize();
            return sMe;
        }

        public static HumanMoveState createFromBundle(Bundle map) {
            sMe.initialize();
            return sMe;
        }

        public HumanMoveState() {
        }
    }

    /** A computer turn. We use the Brain object to determine what to do.
     * This code is mostly concerned with animating the action.
     */
    public static class ComputerMoveState extends GameState {
        /*================= Constants =================*/
        public static final byte ID = 16;

        /*================= Static =================*/
        private static ComputerMoveState sMe = new ComputerMoveState();

        /*================= Data =================*/

        /*================= Operations =================*/
        @Override
        public void saveState(Bundle map) {
            map.putByte(GAME_STATE_ID, ID);
        }

        @Override
        public void onEnter(RunGameActAccessor game) {
            GameState.setCurPlayerAngleText(game);
            game.getGameControlView().cacheTerrain(game);
            GameState.setCurPlayerArmoryText(game);
        }

        @Override
        public GameState main(RunGameActAccessor game) {
                //return BallisticsState.create(power, weapon);
            return null;
        }

        @Override
        public void onExit(RunGameActAccessor game) {
            // TODO: grey out buttons and whatnot
            GameState.setCustomAngleText(game, EMPTY_STRING);
            GameState.clearCurPlayerArmoryText(game);
        }

        @Override
        public int getBlockingDelay() {
            return 1;
        }

        /*================= Lifecycle =================*/
        private void initialize() {
        }

        public static ComputerMoveState create() {
            return ComputerMoveState.create();
            //sMe.initialize();
            //return sMe;
        }

        public static ComputerMoveState createFromBundle(Bundle map) {
            sMe.initialize();
            return sMe;
        }

        public ComputerMoveState() {
        }
    }

    /** Draw missiles flying through the sky. The fun state. */
    public static class BallisticsState extends GameState {
        /*================= Constants =================*/
        public static final byte ID = 20;
        public static final String BALLISTICS_POWER = "BALLISTICS_POWER";
        public static final String WEAPON_TYPE = "WEAPON_TYPE";

        /*================= Types =================*/

        /*================= Static =================*/
        private static BallisticsState sMe = new BallisticsState();

        /*================= Data =================*/
        private int mPower;
        private Projectile mProjectile;
        private Explosion mExplosion;
        private WeaponType mWeapon;

        /*================= Access =================*/

        /*================= Operations =================*/
        @Override
        public void saveState(Bundle map) {
            map.putByte(GAME_STATE_ID, ID);
            map.putInt(BALLISTICS_POWER, mPower);
            map.putInt(WEAPON_TYPE, mWeapon.ordinal());
        }

        @Override
        public void onEnter(RunGameActAccessor game) {
            Model model = game.getModel();
            Player curPlayer = model.getCurPlayer();

            float angle = curPlayer.getAngleRad();
            float cos = (float)Math.cos(angle);
            float sin = - (float)Math.sin(angle);
            float dx = (cos * mPower) / 120f;
            float dy = (sin * mPower) / 120f;
            float turretX = curPlayer.getX() + (Player.TURRET_LENGTH * cos);
            float turretY = curPlayer.getTurretCenterY()
                    + (Player.TURRET_LENGTH * sin);
            mProjectile.initialize(turretX, turretY,
                                   dx, dy, model.getWind());

            game.getGameControlView().cacheTerrain(game);
        }

        @Override
        public GameState main(RunGameActAccessor game) {
            boolean finished = true;
            final Model model = game.getModel();

            if (mProjectile.getInUse()) {
                finished = false;
                mProjectile.step();
                if (mProjectile.hasExploded(game.getModel())) {
                    mProjectile.changeInUse(false);
                    mExplosion.initialize(
                        mProjectile.getCurX(), mProjectile.getCurY(),
                        mWeapon);
                }
            }
            if (mExplosion.getInUse()) {
                finished = false;
                if (mExplosion.getFinished(System.currentTimeMillis())) {
                    mExplosion.clearInUse();
                    mExplosion.doDirectDamage(game);
                    mExplosion.editTerrain(game);
                    Terrain terrain = model.getTerrain();
                    for (Player p : model.getPlayers()) {
                        p.doFalling(terrain);
                    }
                    // TODO: potentially start other explosions here as
                    // players die
                }
            }

            game.getGameControlView().
                drawScreen(game, Player.INVALID_POWER,
                           mProjectile, mExplosion);

            if (finished)
                return TurnStartState.create();
            else
                return null;
        }

        @Override
        public int getBlockingDelay() {
            return 1;
        }

        /*================= Lifecycle =================*/
        private void initialize(int power, WeaponType weapon) {
            mPower = power;
            mProjectile.changeInUse(false);
            mExplosion.clearInUse();
            mWeapon = weapon;
        }

        public static BallisticsState create(int power, WeaponType weapon) {
            sMe.initialize(power, weapon);
            return sMe;
        }

        public static BallisticsState createFromBundle(Bundle map) {
            int power = map.getInt(BALLISTICS_POWER);
            int wType = map.getInt(WEAPON_TYPE);
            WeaponType weapons[] = WeaponType.values();
            WeaponType weapon = weapons[wType];
            sMe.initialize(power, weapon);
            return sMe;
        }

        private BallisticsState() {
            mProjectile = new Projectile();
            mExplosion = new Explosion();
        }
    }

    /*================= Static =================*/
    private static void setCurPlayerArmoryText(RunGameActAccessor game) {
        Player curPlayer = game.getModel().getCurPlayer();
        WeaponType type = curPlayer.getCurWeaponType();
        Integer amount = curPlayer.getArmory().getMap().get(type);

        TextView armoryMain = game.getArmoryMainText();
        TextView armorySecondary = game.getArmorySecondaryText();

        game.getRunGameAct().runOnUiThread(new SetTextView(armoryMain,
                                                           type.getName()));
        StringBuilder b = new StringBuilder(14);
        b.append("[");
        if (amount.intValue() == WeaponType.UNLIMITED)
            b.append("∞");
        else
            b.append(amount);
        b.append("]");
        game.getRunGameAct().runOnUiThread(new SetTextView(armorySecondary,
                                                           b.toString()));
    }

    private static void clearCurPlayerArmoryText(RunGameActAccessor game) {
        TextView armoryMain = game.getArmoryMainText();
        TextView armorySecondary = game.getArmorySecondaryText();

        game.getRunGameAct().runOnUiThread(
            new SetTextView(armoryMain, EMPTY_STRING));
        game.getRunGameAct().runOnUiThread(
            new SetTextView(armorySecondary, EMPTY_STRING));
    }

    /** Sets the current angle text to the turret angle of the current
     * player.
     */
    private static void setCurPlayerAngleText(RunGameActAccessor game) {
        Player curPlayer = game.getModel().getCurPlayer();
        TextView angleText = game.getAngleText();
        StringBuilder b = new StringBuilder(10);
        b.append(curPlayer.getAngleDeg()).append("°");
        game.getRunGameAct().runOnUiThread(new SetTextView(angleText,
                                                           b.toString()));
    }

    /** Sets the current angle text to a custom string */
    private static void setCustomAngleText(RunGameActAccessor game,
                                           String text) {
        Player curPlayer = game.getModel().getCurPlayer();
        TextView angleText = game.getAngleText();
        game.getRunGameAct().runOnUiThread(new SetTextView(angleText, text));
    }

    /** Hides the armory in the middle of the screen */
    private static void hideArmory(RunGameActAccessor game) {
        game.getAngleText().setVisibility(View.INVISIBLE);
        game.getArmoryMainText().setVisibility(View.INVISIBLE);
        game.getArmorySecondaryText().setVisibility(View.INVISIBLE);
        game.getArmoryLeftButton().setVisibility(View.INVISIBLE);
        game.getArmoryRightButton().setVisibility(View.INVISIBLE);
        int color = game.getRunGameAct().getResources().
                        getColor(R.drawable.clear);
        game.getArmoryCenter().setBackgroundColor(color);
    }

    /** Un-hides the armory in the middle of the screen */
    private static void showArmory(RunGameActAccessor game) {
        game.getAngleText().setVisibility(View.VISIBLE);
        game.getArmoryMainText().setVisibility(View.VISIBLE);
        game.getArmorySecondaryText().setVisibility(View.VISIBLE);
        game.getArmoryLeftButton().setVisibility(View.VISIBLE);
        game.getArmoryRightButton().setVisibility(View.VISIBLE);
        int color = game.getRunGameAct().getResources().
                        getColor(R.drawable.armory_bg_color);
        game.getArmoryCenter().setBackgroundColor(color);
    }

    /** Initialize and return a game state object from a Bundle */
    public static GameState fromBundle(Bundle map) {
        byte id = map.getByte(GAME_STATE_ID);
        switch (id) {
            case LeaderboardState.ID:
                return LeaderboardState.createFromBundle(map);
            case BuyWeaponsState.ID:
                return BuyWeaponsState.createFromBundle(map);
            case TurnStartState.ID:
                return TurnStartState.createFromBundle(map);
            case HumanMoveState.ID:
                return HumanMoveState.createFromBundle(map);
            case ComputerMoveState.ID:
                return ComputerMoveState.createFromBundle(map);
            case BallisticsState.ID:
                return BallisticsState.createFromBundle(map);
            default:
                throw new RuntimeException("can't recognize state with ID = "
                                            + id);
        }
    }

    public static GameState createInitialGameState() {
        return TurnStartState.create();
    }
}