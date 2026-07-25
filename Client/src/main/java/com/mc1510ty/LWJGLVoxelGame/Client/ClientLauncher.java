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

import com.mc1510ty.LWJGLVoxelGame.common.BlockNameIDMgr;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import java.io.*;
import java.net.Socket;
import java.nio.IntBuffer;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class ClientLauncher {

    public enum GameState {
        MENU,
        ADDRESS_INPUT, // アドレス入力画面
        PLAYING
    }

    private GameState currentState = GameState.MENU;
    private StringBuilder addressInput = new StringBuilder("localhost:35565");

    private ClientNetwork network = new ClientNetwork();
    private integratedServerMgr integratedservermgr = new integratedServerMgr();

    private BlockNameIDMgr blocknameidmgr = new BlockNameIDMgr();


    private long window;

    public record ConnectionResult(
            World world,
            ClientLauncher.GameState currentState,
            boolean firstMouse,
            Socket socket,
            DataOutputStream serverOut) {}
    public record WorldConnectionResult(
            World world,
            Socket socket,
            DataOutputStream serverOut
    ) {}

    private int windowWidth = 1280;
    private int windowHeight = 720;


    private final boolean[] keys = new boolean[1024];

    private boolean firstMouse = true;
    private double lastX = windowWidth / 2.0;
    private double lastY = windowHeight / 2.0;

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

    private final java.util.Map<Long, Vector3d> otherPlayers = new java.util.concurrent.ConcurrentHashMap<>();

    private double lastSendTime = 0;

    private boolean isBorderlessFullscreen = false;
    private int windowedX, windowedY, windowedWidth, windowedHeight;

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
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

        window = glfwCreateWindow(windowWidth, windowHeight, "Voxel Game Client", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("GLFWウィンドウの作成に失敗しました。");
        }

        glfwSetFramebufferSizeCallback(window, (w, width, height) -> {
            windowWidth = width;
            windowHeight = height;
            glViewport(0, 0, width, height);
        });

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
                    serverProcess = integratedservermgr.extractAndStartServer(worldFilePath,serverProcess);
                    ClientLauncher.WorldConnectionResult result = network.fetchWorldFromServer("localhost", 35565,serverSocket,serverOut, otherPlayers, blocknameidmgr);
                    this.world = result.world();
                    this.serverSocket = result.socket();
                    this.serverOut = result.serverOut();
                    currentState = GameState.PLAYING;
                    glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                    firstMouse = true;
                } else if (multiPlayerButton != null && multiPlayerButton.isHovered(mouseX[0], mouseY[0])) {
                    // マルチプレイボタンを押したらアドレス入力画面へ切り替える
                    currentState = GameState.ADDRESS_INPUT;
                }
            } else if (currentState == GameState.PLAYING && action == GLFW_PRESS) {
                RaycastResult hit = camera.raycast(6.0f,world,camera);
                if (hit.hit) {
                    if (button == GLFW_MOUSE_BUTTON_LEFT) {
                        // 左クリック：問答無用で空気（air）にする
                        int airId = blocknameidmgr.getId("lwjglvoxelgame:air");
                        world.setBlock(hit.x, hit.y, hit.z, airId);
                        network.sendBlockChange(hit.x, hit.y, hit.z, airId, serverOut);
                    } else if (button == GLFW_MOUSE_BUTTON_RIGHT) {
                        // 右クリック：現状は草ブロック（grass_block）にする
                        int grassId = blocknameidmgr.getId("lwjglvoxelgame:grass_block");
                        System.out.println("取得したgrassId: " + grassId); // ここで 0 になっていないか確認！
                        world.setBlock(hit.prevX, hit.prevY, hit.prevZ, grassId);
                        network.sendBlockChange(hit.prevX, hit.prevY, hit.prevZ, grassId, serverOut);
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

            if (key == GLFW_KEY_F11 && action == GLFW_PRESS) {
                long monitor = glfwGetPrimaryMonitor();
                org.lwjgl.glfw.GLFWVidMode mode = glfwGetVideoMode(monitor);

                if (!isBorderlessFullscreen) {
                    // 現在のウィンドウの位置とサイズを保存しておく
                    try (MemoryStack stack = MemoryStack.stackPush()) {
                        IntBuffer pX = stack.mallocInt(1);
                        IntBuffer pY = stack.mallocInt(1);
                        IntBuffer pW = stack.mallocInt(1);
                        IntBuffer pH = stack.mallocInt(1);
                        glfwGetWindowPos(window, pX, pY);
                        glfwGetWindowSize(window, pW, pH);
                        windowedX = pX.get(0);
                        windowedY = pY.get(0);
                        windowedWidth = pW.get(0);
                        windowedHeight = pH.get(0);
                    }

                    // 枠を消してモニターいっぱいに拡大
                    glfwSetWindowAttrib(window, GLFW_DECORATED, GLFW_FALSE);
                    glfwSetWindowPos(window, 0, 0);
                    glfwSetWindowSize(window, mode.width(), mode.height());
                    isBorderlessFullscreen = true;
                } else {
                    // 元のウィンドウ状態に戻す
                    glfwSetWindowAttrib(window, GLFW_DECORATED, GLFW_TRUE);
                    glfwSetWindowPos(window, windowedX, windowedY);
                    glfwSetWindowSize(window, windowedWidth, windowedHeight);
                    isBorderlessFullscreen = false;
                }
            }

            // アドレス入力中の特殊キー処理（バックスペースとエンター）
            if (currentState == GameState.ADDRESS_INPUT && (action == GLFW_PRESS || action == GLFW_REPEAT)) {
                if (key == GLFW_KEY_BACKSPACE && !addressInput.isEmpty()) {
                    addressInput.deleteCharAt(addressInput.length() - 1);
                } else if (key == GLFW_KEY_ENTER) {
                    ConnectionResult result = network.connectToServerWithInput(addressInput,window,world,currentState,firstMouse,serverSocket,serverOut, otherPlayers,blocknameidmgr);
                    this.world = result.world();
                    this.currentState = result.currentState();
                    this.firstMouse = result.firstMouse();
                    this.serverSocket = result.socket();
                    this.serverOut = result.serverOut();
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


    private void loop() {
        double lastFrameTime = glfwGetTime();

        while (!glfwWindowShouldClose(window)) {
            double currentFrameTime = glfwGetTime();
            double deltaTime = (currentFrameTime - lastFrameTime);
            lastFrameTime = currentFrameTime;

            // 毎フレーム（またはサイズが変わったとき）現在のウィンドウサイズからアスペクト比を計算
            Matrix4d projection = new Matrix4d().perspective(
                    Math.toRadians(80.0f),
                    (double) windowWidth / windowHeight,
                    0.1f,
                    100.0f
            );

            if (currentState == GameState.MENU) {
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

                // --- 画面中央を基準にした座標を計算 ---
                double btnWidth = 400.0;
                double btnHeight = 50.0;
                double centerX = (windowWidth - btnWidth) / 2.0;

                // 画面の高さの中央から少し上・下に配置
                double singleY = (windowHeight / 2.0) - 45.0;
                double multiY  = (windowHeight / 2.0) + 15.0;

                // ボタンの位置を更新（※ButtonクラスにsetPosition等がない場合はフィールドに直接代入するかメソッドを追加してください）
                singlePlayerButton.setPosition(centerX, singleY);

                multiPlayerButton.setPosition(centerX,multiY);
                // ------------------------------------

                double[] mx = new double[1];
                double[] my = new double[1];
                glfwGetCursorPos(window, mx, my);

                // ボタンの描画と判定
                boolean isSingleHovered = singlePlayerButton.isHovered(mx[0], my[0]);
                renderer.renderButton(singlePlayerButton, isSingleHovered, windowWidth, windowHeight);

                // 文字の描画位置もボタンの中央付近に連動させる
                fontRenderer.drawText("SinglePlayer", (float)centerX + 110.0f, (float)singleY + 12.0f, 1.0f, windowWidth, windowHeight, new Vector3d(1.0f, 1.0f, 1.0f));

                boolean isMultiHovered = multiPlayerButton.isHovered(mx[0], my[0]);
                renderer.renderButton(multiPlayerButton, isMultiHovered, windowWidth, windowHeight);
                fontRenderer.drawText("MultiPlayer", (float)centerX + 125.0f, (float)multiY + 12.0f, 1.0f, windowWidth, windowHeight, new Vector3d(1.0f, 1.0f, 1.0f));

            } else if (currentState == GameState.ADDRESS_INPUT) {
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

                renderer.renderButton(multiPlayerButton, false, windowWidth, windowHeight);
                fontRenderer.drawText("Enter Server IP:", 440.0f, 220.0f, 1.0f, windowWidth, windowHeight, new Vector3d(1.0f, 1.0f, 1.0f));
                fontRenderer.drawText(addressInput.toString(), 460.0f, 342.0f, 1.0f, windowWidth, windowHeight, new Vector3d(1.0f, 1.0f, 1.0f));

            } else if (currentState == GameState.PLAYING) {
                camera.processInput(keys, deltaTime, world);

                double currentTime = glfwGetTime();
                if (currentTime - lastSendTime > 0.05) {
                    network.sendPosition(camera.pos.x, camera.pos.y, camera.pos.z, serverOut);
                    lastSendTime = currentTime;
                }

                renderer.render(world, camera, projection, otherPlayers, blocknameidmgr);

                renderer.renderCrosshair(windowWidth, windowHeight);
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



}