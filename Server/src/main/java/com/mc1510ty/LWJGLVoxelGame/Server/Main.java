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
package com.mc1510ty.LWJGLVoxelGame.Server;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Main {
    private static final int WORLD_SIZE_X = 16;
    private static final int WORLD_SIZE_Y = 4;
    private static final int WORLD_SIZE_Z = 16;

    public static void main(String[] args) {
        System.out.println("Starting LWJGLVoxelGame Server...");

        // サーバー側でワールドデータを管理・生成
        int[][][] worldData = new int[WORLD_SIZE_X][WORLD_SIZE_Y][WORLD_SIZE_Z];
        for (int x = 0; x < WORLD_SIZE_X; x++) {
            for (int z = 0; z < WORLD_SIZE_Z; z++) {
                worldData[x][0][z] = 1; // 床
            }
        }
        worldData[8][1][8] = 1;
        worldData[8][2][8] = 1;
        worldData[5][1][5] = 1;

        try (ServerSocket serverSocket = new ServerSocket(25565)) {
            System.out.println("Server started on port 25565. Waiting for client...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getRemoteSocketAddress());

                // 別スレッドで接続ごとにワールドデータを送信
                new Thread(() -> {
                    try (DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {
                        // 1. ワールドのサイズを送信
                        out.writeInt(WORLD_SIZE_X);
                        out.writeInt(WORLD_SIZE_Y);
                        out.writeInt(WORLD_SIZE_Z);

                        // 2. ブロックデータを順番に送信
                        for (int x = 0; x < WORLD_SIZE_X; x++) {
                            for (int y = 0; y < WORLD_SIZE_Y; y++) {
                                for (int z = 0; z < WORLD_SIZE_Z; z++) {
                                    out.writeInt(worldData[x][y][z]);
                                }
                            }
                        }
                        out.flush();
                        System.out.println("World data sent to client successfully.");
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        try {
                            clientSocket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}