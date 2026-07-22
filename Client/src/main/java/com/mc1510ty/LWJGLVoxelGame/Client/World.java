package com.mc1510ty.LWJGLVoxelGame.Client;

public class World {
    public static final int SIZE_X = 16;
    public static final int SIZE_Y = 4;
    public static final int SIZE_Z = 16;

    private final int[][][] data = new int[SIZE_X][SIZE_Y][SIZE_Z];

    public World() {
        init();
    }

    private void init() {
        // 床の生成
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                data[x][0][z] = 1;
            }
        }
        // テスト用のブロック
        data[8][1][8] = 1;
        data[8][2][8] = 1;
        data[5][1][5] = 1;
    }

    // 指定した座標にブロックがあるか確認するメソッド（安全に取得できます）
    public int getBlock(int x, int y, int z) {
        if (x < 0 || x >= SIZE_X || y < 0 || y >= SIZE_Y || z < 0 || z >= SIZE_Z) {
            return 0; // ワールド外は空気(0)として扱う
        }
        return data[x][y][z];
    }
}