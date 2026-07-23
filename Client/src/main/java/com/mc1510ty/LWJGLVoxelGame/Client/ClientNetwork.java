package com.mc1510ty.LWJGLVoxelGame.Client;

import org.joml.Vector3d;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

import static org.lwjgl.glfw.GLFW.*;

public class ClientNetwork {

    public ClientLauncher.WorldConnectionResult fetchWorldFromServer(String host, int port, Socket serverSocket, DataOutputStream serverOut, java.util.Map<Long, Vector3d> otherPlayers) {
        int maxRetries = 10;
        int attempts = 0;

        while (attempts < maxRetries) {
            try {
                System.out.println("Connecting to " + host + ":" + port + " (Attempt " + (attempts + 1) + ")...");

                serverSocket = new Socket(host, port);
                DataInputStream in = new DataInputStream(serverSocket.getInputStream());
                serverOut = new DataOutputStream(serverSocket.getOutputStream());

                int initialPacketType = in.readInt();
                int sizeX = in.readInt();
                int sizeY = in.readInt();
                int sizeZ = in.readInt();

                int[][][] loadedData = new int[sizeX][sizeY][sizeZ];
                for (int x = 0; x < sizeX; x++) {
                    for (int y = 0; y < sizeY; y++) {
                        for (int z = 0; z < sizeZ; z++) {
                            loadedData[x][y][z] = in.readInt();
                        }
                    }
                }

                World createdWorld = new World(sizeX, sizeY, sizeZ, loadedData);

                final Socket currentSocket = serverSocket;
                final DataInputStream currentIn = in;

                new Thread(() -> {
                    try {
                        while (!currentSocket.isClosed()) {
                            int packetType = currentIn.readInt();

                            if (packetType == 3) {
                                int sX = in.readInt();
                                int sY = in.readInt();
                                int sZ = in.readInt();

                                for (int x = 0; x < sX; x++) {
                                    for (int y = 0; y < sY; y++) {
                                        for (int z = 0; z < sZ; z++) {
                                            int blockId = in.readInt();
                                            if (createdWorld != null) {
                                                createdWorld.setBlock(x, y, z, blockId);
                                            }
                                        }
                                    }
                                }
                            } else if (packetType == 2) {
                                long targetId = in.readLong();
                                double px = in.readDouble();
                                double py = in.readDouble();
                                double pz = in.readDouble();

                                otherPlayers.computeIfAbsent(targetId, id -> new Vector3d()).set(px, py, pz);
                            }
                        }
                    } catch (IOException e) {
                        System.out.println("Disconnected from server.");
                    }
                }, "Client-Packet-Receiver").start();

                System.out.println("World data received from server and synchronization thread started!");
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

    public ClientLauncher.ConnectionResult connectToServerWithInput(StringBuilder addressInput, long window, World world, ClientLauncher.GameState currentState, boolean firstMouse, Socket serverSocket, DataOutputStream serverOut, java.util.Map<Long, Vector3d> otherPlayers) {
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
            ClientLauncher.WorldConnectionResult result = fetchWorldFromServer(host, port,serverSocket,serverOut, otherPlayers);
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
