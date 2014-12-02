package cast.chrome.cribbage.cribbageforchromecast;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.*;
import android.widget.Toast;

import org.json.JSONObject;

import cast.chrome.cribbage.cribbageforchromecast.Interfaces.CastReceiver;
import cast.chrome.cribbage.cribbageforchromecast.Model.DealerCardManagement;
import cast.chrome.cribbage.cribbageforchromecast.Utils.AppPreferences;
import cast.chrome.cribbage.cribbageforchromecast.Utils.ChromecastManagement;


public class PrimaryActivity extends ActionBarActivity implements ChromecastManagement.MyTestListener {

    DealerCardManagement cardManager;
    ChromecastManagement castManager;

    private static final String TAG = PrimaryActivity.class.getSimpleName();
    private static final int REQUEST_CODE = 1;
    private static Context context;
    private static final int RESULT_SETTINGS = 1;

    int MASTER_HEIGHT, MASTER_WIDTH, PADDING_SIDE = 64, PADDING_TOP = 16, PADDING_BOT = 16, FOUR_CARD_SIZE, FIVE_CARD_SIZE, SIX_CARD_SIZE;

    //hard for now
    int myPosition;
    int handCount = 5;

    int selectedCard;

    int couldNotPlayCount = 0;

    String myName;

    TextView[] hand = new TextView[handCount];
    Button btnDropCards, btnDisplayCards, btnPlayCard, btnDeal;
    TextView txtCurrentScore;

    ProgressDialog pd;


    /**
     * Setters and getters
     */
    public String[][] getHands () { return cardManager.getPlayers(); }

    public String getPlayedCard (int playerPosition, int cardPosition) {
        return cardManager.getPlayersCardToString(playerPosition, cardPosition);
    }

