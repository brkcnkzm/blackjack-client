package com.example.blackjackgameclient;

import org.parceler.Parcel;

@Parcel
public class User {
    String username;
    int winCount;
    int gameCount;

    public User() {
    }

    public User(String username, int winCount, int gameCount) {
        this.username = username;
        this.winCount = winCount;
        this.gameCount = gameCount;
    }
}
