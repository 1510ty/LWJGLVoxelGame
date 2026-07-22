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

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class Main {
    private static final int WORLD_SIZE_X = 16;
    private static final int WORLD_SIZE_Y = 4;
    private static final int WORLD_SIZE_Z = 16;

    public static void main(String[] args) {
        System.out.println("Starting LWJGLVoxelGame Server...");

        // 引数からワールドデータのファイルパスを取得（指定がない場合はデフォルト名）
        String saveFilePath = (args.length > 0) ? args[0] : "world.dat";
        File worldFile = new File(saveFilePath);

        int[][][] worldData = new int[WORLD_SIZE_X][WORLD_SIZE_Y][WORLD_SIZE_Z];

        // 開始時：ファイルが存在すれば読み込み、なければデフォルト生成
        if (worldFile.exists()) {
            try (DataInputStream fileIn = new DataInputStream(new FileInputStream(worldFile))) {
                int sizeX = fileIn.readInt();
                int sizeY = fileIn.readInt();
                int sizeZ = fileIn.readInt();

                for (int x = 0; x < Math.min(sizeX, WORLD_SIZE_X); x++) {
                    for (int y = 0; y < Math.min(sizeY, WORLD_SIZE_Y); y++) {
                        for (int z = 0; z < Math.min(sizeZ, WORLD_SIZE_Z); z++) {
                            worldData[x][y][z] = fileIn.readInt();
                        }
                    }
                }
                System.out.println("World data loaded from: " + worldFile.getAbsolutePath());
            } catch (IOException e) {
                System.out.println("Failed to load world file. Generating default world...");
                generateDefaultWorld(worldData);
            }
        } else {
            System.out.println("World file not found. Generating default world...");
            generateDefaultWorld(worldData);
        }

        // 終了時（シャットダウン時）にワールドデータをファイルへ保存するフックを登録
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            saveWorld(worldFile, worldData);
        }));

        try (ServerSocket serverSocket = new ServerSocket(25565)) {
            System.out.println("Server started on port 25565. Waiting for client...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getRemoteSocketAddress());

                new Thread(() -> {
                    try {
                        DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());
                        DataInputStream in = new DataInputStream(clientSocket.getInputStream());

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

                        // 3. 接続を維持しつつ、クライアントからの変更通知を待ち受ける
                        while (!clientSocket.isClosed()) {
                            int x = in.readInt();
                            int y = in.readInt();
                            int z = in.readInt();
                            int id = in.readInt();

                            if (id == -1) {
                                System.out.println("Shutdown request received from client.");
                                System.exit(0); // これにより ShutdownHook が確実に呼ばれる！
                            }

                            // サーバー側のメモリ上のワールドデータを更新
                            if (x >= 0 && x < WORLD_SIZE_X && y >= 0 && y < WORLD_SIZE_Y && z >= 0 && z < WORLD_SIZE_Z) {
                                worldData[x][y][z] = id;
                                System.out.println("Block updated at (" + x + ", " + y + ", " + z + ") to ID: " + id);
                            }
                        }

                    } catch (IOException e) {
                        System.out.println("Client disconnected: " + clientSocket.getRemoteSocketAddress());
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

    private static void generateDefaultWorld(int[][][] worldData) {
        for (int x = 0; x < WORLD_SIZE_X; x++) {
            for (int z = 0; z < WORLD_SIZE_Z; z++) {
                worldData[x][0][z] = 1; // 床
            }
        }
        worldData[8][1][8] = 1;
        worldData[8][2][8] = 1;
        worldData[5][1][5] = 1;
    }

    private static void saveWorld(File file, int[][][] worldData) {
        try (DataOutputStream fileOut = new DataOutputStream(new FileOutputStream(file))) {
            fileOut.writeInt(WORLD_SIZE_X);
            fileOut.writeInt(WORLD_SIZE_Y);
            fileOut.writeInt(WORLD_SIZE_Z);

            for (int x = 0; x < WORLD_SIZE_X; x++) {
                for (int y = 0; y < WORLD_SIZE_Y; y++) {
                    for (int z = 0; z < WORLD_SIZE_Z; z++) {
                        fileOut.writeInt(worldData[x][y][z]);
                    }
                }
            }
            fileOut.flush();
            System.out.println("World data successfully saved to: " + file.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}