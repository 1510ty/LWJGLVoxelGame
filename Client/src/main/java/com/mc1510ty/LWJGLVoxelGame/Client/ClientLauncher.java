package com.mc1510ty.LWJGLVoxelGame.Client;

import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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

    // ゲームの状態を表す列挙型
    public enum GameState {
        MENU,
        PLAYING
    }
    private GameState currentState = GameState.MENU;

    private World world;
    private Camera camera;
    private Renderer renderer;
    private Process serverProcess; // 起動した内蔵サーバーのプロセス保持用

    // メニュー用のボタン
    private Button singlePlayerButton;
    private Button multiPlayerButton;

    public void run() {
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
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

        window = glfwCreateWindow(WIDTH, HEIGHT, "Voxel Game Client", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("GLFWウィンドウの作成に失敗しました。");
        }

        // 初期状態はメニューなので、マウスカーソルを表示する
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);

        camera = new Camera();

        // マウス座標保持用の変数
        final double[] mouseX = {0.0};
        final double[] mouseY = {0.0};

        glfwSetCursorPosCallback(window, (w, xpos, ypos) -> {
            mouseX[0] = xpos;
            mouseY[0] = ypos;

            // プレイ中のみ視点移動を有効にする
            if (currentState == GameState.PLAYING) {
                if (firstMouse) {
                    lastX = xpos;
                    lastY = ypos;
                    firstMouse = false;
                }
                float xoffset = (float) (xpos - lastX);
                float yoffset = (float) (lastY - ypos);
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

        // マウスクリックのコールバックを追加
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
        renderer.initUI(); // UI用のシェーダーやバッファを初期化

        // ボタンの生成（画面幅1280, 高さ720の中央付近に配置）
        // 幅400、高さ50 のボタンを上下に並べる
        singlePlayerButton = new Button(440, 260, 400, 50, "Single Player");
        multiPlayerButton  = new Button(440, 330, 400, 50, "Multi Player");
    }

    // 内蔵サーバーを一時フォルダに展開して実行する
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

            System.out.println("Starting embedded server process...");
            String javaPath = System.getProperty("java.home") + "/bin/java";
            ProcessBuilder pb = new ProcessBuilder(javaPath, "-jar", serverFile.getAbsolutePath());
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
                Socket socket = new Socket("localhost", 25565);
                DataInputStream in = new DataInputStream(socket.getInputStream());

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

                socket.close();
                System.out.println("World data received from server!");
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
        Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(45.0f), (float)WIDTH / HEIGHT, 0.1f, 100.0f);
        double lastFrameTime = glfwGetTime();

        while (!glfwWindowShouldClose(window)) {
            double currentFrameTime = glfwGetTime();
            float deltaTime = (float) (currentFrameTime - lastFrameTime);
            lastFrameTime = currentFrameTime;

            if (currentState == GameState.MENU) {
                // キーボードでの操作も引き続きサポート
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

                // 現在のマウス座標を取得してボタンのホバー判定を行う
                double[] mx = new double[1];
                double[] my = new double[1];
                glfwGetCursorPos(window, mx, my);

                // シングルプレイボタンの描画（マウスが乗っていれば明るくする）
                boolean isSingleHovered = singlePlayerButton.isHovered(mx[0], my[0]);
                renderer.renderButton(singlePlayerButton, isSingleHovered, WIDTH, HEIGHT);

                // マルチプレイボタンの描画
                boolean isMultiHovered = multiPlayerButton.isHovered(mx[0], my[0]);
                renderer.renderButton(multiPlayerButton, isMultiHovered, WIDTH, HEIGHT);

            } else if (currentState == GameState.PLAYING) {
                camera.processInput(keys, deltaTime, world);
                renderer.render(world, camera, projection);
            }

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void cleanup() {
        if (serverProcess != null && serverProcess.isAlive()) {
            System.out.println("Stopping embedded server process...");
            serverProcess.destroy();
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
}