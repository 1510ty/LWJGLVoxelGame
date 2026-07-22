//        LWJGLVoxelGame
//        Copyright (C) 2026  1510ty
//
//        This program is free software: you can redistribute it and/or modify
//        it under the terms of the GNU General Public License as published by
//        the Free Software Foundation, either version 3 of the License, or
//        (at your option) any later version.
//
//        This program is distributed in the hope that it will be useful,
//        but WITHOUT ANY WARRANTY; without even the implied warranty of
//        MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//        GNU General Public License for more details.
//
//        You should have received a copy of the GNU General Public License
//        along with this program.  If not, see <https://www.gnu.org/licenses/>.
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