package com.tripletriad;

public enum Owner {
    PLAYER, AI, NONE;

    public Owner opposite() {
        return this == PLAYER ? AI : this == AI ? PLAYER : NONE;
    }
}
