package com.mojang.rubydung.level;

public interface LevelListener {
    void tileChanged(int x, int y, int z);

    void lightChanged(int x, int z, int yFrom, int yTo);

    void allChanged();
}
