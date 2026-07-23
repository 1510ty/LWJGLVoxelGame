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

import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public class Main {
    private static final int CHUNK_SIZE_X = 16;
    private static final int CHUNK_SIZE_Y = 4;
    private static final int CHUNK_SIZE_Z = 16;

    // 接続中の全クライアントの出力ストリームを保持するリスト
    private static final List<DataOutputStream> clients = new CopyOnWriteArrayList<>();

    private static AtomicLong idCounter = new AtomicLong(0);

    public static void main(String[] args) {
        System.out.println("Starting LWJGLVoxelGame Server...");

        String worldFilePath = "world.dat"; // デフォルト
        boolean isIntegrated = false;

        // 引数の解析
        for (String arg : args) {
            if (arg.equals("integrated")) {
                isIntegrated = true;
            } else if (!arg.startsWith("-")) {
                // オプション以外の最初の引数をワールドファイルのパスとして扱う
                worldFilePath = arg;
            }
        }

        System.out.println("Server mode: " + (isIntegrated ? "Integrated (Singleplayer)" : "Standalone (Multiplayer)"));


        File configFile = new File("serversettings.yaml");
        Yaml yaml = new Yaml();
        int serverPort = 35565; // デフォルトのポート

        if (!isIntegrated) {
            // スタンドアロンモードの場合のみ設定ファイルを扱う
            if (!configFile.exists()) {
                try (InputStream in = Main.class.getResourceAsStream("/serverproperties.yaml")) {
                    if (in != null) {
                        Files.copy(in, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        System.out.println("serverproperties.yaml をリソースから自動配置しました。");
                    } else {
                        System.out.println("リソース内に serverproperties.yaml が見つかりませんでした。");
                    }
                } catch (IOException e) {
                    System.out.println("serverproperties.yaml の配置に失敗しました。");
                    e.printStackTrace();
                }
            }

            if (configFile.exists()) {

                try (InputStream in = Files.newInputStream(configFile.toPath())) {
                    Map<String, Object> data = yaml.load(in);

                    if (data != null && data.containsKey("server-port")) {
                        // Number 型にキャストしてから intValue() を呼ぶと安全です
                        serverPort = ((Number) data.get("server-port")).intValue();
                    }
                } catch (Exception e) {
                    System.out.println("設定ファイルの読み込みに失敗しました。デフォルト値を使用します。");
                    e.printStackTrace();
                }
            }
        } else {
            System.out.println("Integratedモードで起動中のため、設定ファイルは使用せずデフォルト設定で動作します。");
        }

        File worldFile = new File(worldFilePath);

        int[][][] worldData = new int[CHUNK_SIZE_X][CHUNK_SIZE_Y][CHUNK_SIZE_Z];

        if (worldFile.exists()) {
            try (DataInputStream fileIn = new DataInputStream(new FileInputStream(worldFile))) {
                int sizeX = fileIn.readInt();
                int sizeY = fileIn.readInt();
                int sizeZ = fileIn.readInt();

                for (int x = 0; x < Math.min(sizeX, CHUNK_SIZE_X); x++) {
                    for (int y = 0; y < Math.min(sizeY, CHUNK_SIZE_Y); y++) {
                        for (int z = 0; z < Math.min(sizeZ, CHUNK_SIZE_Z); z++) {
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

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            kickPlayers();
            saveWorld(worldFile, worldData);
        }));

        try (ServerSocket serverSocket = new ServerSocket(serverPort)) {
            System.out.println("Server started on port " + serverPort + ". Waiting for client...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getRemoteSocketAddress());

                new Thread(() -> {
                    long myId = idCounter.incrementAndGet();
                    DataOutputStream out = null;
                    try {
                        out = new DataOutputStream(clientSocket.getOutputStream());
                        DataInputStream in = new DataInputStream(clientSocket.getInputStream());

                        // リストに登録
                        clients.add(out);

                        // ★ 共通化したメソッドを使ってワールドデータを送信
                        sendWorldData(out, worldData);
                        System.out.println("World data sent to client successfully.");

                        // 3. 接続を維持しつつ、クライアントからの変更通知を待ち受ける
                        while (!clientSocket.isClosed()) {
                            int packetType = in.readInt();

                            if (packetType == 1) {
                                // ブロック変更のパケット
                                int x = in.readInt();
                                int y = in.readInt();
                                int z = in.readInt();
                                int id = in.readInt();

                                if (x >= 0 && x < CHUNK_SIZE_X && y >= 0 && y < CHUNK_SIZE_Y && z >= 0 && z < CHUNK_SIZE_Z) {
                                    worldData[x][y][z] = id;
                                    System.out.println("Block updated at (" + x + ", " + y + ", " + z + ") to ID: " + id);

                                    // ★ ブロックが変更されたら、接続中の全クライアントに最新のワールド全体を送信する
                                    for (DataOutputStream clientOut : clients) {
                                        try {
                                            sendWorldData(clientOut, worldData);
                                        } catch (IOException e) {
                                            // 送信失敗時はスルー
                                        }
                                    }
                                }
                            } else if (packetType == 2) {
                                double px = in.readDouble();
                                double py = in.readDouble();
                                double pz = in.readDouble();

                                // 自分以外のクライアントに「ID」と「座標」を転送する
                                for (DataOutputStream clientOut : clients) {
                                    if (clientOut != out) {
                                        try {
                                            clientOut.writeLong(2); // パケットID 2
                                            clientOut.writeLong(myId); // 誰のIDか一緒に送る
                                            clientOut.writeDouble(px);
                                            clientOut.writeDouble(py);
                                            clientOut.writeDouble(pz);
                                            clientOut.flush();
                                        } catch (IOException e) {
                                        }
                                    }
                                }
                            }
                        }

                    } catch (IOException e) {
                        System.out.println("Client disconnected: " + clientSocket.getRemoteSocketAddress());
                    } finally {
                        if (out != null) {
                            clients.remove(out); // リストから削除
                        }
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

    // ★ ワールドデータを送信する共通メソッド
    private static void sendWorldData(DataOutputStream out, int[][][] worldData) throws IOException {
        out.writeLong(3); // パケットID 3: ワールド全体データ同期
        out.writeInt(CHUNK_SIZE_X);
        out.writeInt(CHUNK_SIZE_Y);
        out.writeInt(CHUNK_SIZE_Z);

        for (int x = 0; x < CHUNK_SIZE_X; x++) {
            for (int y = 0; y < CHUNK_SIZE_Y; y++) {
                for (int z = 0; z < CHUNK_SIZE_Z; z++) {
                    out.writeInt(worldData[x][y][z]);
                }
            }
        }
        out.flush();
    }

    private static void generateDefaultWorld(int[][][] worldData) {
        for (int x = 0; x < CHUNK_SIZE_X; x++) {
            for (int z = 0; z < CHUNK_SIZE_Z; z++) {
                worldData[x][0][z] = 1;
            }
        }
        worldData[8][1][8] = 1;
        worldData[8][2][8] = 1;
        worldData[5][1][5] = 1;
    }

    private static void saveWorld(File file, int[][][] worldData) {
        try (DataOutputStream fileOut = new DataOutputStream(new FileOutputStream(file))) {
            fileOut.writeInt(CHUNK_SIZE_X);
            fileOut.writeInt(CHUNK_SIZE_Y);
            fileOut.writeInt(CHUNK_SIZE_Z);

            for (int x = 0; x < CHUNK_SIZE_X; x++) {
                for (int y = 0; y < CHUNK_SIZE_Y; y++) {
                    for (int z = 0; z < CHUNK_SIZE_Z; z++) {
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

    private static void kickPlayers() {
        for (DataOutputStream out : clients) {
            try {
                out.writeLong(-1); // 切断を伝えるパケット（例）
                out.flush();
            } catch (IOException e) {
                // すでに切断されている場合はスルー
            }
        }
    }
}