package com.example.blackjackgameclient;

import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;
import org.parceler.Parcels;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;

public class LobbyActivity extends AppCompatActivity {
    private String username;
    private int winCount;
    private int gameCount;
    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;
    private MessageDispatcher.MessageListener messageListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_details);

        // Read variables passed from previous activity
        username = getIntent().getStringExtra("username");
        winCount = getIntent().getIntExtra("winCount", 0);
        gameCount = getIntent().getIntExtra("gameCount", 0);

        // Initialize socket and message listener
        socket = SocketManager.getSocket();
        messageListener = new LobbyActivityMessageListener();
        MessageDispatcher.getInstance().registerListener("LobbyActivity",messageListener);
        MessageDispatcher.getInstance().startListening(socket);

        // Initialize ui elements
        TextView usernameTextView = findViewById(R.id.usernameTextView);
        TextView winCountTextView = findViewById(R.id.winCountTextView);
        TextView gameCountTextView = findViewById(R.id.gameCountTextView);
        Button createRoomButton = findViewById(R.id.createRoomButton);
        Button joinRoomButton = findViewById(R.id.joinRoomButton);

        // Set variables
        usernameTextView.setText("Username: " + username);
        winCountTextView.setText("Wins: " + winCount);
        gameCountTextView.setText("Games Played: " + gameCount);

        // Set click listeners for buttons
        createRoomButton.setOnClickListener(v -> createRoom());
        joinRoomButton.setOnClickListener(v -> joinRoom());
    }

    // When create room button is pressed send create room message
    private void createRoom() {
        new Thread(() -> {
            try {
                JSONObject request = new JSONObject();
                request.put("code", 2);
                writer = new PrintWriter(socket.getOutputStream(), true);
                writer.println(request.toString());
            } catch (Exception e) {
                Log.e("LobbyActivity", "Error creating room", e);
            }
        }).start();
    }

    // When join room is pressed send join room message
    private void joinRoom() {
        runOnUiThread(() -> {
            // Create a popup and ask for room id
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            builder.setTitle("Enter Room ID");

            final EditText input = new EditText(this);
            input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            builder.setView(input);

            builder.setPositiveButton("Join", (dialog, which) -> {
                String roomIdStr = input.getText().toString().trim();
                if (!roomIdStr.isEmpty()) {
                    int roomId = Integer.parseInt(roomIdStr);
                    sendJoinRoomRequest(roomId);
                } else {
                    Toast.makeText(this, "Room ID cannot be empty", Toast.LENGTH_SHORT).show();
                }
            });

            builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
            builder.show();
        });
    }

    private void sendJoinRoomRequest(int roomId) {
        new Thread(() -> {
            try {
                JSONObject request = new JSONObject();
                request.put("code", 3);
                request.put("roomId", roomId);

                writer = new PrintWriter(socket.getOutputStream(), true);
                writer.println(request.toString());
            } catch (Exception e) {
                Log.e("LobbyActivity", "Error joining room", e);
            }
        }).start();
    }

    private void navigateToGameRoom(int roomName, JSONArray playersArray) {
        try {
            // Put the current user's details
            Intent intent = new Intent(LobbyActivity.this, GameRoomActivity.class);
            intent.putExtra("roomName", roomName);
            Parcelable wrap = Parcels.wrap(new User(username, winCount, gameCount));
            intent.putExtra("user", wrap);

            // If there are players already in the room, put them to intent too
            ArrayList<Parcelable> users = new ArrayList<>();
            for (int i = 0; i < playersArray.length(); i++) {
                JSONObject player = playersArray.getJSONObject(i);
                User user = new User(player.getString("username"), player.getInt("winCount"), player.getInt("gameCount"));
                users.add(Parcels.wrap(user));
            }
            Bundle bundle = new Bundle();
            bundle.putParcelableArrayList("users", users);
            intent.putExtra("users", bundle);

            // Go to next view
            startActivity(intent);
        } catch (Exception e) {
            Log.e("LobbyActivity", "Error navigating to GameRoomActivity", e);
        }
    }

    private class LobbyActivityMessageListener implements MessageDispatcher.MessageListener {
        @Override
        public void onMessageReceived(JSONObject jsonResponse) {
            try {
                // Since only join room server message arrives here, we don't need to check for code etc.
                int roomName = jsonResponse.getInt("roomName");
                JSONArray playersArray = jsonResponse.getJSONArray("players");

                navigateToGameRoom(roomName, playersArray);
            } catch (Exception e) {
                Log.e("LobbyActivity", "Error handling room details");
            }
        }
    }

    // When this activity is finished remove it from listeners.
    @Override
    protected void onDestroy() {
        super.onDestroy();
        MessageDispatcher.getInstance().unregisterListener("LobbyActivity");
    }
}
