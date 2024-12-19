package com.example.blackjackgameclient;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;
import org.parceler.Parcels;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public class GameRoomActivity extends AppCompatActivity {

    private final HashSet<User> players = new HashSet<>();
    private LinearLayout rivalCardLayout;
    private LinearLayout selfCardLayout;
    private TextView statusTextView;
    private TextView selfNameTextView;
    private TextView rivalNameTextView;
    private LinearLayout actionButtons;
    private TextView scoreBoardTextView;
    private Button hitButton, standButton;
    private String currentUsername;
    private int selfWin;
    private int selfGame;
    private String rivalName;
    private int rivalWin;
    private int rivalGame;
    private boolean isGameStarted = false;

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;

    private AlertDialog countdownPopup;
    private AlertDialog gameOverPopup;
    private int selfScore = 0;
    private int rivalScore = 0;
    private final MessageHandler handleMessage = new MessageHandler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_room);

        // Setup listener
        MessageDispatcher.getInstance().registerListener("GameRoomActivity", handleMessage);

        // Setup ui elements
        scoreBoardTextView = findViewById(R.id.scoreBoardTextView);
        rivalCardLayout = findViewById(R.id.rivalCardLayout);
        TextView roomNameTextView = findViewById(R.id.roomNameTextView);
        selfCardLayout = findViewById(R.id.selfCardLayout);
        statusTextView = findViewById(R.id.statusTextView);
        actionButtons = findViewById(R.id.actionButtons);
        hitButton = findViewById(R.id.hitButton);
        standButton = findViewById(R.id.standButton);

        roomNameTextView.setText("Room " + getIntent().getIntExtra("roomName", 0));

        hitButton.setOnClickListener(v -> sendAction(true));
        standButton.setOnClickListener(v -> sendAction(false));

        selfNameTextView = findViewById(R.id.selfNameTextView);
        rivalNameTextView = findViewById(R.id.rivalNameTextView);

        Button leaveButton = findViewById(R.id.leaveButton);
        leaveButton.setOnClickListener(v -> leaveGame());

        // Read user's details from previous activity
        User user = Parcels.unwrap(getIntent().getParcelableExtra("user"));
        currentUsername = user.username;
        selfWin = user.winCount;
        selfGame = user.gameCount;
        // Setup username and win count
        selfNameTextView.setText(currentUsername + " " + selfWin + "/" + selfGame);

        // Read rival's details and setup username and win rate details
        List<User> parcelableArrayList = getIntent().getBundleExtra("users").getParcelableArrayList("users").stream().map(parcel -> (User) Parcels.unwrap(parcel)).collect(Collectors.toList());
        players.addAll(parcelableArrayList);

        for (User player : players) {
            if (!player.username.equals(currentUsername)) {
                statusTextView.setText("Players in room: " + players.size());
                rivalName = player.username;
                rivalWin = player.winCount;
                rivalGame = player.gameCount;
                rivalNameTextView.setText(rivalName + " " + rivalWin + "/" + rivalGame);
            }
        }

        // Initialize socket and start listening on message dispatcher.
        socket = SocketManager.getSocket();
        MessageDispatcher.getInstance().startListening(socket);

        // When room is full start a countdown and wait for game start message from game server.
        if (players.size() >= 2) {
            showCountdownPopup();
        }

        // Show 2 cards backs.
        addPlaceholderCards();
    }

    // When another player joins to room add them to player list, update rival's name and win game counts.
    // Then reset score board and if the room is full show game starting countdown popup and wait for game start message from game server.
    private void handlePlayerJoin(User user) {
        players.add(user);
        statusTextView.setText("Players in room: " + players.size());
        rivalName = user.username;
        rivalNameTextView.setText(user.username + " " + user.winCount + "/" + user.gameCount);
        selfScore = 0;
        rivalScore = 0;
        scoreBoardTextView.setText("Score: " + selfScore + " - " + rivalScore);

        if (players.size() >= 2 && !isGameStarted) {
            showCountdownPopup();
        }
    }

    private void startGame(JSONObject gameStartMessage) {
        // Set room status to game started.
        isGameStarted = true;

        // If countdown popup is still here destroy it.
        if (countdownPopup != null && countdownPopup.isShowing()) {
            countdownPopup.dismiss();
        }

        // Destroy game over popup.
        if (gameOverPopup != null && gameOverPopup.isShowing()) {
            gameOverPopup.dismiss();
        }

        try {
            // Set the initial cards for both users. And set current users hands points.
            int selfOpenCard = gameStartMessage.getInt("selfOpenCard");
            int selfCloseCard = gameStartMessage.getInt("selfCloseCard");
            int rivalOpenCard = gameStartMessage.getInt("rivalOpenCard");
            int selfPoints = gameStartMessage.getInt("selfPoints");
            selfNameTextView.setText(currentUsername + " " + selfWin + "/" + selfGame + ": " + selfPoints + " Points");
            rivalNameTextView.setText(rivalName + " " + rivalWin + "/" + rivalGame);

            selfCardLayout.removeAllViews();
            addCardToLayout(selfCardLayout, selfOpenCard);
            addCardToLayout(selfCardLayout, selfCloseCard);

            rivalCardLayout.removeAllViews();
            addCardToLayout(rivalCardLayout, rivalOpenCard);
            addCardToLayout(rivalCardLayout, -1);
        } catch (Exception e) {
            Log.e("GameRoomActivity", "Error starting game", e);
        }
    }

    private void handleTurnChange(boolean yourTurn) {
        // If it is your turn show hit and stand buttons, and notify user. And vice versa.
        if (yourTurn) {
            actionButtons.setVisibility(View.VISIBLE);
            hitButton.setEnabled(true);
            standButton.setEnabled(true);
            Toast.makeText(this, "Your turn! Choose Hit or Stand.", Toast.LENGTH_SHORT).show();
        } else {
            actionButtons.setVisibility(View.VISIBLE);
            hitButton.setEnabled(false);
            standButton.setEnabled(false);
            Toast.makeText(this, "Opponent's turn. Please wait...", Toast.LENGTH_SHORT).show();
        }
    }

    // When a player hits or stands send message to server.
    private void sendAction(boolean isHit) {
        new Thread(() -> {
            try {
                JSONObject actionMessage = new JSONObject();
                actionMessage.put("code", 4);
                actionMessage.put("isHit", isHit);
                writer = new PrintWriter(socket.getOutputStream(), true);
                writer.println(actionMessage);
            } catch (Exception e) {
                Log.e("GameRoomActivity", "Error sending action", e);
            }
        }).start();
    }

    private void handleHitResult(JSONObject message) {
        // When a player hits for another card, check if the player is self or rival.
        // If the self played, update the cards and the value of hand and display it.
        // If the rival played, just update rival's cards.
        try {
            String user = message.getString("user");
            int card = message.getInt("card");
            int totalValue = message.getInt("totalValue");

            if (user.equals(currentUsername)) {
                addCardToLayout(selfCardLayout, card);
                selfNameTextView.setText(currentUsername + " " + selfWin + "/" + selfGame + ": " + totalValue + " Points");
            } else {
                addCardToLayout(rivalCardLayout, card);
            }
        } catch (Exception e) {
            Log.e("GameRoomActivity", "Error handling hit result", e);
        }
    }

    private void handleGameEnd(JSONObject message) {
        try {
            // Read game end parameters
            String winner = message.getString("winner");
            boolean bust = message.getBoolean("bust");
            int selfPoints = message.getInt("selfPoints");
            int rivalPoints = message.getInt("rivalPoints");
            JSONArray selfHandJsonArray = message.getJSONArray("selfHand");
            JSONArray rivalHandJsonArray = message.getJSONArray("rivalHand");

            // Update points for final result.
            selfNameTextView.setText(currentUsername + " " + selfWin + "/" + selfGame + ": " + selfPoints + " Points");
            rivalNameTextView.setText(rivalName + " " + rivalWin + "/" + rivalGame + ": " + rivalPoints + "Points");
            // Reveal all cards of rival and self
            selfCardLayout.removeAllViews();
            for (int i = 0; i < selfHandJsonArray.length(); i++) {
                addCardToLayout(selfCardLayout, selfHandJsonArray.getInt(i));
            }
            rivalCardLayout.removeAllViews();
            for (int i = 0; i < rivalHandJsonArray.length(); i++) {
                addCardToLayout(rivalCardLayout, rivalHandJsonArray.getInt(i));
            }

            if (winner.equals(currentUsername)) {
                // If the self won, show win popup.
                // Then update scoreboard, win and game count for self and game count for rival.
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("You won").setMessage("The game will start soon");
                gameOverPopup = builder.create();
                gameOverPopup.show();
                selfScore++;
                selfWin++;
                selfGame++;
                rivalGame++;
            } else {
                // If the rival won, show lose popup.
                // Then update scoreboard, win and game count for rival and game count for self.
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("You lost").setMessage("The game will start soon");
                gameOverPopup = builder.create();
                gameOverPopup.show();
                rivalScore++;
                selfGame++;
                rivalWin++;
                rivalGame++;
            }

            // Display updated scoreboard
            scoreBoardTextView.setText("Score: " + selfScore + " - " + rivalScore);

            // Disable all buttons and wait for game start.
            actionButtons.setVisibility(View.GONE);
            hitButton.setEnabled(false);
            standButton.setEnabled(false);
        } catch (Exception e) {
            Log.e("GameRoomActivity", "Error handling game end", e);
        }
    }

    // When self left the game and message from game server arrives, go back to lobby.
    // Pass the self details back to lobby to display them correctly.
    private void handleLeaveGame(JSONObject jsonResponse) {
        try {
            Intent intent = new Intent(GameRoomActivity.this, LobbyActivity.class);
            JSONObject user = jsonResponse.getJSONObject("user");
            intent.putExtra("username", user.getString("username"));
            intent.putExtra("winCount", user.getInt("winCount"));
            intent.putExtra("gameCount", user.getInt("gameCount"));
            runOnUiThread(() -> {
                Toast.makeText(this, "You left the game.", Toast.LENGTH_SHORT).show();

                startActivity(intent);
                finish();
            });
        } catch (Exception e) {
            Log.d("GameRoomActivity", "Error handling leave game");
        }
    }

    // When self clicks leave button send leave message to server. And wait for leave message
    private void leaveGame() {
        new Thread(() -> {
            try {
                JSONObject leaveMessage = new JSONObject();
                leaveMessage.put("code", 5);
                writer = new PrintWriter(socket.getOutputStream(), true);
                writer.println(leaveMessage);
            } catch (Exception e) {
                Log.e("GameRoomActivity", "Error sending leave message", e);
            }
        }).start();
    }

    // When rival leaves, notify player and reset the state of the game room.
    private void handlePlayerLeft() {
        Toast.makeText(this, "Player left the game. You won!", Toast.LENGTH_SHORT).show();
        resetForNewPlayer();
    }

    private void resetForNewPlayer() {
        rivalCardLayout.removeAllViews();
        rivalNameTextView.setText("Rival: Waiting...");
        statusTextView.setText("Waiting for a new player to join...");
        actionButtons.setVisibility(View.GONE);
    }

    private void addPlaceholderCards() {
        addCardToLayout(selfCardLayout, -1);
        addCardToLayout(selfCardLayout, -1);
        addCardToLayout(rivalCardLayout, -1);
        addCardToLayout(rivalCardLayout, -1);
    }

    // Add card to layout of self or rival
    private void addCardToLayout(LinearLayout layout, int cardValue) {
        ImageView cardView = new ImageView(this);
        cardView.setLayoutParams(new LinearLayout.LayoutParams(80, 120));
        cardView.setImageResource(getCardDrawable(cardValue));
        layout.addView(cardView);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        MessageDispatcher.getInstance().unregisterListener("GameRoomActivity");
    }

    private void showCountdownPopup() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Game Starting Soon").setMessage("The game will start in 5 seconds...");

        countdownPopup = builder.create();
        countdownPopup.show();

        new CountDownTimer(5000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                countdownPopup.setMessage("The game will start in: " + (millisUntilFinished / 1000) + " seconds");
            }

            @Override
            public void onFinish() {
                countdownPopup.setMessage("Waiting for game start message...");
            }
        }.start();
    }

    private int getCardDrawable(int cardValue) {
        switch (cardValue) {
            case 0:
                return R.drawable.spadesa;
            case 1:
                return R.drawable.spades2;
            case 2:
                return R.drawable.spades3;
            case 3:
                return R.drawable.spades4;
            case 4:
                return R.drawable.spades5;
            case 5:
                return R.drawable.spades6;
            case 6:
                return R.drawable.spades7;
            case 7:
                return R.drawable.spades8;
            case 8:
                return R.drawable.spades9;
            case 9:
                return R.drawable.spadest;
            case 10:
                return R.drawable.spadesj;
            case 11:
                return R.drawable.spadesq;
            case 12:
                return R.drawable.spadesk;
            case 13:
                return R.drawable.heartsa;
            case 14:
                return R.drawable.hearts2;
            case 15:
                return R.drawable.hearts3;
            case 16:
                return R.drawable.hearts4;
            case 17:
                return R.drawable.hearts5;
            case 18:
                return R.drawable.hearts6;
            case 19:
                return R.drawable.hearts7;
            case 20:
                return R.drawable.hearts8;
            case 21:
                return R.drawable.hearts9;
            case 22:
                return R.drawable.heartst;
            case 23:
                return R.drawable.heartsj;
            case 24:
                return R.drawable.heartsq;
            case 25:
                return R.drawable.heartsk;
            case 26:
                return R.drawable.diamontsa;
            case 27:
                return R.drawable.diamonts2;
            case 28:
                return R.drawable.diamonts3;
            case 29:
                return R.drawable.diamonts4;
            case 30:
                return R.drawable.diamonts5;
            case 31:
                return R.drawable.diamonts6;
            case 32:
                return R.drawable.diamonts7;
            case 33:
                return R.drawable.diamonts8;
            case 34:
                return R.drawable.diamonts9;
            case 35:
                return R.drawable.diamontst;
            case 36:
                return R.drawable.diamontsj;
            case 37:
                return R.drawable.diamontsq;
            case 38:
                return R.drawable.diamontsk;
            case 39:
                return R.drawable.clubsa;
            case 40:
                return R.drawable.clubs2;
            case 41:
                return R.drawable.clubs3;
            case 42:
                return R.drawable.clubs4;
            case 43:
                return R.drawable.clubs5;
            case 44:
                return R.drawable.clubs6;
            case 45:
                return R.drawable.clubs7;
            case 46:
                return R.drawable.clubs8;
            case 47:
                return R.drawable.clubs9;
            case 48:
                return R.drawable.clubst;
            case 49:
                return R.drawable.clubsj;
            case 50:
                return R.drawable.clubsq;
            case 51:
                return R.drawable.clubsk;
            default:
                return R.drawable.back;
        }
    }

    // This class is the main listener for commands from game server.
    private class MessageHandler implements MessageDispatcher.MessageListener {
        @Override
        public void onMessageReceived(JSONObject jsonResponse) {
            {
                try {
                    int code = jsonResponse.getInt("code");

                    if (code == 1) { // Player joined the room
                        // Parse player joined message and handle the incoming player.
                        JSONObject joiningPlayer = jsonResponse.getJSONObject("user");
                        String joiningUsername = joiningPlayer.getString("username");
                        int joiningWin = joiningPlayer.getInt("winCount");
                        int joiningGame = joiningPlayer.getInt("gameCount");
                        User user = new User(joiningUsername, joiningWin, joiningGame);
                        runOnUiThread(() -> handlePlayerJoin(user));
                    } else if (code == 2) { // Game start message
                        runOnUiThread(() -> startGame(jsonResponse));
                    } else if (code == 3) { // Turn change message
                        boolean yourTurn = jsonResponse.getBoolean("yourTurn");
                        runOnUiThread(() -> handleTurnChange(yourTurn));
                    } else if (code == 4) { // Hit or Stand result
                        runOnUiThread(() -> handleHitResult(jsonResponse));
                    } else if (code == 5) { // Game end message
                        runOnUiThread(() -> handleGameEnd(jsonResponse));
                    } else if (code == 7) { // Player left
                        runOnUiThread(GameRoomActivity.this::handlePlayerLeft);
                    } else if (code == 9) {
                        runOnUiThread(() -> handleLeaveGame(jsonResponse));
                    }
                } catch (Exception e) {
                    Log.e("GameRoomActivity", "Error in server communication", e);
                }
            }
        }
    }
}
