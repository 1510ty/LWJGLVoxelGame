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
    private long window;


    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;

    private final boolean[] keys = new boolean[1024];

    private boolean firstMouse = true;
    private double lastX = WIDTH / 2.0;
    private double lastY = HEIGHT / 2.0;

    public enum GameState {
        MENU,
        PLAYING
    }
    private GameState currentState = GameState.MENU;

    private World world;
    private Camera camera;
    private Renderer renderer;
    private FontRenderer fontRenderer; // ← 追加
    private Process serverProcess;

    private Button singlePlayerButton;
    private Button multiPlayerButton;

    private DataOutputStream serverOut;
    private Socket serverSocket;

    private String worldFilePath;

    public void run(String[] args) {
        // 引数でワールドファイルのパスが指定されていればそれを使用する
        if (args.length > 0) {
            worldFilePath = args[0];
        } else {
            // 指定がない場合のデフォルトパス（一時フォルダ内）
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

        glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
                glfwSetWindowShouldClose(window, true);
            }
            if (key >= 0 && key < keys.length) {
                if (action == GLFW_PRESS) {
                    keys[key] = true;
                } else if (action == GLFW_RELEASE) {
                    keys[key] = false;
                }
            }
        });


        glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            if (currentState == GameState.MENU && button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS) {
                if (singlePlayerButton != null && singlePlayerButton.isHovered(mouseX[0], mouseY[0])) {
                    extractAndStartServer();
                    world = fetchWorldFromServer();
                    currentState = GameState.PLAYING;
                    glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                    firstMouse = true;
                } else if (multiPlayerButton != null && multiPlayerButton.isHovered(mouseX[0], mouseY[0])) {
                    world = fetchWorldFromServer();
                    currentState = GameState.PLAYING;
                    glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                    firstMouse = true;
                }
            } else if (currentState == GameState.PLAYING && action == GLFW_PRESS) {
                RaycastResult hit = raycast(6.0f); // 射程距離 6ブロック
                if (hit.hit) {
                    if (button == GLFW_MOUSE_BUTTON_LEFT) {
                        // 左クリック：ブロックを破壊
                        world.setBlock(hit.x, hit.y, hit.z, 0);
                        sendBlockChange(hit.x, hit.y, hit.z, 0); // サーバーへ送信
                    } else if (button == GLFW_MOUSE_BUTTON_RIGHT) {
                        // 右クリック：ブロックを設置
                        world.setBlock(hit.prevX, hit.prevY, hit.prevZ, 1);
                        sendBlockChange(hit.prevX, hit.prevY, hit.prevZ, 1); // サーバーへ送信
                    }
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

        // フォントレンダラーの初期化（resourcesフォルダ内のパスを指定）
        fontRenderer = new FontRenderer("/NotoSansJP-Regular.ttf"); // ← 追加

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

            // クライアントが保持しているワールドファイルのパスをそのまま引数として渡す
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

    private World fetchWorldFromServer() {
        int maxRetries = 10;
        int attempts = 0;

        while (attempts < maxRetries) {
            try {
                System.out.println("Connecting to server (Attempt " + (attempts + 1) + ")...");

                // ローカル変数ではなく、クラスのフィールドに代入する
                serverSocket = new Socket("localhost", 25565);
                DataInputStream in = new DataInputStream(serverSocket.getInputStream());
                serverOut = new DataOutputStream(serverSocket.getOutputStream()); // ← ここで出力ストリームを保持！

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

                // socket.close(); は削除！ 接続を維持します。
                System.out.println("World data received from server and connection kept open!");
                return new World(sizeX, sizeY, sizeZ, loadedData);

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
                if (keys[GLFW_KEY_S]) {
                    extractAndStartServer();
                    world = fetchWorldFromServer();
                    currentState = GameState.PLAYING;
                    glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                    firstMouse = true;
                } else if (keys[GLFW_KEY_M]) {
                    world = fetchWorldFromServer();
                    currentState = GameState.PLAYING;
                    glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                    firstMouse = true;
                }

                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

                double[] mx = new double[1];
                double[] my = new double[1];
                glfwGetCursorPos(window, mx, my);

                // シングルプレイボタンの描画
                boolean isSingleHovered = singlePlayerButton.isHovered(mx[0], my[0]);
                renderer.renderButton(singlePlayerButton, isSingleHovered, WIDTH, HEIGHT);
                // ボタンの上に文字を描画（白文字: RGB(1,1,1)）
                fontRenderer.drawText("SinglePlayer", 470.0f, 272.0f, 1.0f, WIDTH, HEIGHT, new Vector3d(1.0f, 1.0f, 1.0f));

                // マルチプレイボタンの描画
                boolean isMultiHovered = multiPlayerButton.isHovered(mx[0], my[0]);
                renderer.renderButton(multiPlayerButton, isMultiHovered, WIDTH, HEIGHT);
                // ボタンの上に文字を描画
                fontRenderer.drawText("MultiPlayer", 485.0f, 342.0f, 1.0f, WIDTH, HEIGHT, new Vector3d(1.0f, 1.0f, 1.0f));

            } else if (currentState == GameState.PLAYING) {
                camera.processInput(keys, deltaTime, world);
                renderer.render(world, camera, projection);
            }

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void cleanup() {
        // インテグレーテッドサーバーが起動している場合（シングルプレイ時）のみ終了処理を行う
        if (serverProcess != null && serverProcess.isAlive()) {
            System.out.println("Stopping embedded server gracefully...");

            // 1. サーバーへ終了パケット（-1）を送信してセーブさせる
            if (serverOut != null) {
                try {
                    serverOut.writeInt(-1); // パケットID -1: 終了シグナル
                    serverOut.flush();
                } catch (IOException ignored) {}
            }

            try {
                // 2. サーバーが安全に終了（セーブ完了）するのを最大3秒待つ
                boolean exited = serverProcess.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
                if (!exited) {
                    // 時間内に終わらなかった場合のみ強制終了
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
                serverOut.writeInt(1); // パケットID 1: ブロック変更
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
}