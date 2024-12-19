package com.example.blackjackgameclient;

import java.net.Socket;

public class SocketManager {
    private static Socket socket;

    public static void setSocket(Socket socket) {
        SocketManager.socket = socket;
    }

    public static Socket getSocket() {
        return socket;
    }
}
