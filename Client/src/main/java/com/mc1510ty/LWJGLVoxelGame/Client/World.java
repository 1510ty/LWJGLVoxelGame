package com.mc1510ty.LWJGLVoxelGame.Client;

public class World {
    public static int SIZE_X = 16;
    public static int SIZE_Y = 4;
    public static int SIZE_Z = 16;

    private final int[][][] data;

    // サーバーから受信したデータでワールドを構築するコンストラクタ
    public World(int sizeX, int sizeY, int sizeZ, int[][][] loadedData) {
        SIZE_X = sizeX;
        SIZE_Y = sizeY;
        SIZE_Z = sizeZ;
        this.data = loadedData;
    }

    public int getBlock(int x, int y, int z) {
        if (x < 0 || x >= SIZE_X || y < 0 || y >= SIZE_Y || z < 0 || z >= SIZE_Z) {
            return 0;
        }
        return data[x][y][z];
    }

    public void setBlock(int x, int y, int z, int id) {
        if (x >= 0 && x < SIZE_X && y >= 0 && y < SIZE_Y && z >= 0 && z < SIZE_Z) {
            data[x][y][z] = id;
        }
    }
}