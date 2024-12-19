package com.example.    blackjackgameclient;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;

public class LoginActivity extends AppCompatActivity {
    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        socket = SocketManager.getSocket();

        // Setup ui elements
        EditText usernameEditText = findViewById(R.id.usernameEditText);
        Button loginButton = findViewById(R.id.loginButton);

        // Set click listener for login button
        loginButton.setOnClickListener(v -> {
            String username = usernameEditText.getText().toString().trim();
            if (!username.isEmpty()) {
                sendLoginRequest(username);
            } else {
                Toast.makeText(this, "Username cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });

        new Thread(() -> {
            try {
                writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            } catch (Exception e) {
                Log.e("LoginActivity", "Error setting up socket streams", e);
            }
        }).start();
    }

    private void sendLoginRequest(String username) {
        new Thread(() -> {
            try {
                // Create request object
                JSONObject json = new JSONObject();
                json.put("username", username);
                json.put("code", 1);

                writer.println(json.toString());

                // Wait for response
                String response = reader.readLine();
                if (response != null) {
                    // Read response and set user details for next activity
                    JSONObject jsonResponse = new JSONObject(response);
                    int code = jsonResponse.getInt("code");
                    if (code == 0) {
                        JSONObject user = jsonResponse.getJSONObject("user");
                        String userName = user.getString("username");
                        int winCount = user.getInt("winCount");
                        int gameCount = user.getInt("gameCount");

                        runOnUiThread(() -> navigateToNextScreen(userName, winCount, gameCount));
                    } else if (code == 1000) {
                        String errorMessage = jsonResponse.optString("message", "An error occurred.");
                        runOnUiThread(() -> Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show());
                    }else {
                        Log.e("LoginActivity", "Unexpected response code: " + code);
                    }
                }
            } catch (Exception e) {
                Log.e("LoginActivity", "Error during login process", e);
            }
        }).start();
    }

    private void navigateToNextScreen(String username, int winCount, int gameCount) {
        Intent intent = new Intent(this, LobbyActivity.class);
        intent.putExtra("username", username);
        intent.putExtra("winCount", winCount);
        intent.putExtra("gameCount", gameCount);
        startActivity(intent);
        finish();
    }
}
