package com.example.blackjackgameclient;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class MessageDispatcher {

    private static MessageDispatcher instance;
    private BufferedReader reader;
    private Thread listenerThread;
    private boolean isListening = true;

    private Context appContext; // Application context for showing Toasts

    public interface MessageListener {
        void onMessageReceived(JSONObject message);
    }

    private final Map<String, MessageListener> listeners = new HashMap<>();

    private MessageDispatcher() {}

    public static synchronized MessageDispatcher getInstance() {
        if (instance == null) {
            instance = new MessageDispatcher();
        }
        return instance;
    }

    public void setContext(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void startListening(Socket socket) {
        if (listenerThread != null && listenerThread.isAlive()) {
            return;
        }

        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (Exception e) {
            Log.e("MessageDispatcher", "Error initializing reader", e);
        }

        listenerThread = new Thread(() -> {
            while (isListening) {
                try {
                    String messageStr = reader.readLine();
                    if (messageStr != null) {
                        Log.d("MessageDispatcher", "Received message: " + messageStr);
                        JSONObject message = new JSONObject(messageStr);
                        dispatchMessage(message);
                    }
                } catch (Exception e) {
                    if (isListening) {
                        Log.e("MessageDispatcher", "Error reading message", e);
                    }
                }
            }
        });

        listenerThread.start();
    }

    public void stopListening() {
        isListening = false;
        if (listenerThread != null) {
            listenerThread.interrupt();
            listenerThread = null;
        }
    }

    private void dispatchMessage(JSONObject message) {
        try {
            int code = message.getInt("code");

            if (code == 1000) {
                // Handle global error messages
                String errorMessage = message.optString("message", "An error occurred.");
                showToast(errorMessage);
                return;
            }

            String activity;
            if (code == 6) {
                activity = "LobbyActivity";
            } else {
                activity = "GameRoomActivity";
            }

            MessageListener listener = listeners.get(activity);
            if (listener != null) {
                listener.onMessageReceived(message);
            } else {
                Log.w("MessageDispatcher", "No active listener for targetActivity: " + activity);
            }
        } catch (Exception e) {
            Log.e("MessageDispatcher", "Error dispatching message", e);
        }
    }

    private void showToast(String message) {
        if (appContext != null) {
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
            );
        } else {
            Log.e("MessageDispatcher", "Application context is not set. Cannot show Toast.");
        }
    }

    public void registerListener(String activityName, MessageListener listener) {
        listeners.put(activityName, listener);
    }

    public void unregisterListener(String activityName) {
        listeners.remove(activityName);
    }
}
