package com.example.qrgameteam15;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SingletonPlayer {
    public static Player player = new Player("", "");
    public static double lat = -1;  // player current location
    public static double lon = -1;  // player current location
    public SingletonPlayer() {
    }
}