    public String[] getCrib () { return cardManager.getCrib(); }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_primary);
        PrimaryActivity.context = getApplicationContext();

        CastReceiver c = new CastReceiver() {
            @Override
            public void onReceiveJSON(JSONObject jsonObject) throws Exception {

            }
        };

        castManager = new ChromecastManagement(getAppContext(), getResources().getString(R.string.app_id), TAG, c);

        //Attach listener for interface
        castManager.setMyTestListener(this);


        ActionBar actionBar = getSupportActionBar();
        actionBar.setBackgroundDrawable(new ColorDrawable(
                android.R.color.transparent));

        RelativeLayout mainLayout;
        mainLayout = new RelativeLayout(this);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        DisplayMetrics metrics = this.getResources().getDisplayMetrics();
        MASTER_HEIGHT = metrics.heightPixels;
        MASTER_WIDTH = metrics.widthPixels;

        System.out.println(MASTER_HEIGHT + " " + MASTER_WIDTH);

        btnDropCards = (Button) findViewById(R.id.btnDropCards);
        btnDisplayCards = (Button) findViewById(R.id.btnDisplayCards);
        btnPlayCard = (Button) findViewById(R.id.btnPlayCard);
        btnDeal = (Button) findViewById(R.id.btnDeal);
        txtCurrentScore = (TextView) findViewById(R.id.scoreTextView);

        btnDeal.setText("Setup Game");
        btnDeal.setOnClickListener(new  Button.OnClickListener() {
            public void onClick(View v) {
                createNewGame();
            }
        });

        //procedurally generate views later
        hand[0] = (TextView) findViewById(R.id.card1);
        hand[1] = (TextView) findViewById(R.id.card2);
        hand[2] = (TextView) findViewById(R.id.card3);
        hand[3] = (TextView) findViewById(R.id.card4);
        hand[4] = (TextView) findViewById(R.id.card5);

    }

    public static Context getAppContext() {
        return PrimaryActivity.context;
    }

    public void createNewGame() {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);

        alert.setTitle("Create game");
        alert.setMessage("Name");

        final EditText input = new EditText(this);
        alert.setView(input);alert.setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                myName = input.getText().toString();

                System.out.println(myName);

                castManager.joinGame(myName);

                btnDeal.setVisibility(View.INVISIBLE);
            }
        });

        alert.show();


    }

    /**
     * Redeal the hands and setup new game
     * @param view
     */
    public void newDeal (View view) {
        cardManager = new DealerCardManagement(3);

        myPosition = 1;

        for (int i = 0; i < 5; i++) {
            resetFullLayout();
            hand[i].setVisibility(View.INVISIBLE);
        }

        btnDisplayCards.setVisibility(View.VISIBLE);
        btnPlayCard.setVisibility(View.INVISIBLE);
        btnDropCards.setVisibility(View.INVISIBLE);
        btnDeal.setVisibility(View.INVISIBLE);

        castManager.sendHands(cardManager.getPlayers());
        castManager.sendCrib(cardManager.getCrib());
        castManager.sendCutCard(cardManager.getCutCard());
    }

    /**
     * Play currently selected card; remove from selectability and reset position
     * @param view
     */
    public void playCard (View view) {
        System.out.println(cardManager.getPlayersCardToString(myPosition, selectedCard));

        if (cardManager.canAddToActiveCards(myPosition, selectedCard)) {
            castManager.sendPegPoints(cardManager.doAddToActiveCards(myPosition, selectedCard));
            hand[selectedCard].setClickable(false);
            resetCardPosition();
            hand[selectedCard].setTextColor(Color.GRAY);
            hand[selectedCard].setSelected(true);

            btnPlayCard.setClickable(false);
            btnPlayCard.setTextColor(Color.GRAY);

            //castManager.sendPlayerPositionAndCardPositionToCast(myPosition, selectedCard, "send_card_played");
            castManager.sendCardPlayed(cardManager.getPlayersCardToString(myPosition, selectedCard), myPosition, selectedCard);

            castManager.sendIntToCast(cardManager.getCurrentScore(), "scores_during_play");

            lockCards();
        }
        else
            Toast.makeText(context, "can not drop", Toast.LENGTH_LONG);
    }

    /**
     * Drop card from players hand
     * @param view
     */
    public void dropCard (View view) {
        castManager.sendPlayerPositionAndCardPositionToCast(myPosition, selectedCard, "card_dropped");

        replaceCard(selectedCard, myPosition);
        //sendCribToCast(players[myPosition][selectedCard]);

        hand[selectedCard].setVisibility(View.INVISIBLE);
        btnDropCards.setVisibility(View.INVISIBLE);

        cardManager.setPlayState(true);

        btnPlayCard.setVisibility(View.VISIBLE);

        //btnPlayCard.setClickable(false);
        btnPlayCard.setTextColor(Color.WHITE);

        if (myPosition == 1)
            lockCards();

        txtCurrentScore.setVisibility(View.VISIBLE);
    }

    /**
     * Remove requested card from players hand
     * @param replacedCard
     * @param playerPosition
     */
    public void replaceCard (int replacedCard, int playerPosition) {

        boolean dropped = false;

        cardManager.addToCrib(cardManager.getPlayersCardToString(myPosition, replacedCard));

        cardManager.replaceCard(playerPosition, replacedCard);

        System.out.println("");
        for (int i = 0; i < 4; i++)
            System.out.println(cardManager.getPlayersCardToString(myPosition, i) + ", ");
    }


    public void lockCards () {
        for (int i = 0; i < 4; i++)
            hand[i].setClickable(false);
    }


    /**
     * Display players hand on screen
     */
    public void displayHand (View view) {

        resetCardPosition();

        drawHands();

        //Scoring.doHandScoreCheck(cardManager.players[1]);
    }

    public void drawHands() {
        for (int i = 0; i < handCount; i++) {
            hand[i].setText(cardManager.getPlayersCardToString(myPosition, i));
            hand[i].setVisibility(View.VISIBLE);
        }

        btnDisplayCards.setVisibility(View.INVISIBLE);

        btnDropCards.setVisibility(View.VISIBLE);
        btnDropCards.setClickable(false);
        btnDropCards.setTextColor(Color.GRAY);
    }

    /**
     * Establish players turn, if
     */
    public void myTurn() {
        boolean canPlay = false;

        for (int i = 0; i < 4; i++) {
            if (cardManager.canAddToActiveCards(myPosition, i)) {
                if (!hand[i].isSelected()) {
                    hand[i].setClickable(true);
                    hand[i].setTextColor(Color.RED);
                    canPlay = true;
                }
            } else {
                hand[i].setClickable(false);
                hand[i].setTextColor(Color.GRAY);
            }
        }

        if (couldNotPlayCount == 2) {
            castManager.sendPrepNewPlay();
            cardManager.resetActiveCards();
            cardManager.setCurrentScore(0);
            drawCurrentScore();
        } else if (canPlay) {
            btnPlayCard.setClickable(true);
            btnPlayCard.setTextColor(Color.BLACK);
            couldNotPlayCount = 0;
        } else {
            btnPlayCard.setClickable(false);
            btnPlayCard.setTextColor(Color.GRAY);
            castManager.sendNextPlayerTurn(couldNotPlayCount++);
            System.out.println("could not play");
        }
    }

    //cycle to selected card and mark as tagged to be played/dropped;
    public void tagSelectedCard (View view) {
        switch (view.getId()) {
            case R.id.card1:
                selectedCard = 0;
                break;
            case R.id.card2:
                selectedCard = 1;
                break;
            case R.id.card3:
                selectedCard = 2;
                break;
            case R.id.card4:
                selectedCard = 3;
                break;
            case R.id.card5:
                selectedCard = 4;
                break;
        }
    }

    /**
     * Switch card to selected state and attach to selectedCard var
     * @param view
     */
    public void selectCard (View view) {

        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) view.getLayoutParams();

        if (params.topMargin != 5) {

            //reset card position to lower any other cards, tag current as selected, then shift up
            resetCardPosition();
            tagSelectedCard(view);
            params = (RelativeLayout.LayoutParams) view.getLayoutParams();
            params.topMargin = 5;
            view.setLayoutParams(params);

            //drop button only needed prior to play; change from visibility to unclickable in future
            if (!cardManager.getPlayState()) {
                //btnDropCards.setVisibility(View.VISIBLE);
                btnDropCards.setTextColor(Color.BLACK);
                btnDropCards.setClickable(true);
            }
            else {
                //btnPlayCard.setVisibility(View.VISIBLE);
                btnPlayCard.setTextColor(Color.BLACK);
                btnPlayCard.setClickable(true);
            }
        }
        else {
            //shift card back to standard position and hide drop button
            params.topMargin = 75;
            view.setLayoutParams(params);

            btnDropCards.setTextColor(Color.GRAY);
            btnDropCards.setClickable(false);

            btnPlayCard.setTextColor(Color.GRAY);
            btnPlayCard.setClickable(false);

            //btnDropCards.setVisibility(View.INVISIBLE);
            //btnPlayCard.setVisibility(View.INVISIBLE);
        }

    }

    public void drawCurrentScore() {
        txtCurrentScore.setText("Current score :" + cardManager.getCurrentScore());
    }

    /**
     * reset all card positions back to unselected state
     */
    public void resetCardPosition () {

        RelativeLayout.LayoutParams tempParams;
        for (int i = 0; i < handCount; i++) {
            tempParams = (RelativeLayout.LayoutParams) hand[i].getLayoutParams();
            tempParams.topMargin = 75;
            hand[i].setLayoutParams(tempParams);
        }
    }

    public void resetFullLayout() {
        for (int i = 0; i < 5; i++) {
            hand[i].setSelected(false);
            hand[i].setTextColor(Color.RED);
            hand[i].setClickable(true);
        }
    }


    public void viewCreation(Bundle savedInstance)  {

    }

    @Override
    protected void onResume() {
        super.onResume();
        // Start media router discovery
        castManager.addCallback();
    }

    @Override
    protected void onPause() {
        if (isFinishing()) {
            // End media router discovery
            castManager.removeCallback();
        }
        super.onPause();
    }

    @Override
    public void onDestroy() {
        teardown();
        super.onDestroy();
    }

    @Override  // Inflate the menu; this adds items to the action bar if it is present.
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.primary, menu);
        MenuItem mediaRouteMenuItem = menu.findItem(R.id.media_route_menu_item);
        MediaRouteActionProvider mediaRouteActionProvider = (MediaRouteActionProvider) MenuItemCompat
                .getActionProvider(mediaRouteMenuItem);
        // Set the MediaRouteActionProvider selector for device discovery.
        mediaRouteActionProvider.setRouteSelector(castManager.getmMediaRouteSelector());
        //getMenuInflater().inflate(R.menu.primary, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {

            case R.id.action_settings:
                Intent i = new Intent(this, AppPreferences.class);
                startActivityForResult(i, RESULT_SETTINGS);
                break;
        }

        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case RESULT_SETTINGS:
                break;
        }
    }

    private void displayUserSettings()
    {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        String  settings = "";

        settings=settings+"Password: " + sharedPrefs.getString("prefUserPassword", "NOPASSWORD");

        settings=settings+"\nRemind For Update:"+ sharedPrefs.getBoolean("prefLockScreen", false);

        settings=settings+"\nUpdate Frequency: "
                + sharedPrefs.getString("prefUpdateFrequency", "NOUPDATE");

        //TextView textViewSetting = (TextView) findViewById(R.id.textViewSettings);

        //textViewSetting.setText(settings);
    }


    private void teardown() {
        castManager.teardown();
    }


    /**
     * Initiates new game based on hands received
     * @param playerHandsTemp
     */
    public void receiveHands(String[][] playerHandsTemp) {
        pd.dismiss();
        cardManager = new DealerCardManagement(playerHandsTemp, "5 of Clubs");
        myPosition = 0;
        drawHands();
    }

    public void receiveCardPlayed(int playerPosition, int cardPosition) {
        int j = cardManager.doAddToActiveCards(playerPosition, cardPosition);
        myTurn();
    }

    public void receiveCribCard(int playerPosition, int cardPlayed) {
        cardManager.addToCrib(cardManager.getPlayersCardToString(playerPosition, cardPlayed));
    }

    public void receievePlayerPosition (int playerPosition) {
        myPosition = playerPosition;
    }

    public void receiveCutCard (String cutCard) { cardManager.setCutCard(cutCard); }

    public void receieveNextTurn() {
        myTurn();
    }

    public void receiveScoreDuringPlay(int scoreDuringPlay) {
        cardManager.setCurrentScore(scoreDuringPlay);
        drawCurrentScore();
    }

    public void myDeal() {
        btnDeal.setText("Deal");
        btnDeal.setVisibility(View.VISIBLE);
        btnDeal.setOnClickListener(new  Button.OnClickListener() {
            public void onClick (View v) {
                newDeal(v);
            }
        });
    }

    public void prepForDeal() {
        pd = ProgressDialog.show(PrimaryActivity.this, "", "Other player is dealing. . . ");
    }

    public void receieveCouldNotPlay(int couldNotPlayCount) {
        this.couldNotPlayCount = couldNotPlayCount;

        myTurn();
    }

    public void prepNewPlay() {
        cardManager.resetActiveCards();
        cardManager.setCurrentScore(0);
        drawCurrentScore();
    }

    public void initGame() {
        btnDeal.setVisibility(View.VISIBLE);
    }
}
