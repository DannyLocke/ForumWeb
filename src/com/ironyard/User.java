package com.ironyard;

/**
 * Created by dlocke on 12/9/16.
 */
public class User {

    int id;
    String name;
    String password;


    public User(int id, String name, String password) {
        this.id = id;
        this.name = name;
        this.password = password;
    }

    public User(String name) {
        this.name = name;
    }
}
