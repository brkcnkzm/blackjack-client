package com.example.blackjackgameclient;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import java.io.IOException;
import java.net.Socket;

public class MainActivity extends AppCompatActivity {

    private static final String SERVER_IP = "16.171.111.209";
    private static final int SERVER_PORT = 12345;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        new Thread(() -> {
            try {
                // Create a socket connection to the server
                Socket socket = new Socket(SERVER_IP, SERVER_PORT);
                Log.d("MainActivity", "Socket connected to " + SERVER_IP + ":" + SERVER_PORT);

                // Set the socket for whole app via SocketManager
                SocketManager.setSocket(socket);
                MessageDispatcher.getInstance().setContext(this);

                // Go to login screen
                runOnUiThread(() -> {
                    Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                    startActivity(intent);
                    finish();
                });
            } catch (IOException e) {
                Log.e("MainActivity", "Error connecting to server", e);
            }
        }).start();
    }
}
