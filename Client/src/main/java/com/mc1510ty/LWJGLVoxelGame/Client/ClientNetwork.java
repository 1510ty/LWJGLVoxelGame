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

import org.joml.Vector3d;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

import static org.lwjgl.glfw.GLFW.*;

import com.mc1510ty.LWJGLVoxelGame.common.BlockNameIDMgr;

public class ClientNetwork {

    public ClientLauncher.WorldConnectionResult fetchWorldFromServer(String host, int port, Socket serverSocket, DataOutputStream serverOut, java.util.Map<Long, Vector3d> otherPlayers, BlockNameIDMgr blocknameidmgr) {
        int maxRetries = 10;
        int attempts = 0;

        while (attempts < maxRetries) {
            try {
                System.out.println("Connecting to " + host + ":" + port + " (Attempt " + (attempts + 1) + ")...");

                serverSocket = new Socket(host, port);
                DataInputStream in = new DataInputStream(serverSocket.getInputStream());
                serverOut = new DataOutputStream(serverSocket.getOutputStream());

                World createdWorld = null;
                int sizeX = 0, sizeY = 0, sizeZ = 0;

                // 1. 接続直後に送られてくる「ブロック辞書 (パケットID: 4)」を受け取る
                long firstPacketType = in.readLong();
                if (firstPacketType == 4) {
                    int registrySize = in.readInt();
                    for (int i = 0; i < registrySize; i++) {
                        int id = in.readInt();
                        String name = in.readUTF();
                        blocknameidmgr.registerFromServer(id, name);
                    }
                    System.out.println("Block registry received from server. Total: " + registrySize);


                    // 2. その次に送られてくる「ワールドデータ (パケットID: 3)」を受け取る
                    long secondPacketType = in.readLong();
                    if (secondPacketType == 3) {
                        sizeX = in.readInt();
                        sizeY = in.readInt();
                        sizeZ = in.readInt();

                        int[][][] loadedData = new int[sizeX][sizeY][sizeZ];
                        for (int x = 0; x < sizeX; x++) {
                            for (int y = 0; y < sizeY; y++) {
                                for (int z = 0; z < sizeZ; z++) {
                                    loadedData[x][y][z] = in.readInt();
                                }
                            }
                        }
                        createdWorld = new World(sizeX, sizeY, sizeZ, loadedData);
                        createdWorld.setairid(blocknameidmgr.getId("lwjglvoxelgame:air"));
                    }
                }

                final Socket currentSocket = serverSocket;
                final DataInputStream currentIn = in;
                final World finalWorld = createdWorld;

                new Thread(() -> {
                    try {
                        while (!currentSocket.isClosed()) {
                            long packetType = currentIn.readLong();

                            if (packetType == 3) {
                                int sX = currentIn.readInt();
                                int sY = currentIn.readInt();
                                int sZ = currentIn.readInt();

                                for (int x = 0; x < sX; x++) {
                                    for (int y = 0; y < sY; y++) {
                                        for (int z = 0; z < sZ; z++) {
                                            int blockId = currentIn.readInt();
                                            if (finalWorld != null) {
                                                finalWorld.setBlock(x, y, z, blockId);
                                            }
                                        }
                                    }
                                }
                            } else if (packetType == 4) {
                                // プレイ中に辞書が再同期された場合の処理
                                int registrySize = currentIn.readInt();
                                for (int i = 0; i < registrySize; i++) {
                                    int id = currentIn.readInt();
                                    String name = currentIn.readUTF();
                                    blocknameidmgr.registerFromServer(id, name);
                                }
                                finalWorld.setairid(blocknameidmgr.getId("lwjglvoxelgame:air"));
                            } else if (packetType == 2) {
                                long targetId = currentIn.readLong();
                                double px = currentIn.readDouble();
                                double py = currentIn.readDouble();
                                double pz = currentIn.readDouble();

                                otherPlayers.computeIfAbsent(targetId, id -> new Vector3d()).set(px, py, pz);
                            }  else if (packetType == -1) {
                                IO.println("Server Closed");
                            }
                        }
                    } catch (IOException e) {
                        System.out.println("Disconnected from server.");
                    }
                }, "Client-Packet-Receiver").start();

                System.out.println("World data and block registry received from server, and synchronization thread started!");
                return new ClientLauncher.WorldConnectionResult(createdWorld, serverSocket, serverOut);

            } catch (IOException e) {
                attempts++;
                if (attempts >= maxRetries) {
                    throw new RuntimeException("サーバーへの接続に失敗しました。", e);
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {}
            }
        }
        throw new RuntimeException("サーバーからのワールドデータの取得に失敗しました。");
    }

    public void sendBlockChange(int x, int y, int z, int id, DataOutputStream serverOut) {
        if (serverOut != null) {
            try {
                serverOut.writeInt(1);
                serverOut.writeInt(x);
                serverOut.writeInt(y);
                serverOut.writeInt(z);
                serverOut.writeInt(id);
                serverOut.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void sendPosition(double x, double y, double z, DataOutputStream serverOut) {
        if (serverOut != null) {
            try {
                serverOut.writeInt(2);
                serverOut.writeDouble(x);
                serverOut.writeDouble(y);
                serverOut.writeDouble(z);
                serverOut.flush();
            } catch (IOException e) {}
        }
    }

    public ClientLauncher.ConnectionResult connectToServerWithInput(StringBuilder addressInput, long window, World world, ClientLauncher.GameState currentState, boolean firstMouse, Socket serverSocket, DataOutputStream serverOut, java.util.Map<Long, Vector3d> otherPlayers, BlockNameIDMgr blockidnamemgr) {
        String inputStr = addressInput.toString().trim();
        String host = "localhost";
        int port = 35565;

        if (inputStr.contains(":")) {
            String[] parts = inputStr.split(":", 2);
            host = parts[0];
            try {
                port = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                System.out.println("ポート番号の形式が正しくないため、デフォルトの 35565 を使用します。");
            }
        } else if (!inputStr.isEmpty()) {
            host = inputStr;
        }

        try {
            ClientLauncher.WorldConnectionResult result = fetchWorldFromServer(host, port,serverSocket,serverOut, otherPlayers, blockidnamemgr);
            World newWorld = result.world();
            Socket newServerSocket = result.socket();
            DataOutputStream newServerOut = result.serverOut();

            currentState = ClientLauncher.GameState.PLAYING;
            glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
            firstMouse = true;
            return new ClientLauncher.ConnectionResult(newWorld,currentState,firstMouse,newServerSocket,newServerOut);
        } catch (Exception e) {
            System.out.println("サーバーへの接続に失敗しました: " + e.getMessage());
            return new ClientLauncher.ConnectionResult(world, ClientLauncher.GameState.ADDRESS_INPUT, false, serverSocket, serverOut);
        }
    }

}
