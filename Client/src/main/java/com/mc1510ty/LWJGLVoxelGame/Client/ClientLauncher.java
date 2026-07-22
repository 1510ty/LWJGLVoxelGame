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

import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import java.io.*;
import java.net.Socket;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class ClientLauncher {

    enum GameState {
        MENU,
        ADDRESS_INPUT, // アドレス入力画面
        PLAYING
    }

    private GameState currentState = GameState.MENU;
    private StringBuilder addressInput = new StringBuilder("localhost:25565");

    private long window;

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;

    private final boolean[] keys = new boolean[1024];

    private boolean firstMouse = true;
    private double lastX = WIDTH / 2.0;
    private double lastY = HEIGHT / 2.0;

    private World world;
    private Camera camera;
    private Renderer renderer;
    private FontRenderer fontRenderer;
    private Process serverProcess;

    private Button singlePlayerButton;
    private Button multiPlayerButton;

    private DataOutputStream serverOut;
    private Socket serverSocket;

    private String worldFilePath;

    private final java.util.Map<Integer, Vector3d> otherPlayers = new java.util.concurrent.ConcurrentHashMap<>();

    private double lastSendTime = 0;

    public void run(String[] args) {
        if (args.length > 0) {
            worldFilePath = args[0];
        } else {
            File tempDir = new File(System.getProperty("java.io.tmpdir"), "voxelgame_server");
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }
            worldFilePath = new File(tempDir, "world.dat").getAbsolutePath();
        }

        init();
        loop();
        cleanup();
    }

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) {
            throw new IllegalStateException("GLFWの初期化に失敗しました。");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 6);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

        window = glfwCreateWindow(WIDTH, HEIGHT, "Voxel Game Client", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("GLFWウィンドウの作成に失敗しました。");
        }

        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);

        camera = new Camera();

        final double[] mouseX = {0.0};
        final double[] mouseY = {0.0};

        glfwSetCursorPosCallback(window, (w, xpos, ypos) -> {
            mouseX[0] = xpos;
            mouseY[0] = ypos;

            if (currentState == GameState.PLAYING) {
                if (firstMouse) {
                    lastX = xpos;
                    lastY = ypos;
                    firstMouse = false;
                }
                double xoffset = (xpos - lastX);
                double yoffset = (lastY - ypos);
                lastX = xpos;
                lastY = ypos;

                camera.processMouseMovement(xoffset, yoffset);
            }
        });

        glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            if (currentState == GameState.MENU && button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS) {
                if (singlePlayerButton != null && singlePlayerButton.isHovered(mouseX[0], mouseY[0])) {
                    extractAndStartServer();
                    world = fetchWorldFromServer("localhost", 25565);
                    currentState = GameState.PLAYING;
                    glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                    firstMouse = true;
                } else if (multiPlayerButton != null && multiPlayerButton.isHovered(mouseX[0], mouseY[0])) {
                    // マルチプレイボタンを押したらアドレス入力画面へ切り替える
                    currentState = GameState.ADDRESS_INPUT;
                }
            } else if (currentState == GameState.PLAYING && action == GLFW_PRESS) {
                RaycastResult hit = raycast(6.0f);
                if (hit.hit) {
                    if (button == GLFW_MOUSE_BUTTON_LEFT) {
                        world.setBlock(hit.x, hit.y, hit.z, 0);
                        sendBlockChange(hit.x, hit.y, hit.z, 0);
                    } else if (button == GLFW_MOUSE_BUTTON_RIGHT) {
                        world.setBlock(hit.prevX, hit.prevY, hit.prevZ, 1);
                        sendBlockChange(hit.prevX, hit.prevY, hit.prevZ, 1);
                    }
                }
            }
        });

        // アドレス入力中の文字入力を受け取るコールバック
        glfwSetCharCallback(window, (w, codepoint) -> {
            if (currentState == GameState.ADDRESS_INPUT) {
                addressInput.append((char) codepoint);
            }
        });

        glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
                if (currentState == GameState.ADDRESS_INPUT) {
                    currentState = GameState.MENU;
                } else {
                    glfwSetWindowShouldClose(window, true);
                }
            }

            // アドレス入力中の特殊キー処理（バックスペースとエンター）
            if (currentState == GameState.ADDRESS_INPUT && (action == GLFW_PRESS || action == GLFW_REPEAT)) {
                if (key == GLFW_KEY_BACKSPACE && addressInput.length() > 0) {
                    addressInput.deleteCharAt(addressInput.length() - 1);
                } else if (key == GLFW_KEY_ENTER) {
                    connectToServerWithInput();
                }
            }

            if (key >= 0 && key < keys.length) {
                if (action == GLFW_PRESS) {
                    keys[key] = true;
                } else if (action == GLFW_RELEASE) {
                    keys[key] = false;
                }
            }
        });

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            glfwGetWindowSize(window, pWidth, pHeight);
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            glfwSetWindowPos(
                    window,
                    (vidmode.width() - pWidth.get(0)) / 2,
                    (vidmode.height() - pHeight.get(0)) / 2
            );
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);
        GL.createCapabilities();

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glClearColor(0.2f, 0.4f, 0.6f, 0.0f);

        renderer = new Renderer();
        renderer.initUI();

        fontRenderer = new FontRenderer("/NotoSansJP-Regular.ttf");

        singlePlayerButton = new Button(440, 260, 400, 50, "Single Player");
        multiPlayerButton  = new Button(440, 330, 400, 50, "Multi Player");
    }

    private void extractAndStartServer() {
        try {
            System.out.println("Extracting embedded server.jar...");
            InputStream in = ClientLauncher.class.getResourceAsStream("/server.jar");
            if (in == null) {
                throw new RuntimeException("内蔵された server.jar が見つかりません。");
            }

            File tempDir = new File(System.getProperty("java.io.tmpdir"), "voxelgame_server");
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }
            File serverFile = new File(tempDir, "server.jar");
            Files.copy(in, serverFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            System.out.println("Starting embedded server process with save path: " + worldFilePath);
            String javaPath = System.getProperty("java.home") + "/bin/java";

            ProcessBuilder pb = new ProcessBuilder(
                    javaPath,
                    "-jar",
                    serverFile.getAbsolutePath(),
                    worldFilePath
            );

            pb.inheritIO();
            serverProcess = pb.start();

            Thread.sleep(2000);

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException("内蔵サーバーの起動に失敗しました。");
        }
    }

    // 入力された文字列をパースしてサーバーに接続するメソッド
    private void connectToServerWithInput() {
        String inputStr = addressInput.toString().trim();
        String host = "localhost";
        int port = 25565;

        if (inputStr.contains(":")) {
            String[] parts = inputStr.split(":", 2);
            host = parts[0];
            try {
                port = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                System.out.println("ポート番号の形式が正しくないため、デフォルトの 25565 を使用します。");
            }
        } else if (!inputStr.isEmpty()) {
            host = inputStr;
        }

        try {
            world = fetchWorldFromServer(host, port);
            currentState = GameState.PLAYING;
            glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
            firstMouse = true;
        } catch (Exception e) {
            System.out.println("サーバーへの接続に失敗しました: " + e.getMessage());
        }
    }

    private World fetchWorldFromServer(String host, int port) {
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

                new Thread(() -> {
                    try {
                        while (!serverSocket.isClosed()) {
                            int packetType = in.readInt();

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
                                int targetId = in.readInt();
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
                return createdWorld;

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

    private void loop() {
        Matrix4d projection = new Matrix4d().perspective(Math.toRadians(45.0f), (double) WIDTH / HEIGHT, 0.1f, 100.0f);
        double lastFrameTime = glfwGetTime();

        while (!glfwWindowShouldClose(window)) {
            double currentFrameTime = glfwGetTime();
            double deltaTime = (currentFrameTime - lastFrameTime);
            lastFrameTime = currentFrameTime;

            if (currentState == GameState.MENU) {
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

                double[] mx = new double[1];
                double[] my = new double[1];
                glfwGetCursorPos(window, mx, my);

                boolean isSingleHovered = singlePlayerButton.isHovered(mx[0], my[0]);
                renderer.renderButton(singlePlayerButton, isSingleHovered, WIDTH, HEIGHT);
                fontRenderer.drawText("SinglePlayer", 470.0f, 272.0f, 1.0f, WIDTH, HEIGHT, new Vector3d(1.0f, 1.0f, 1.0f));

                boolean isMultiHovered = multiPlayerButton.isHovered(mx[0], my[0]);
                renderer.renderButton(multiPlayerButton, isMultiHovered, WIDTH, HEIGHT);
                fontRenderer.drawText("MultiPlayer", 485.0f, 342.0f, 1.0f, WIDTH, HEIGHT, new Vector3d(1.0f, 1.0f, 1.0f));

            } else if (currentState == GameState.ADDRESS_INPUT) {
                // アドレス入力画面の描画
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

                renderer.renderButton(multiPlayerButton, false, WIDTH, HEIGHT);
                fontRenderer.drawText("Enter Server IP:", 440.0f, 220.0f, 1.0f, WIDTH, HEIGHT, new Vector3d(1.0f, 1.0f, 1.0f));
                fontRenderer.drawText(addressInput.toString(), 460.0f, 342.0f, 1.0f, WIDTH, HEIGHT, new Vector3d(1.0f, 1.0f, 1.0f));

            } else if (currentState == GameState.PLAYING) {
                camera.processInput(keys, deltaTime, world);

                double currentTime = glfwGetTime();
                if (currentTime - lastSendTime > 0.05) {
                    sendPosition(camera.pos.x, camera.pos.y, camera.pos.z);
                    lastSendTime = currentTime;
                }

                renderer.render(world, camera, projection, otherPlayers);
            }

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void cleanup() {
        if (serverProcess != null && serverProcess.isAlive()) {
            System.out.println("Stopping embedded server gracefully...");

            if (serverOut != null) {
                try {
                    serverOut.writeInt(-1);
                    serverOut.flush();
                } catch (IOException ignored) {}
            }

            try {
                boolean exited = serverProcess.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
                if (!exited) {
                    serverProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                serverProcess.destroyForcibly();
            }
        }

        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (fontRenderer != null) {
            fontRenderer.cleanup();
        }

        renderer.cleanup();

        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);

        glfwTerminate();
        GLFWErrorCallback callback = glfwSetErrorCallback(null);
        if (callback != null) {
            callback.free();
        }
    }

    private RaycastResult raycast(double maxDistance) {
        RaycastResult result = new RaycastResult();
        if (world == null) return result;

        Vector3d rayPos = new Vector3d(camera.pos);
        Vector3d rayDir = new Vector3d(camera.front);

        double step = 0.05f;
        int lastX = (int) Math.round(rayPos.x);
        int lastY = (int) Math.round(rayPos.y);
        int lastZ = (int) Math.round(rayPos.z);

        for (double d = 0; d < maxDistance; d += step) {
            rayPos.add(new Vector3d(rayDir).mul(step));

            int bx = (int) Math.round(rayPos.x);
            int by = (int) Math.round(rayPos.y);
            int bz = (int) Math.round(rayPos.z);

            if (bx != lastX || by != lastY || bz != lastZ) {
                if (world.getBlock(bx, by, bz) > 0) {
                    result.hit = true;
                    result.x = bx;
                    result.y = by;
                    result.z = bz;
                    result.prevX = lastX;
                    result.prevY = lastY;
                    result.prevZ = lastZ;
                    return result;
                }
                lastX = bx;
                lastY = by;
                lastZ = bz;
            }
        }
        return result;
    }

    private void sendBlockChange(int x, int y, int z, int id) {
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

    private void sendPosition(double x, double y, double z) {
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
}