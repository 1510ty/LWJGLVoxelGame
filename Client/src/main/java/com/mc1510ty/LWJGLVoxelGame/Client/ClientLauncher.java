package com.mc1510ty.LWJGLVoxelGame.Client;

import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.IntBuffer;

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

        glfwSetCursorPosCallback(window, (w, xpos, ypos) -> {
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
    }

    private World fetchWorldFromServer() {
        try {
            System.out.println("Connecting to server...");
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
            e.printStackTrace();
            throw new RuntimeException("サーバーからのワールドデータの取得に失敗しました。");
        }
    }

    private void loop() {
        Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(45.0f), (float)WIDTH / HEIGHT, 0.1f, 100.0f);
        double lastFrameTime = glfwGetTime();

        while (!glfwWindowShouldClose(window)) {
            double currentFrameTime = glfwGetTime();
            float deltaTime = (float) (currentFrameTime - lastFrameTime);
            lastFrameTime = currentFrameTime;

            // 状態に応じた処理の分岐
            if (currentState == GameState.MENU) {
                // メニュー画面中の入力判定（例: Enterキーでゲーム開始）
                if (keys[GLFW_KEY_ENTER]) {
                    // 1. サーバーからワールドデータを取得
                    world = fetchWorldFromServer();

                    // 2. 状態をプレイ中に変更
                    currentState = GameState.PLAYING;

                    // 3. マウスを非表示・ロックして視点移動できるようにする
                    glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);

                    // マウス位置の急変動を防ぐリセット
                    firstMouse = true;
                }

                // メニュー画面の描画（今は背景色でクリアするだけ）
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            } else if (currentState == GameState.PLAYING) {
                // プレイ中の通常のゲーム処理
                camera.processInput(keys, deltaTime, world);
                renderer.render(world, camera, projection);
            }

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void cleanup() {
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