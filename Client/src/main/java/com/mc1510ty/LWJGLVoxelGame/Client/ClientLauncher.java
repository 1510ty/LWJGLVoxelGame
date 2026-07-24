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
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.io.*;
import java.net.Socket;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.vulkan.KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.glfw.GLFWVulkan.*;
import static org.lwjgl.vulkan.VK14.VK_API_VERSION_1_4;


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

    private final java.util.Map<Long, Vector3d> otherPlayers = new java.util.concurrent.ConcurrentHashMap<>();

    private double lastSendTime = 0;


    //Vulkan
    private VkInstance vkInstance;
    private long surface;
    private VkPhysicalDevice physicalDevice;
    private VkDevice device;
    private long swapchain;


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
        // ★ここがポイント：OpenGLではなくVulkanを使うことをGLFWに伝える
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);

        window = glfwCreateWindow(WIDTH, HEIGHT, "Voxel Game Client (Vulkan)", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("GLFWウィンドウの作成に失敗しました。");
        }

        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
        camera = new Camera();

        final double[] mouseX = {0.0};
        final double[] mouseY = {0.0};

//        glfwSetCursorPosCallback(window, (w, xpos, ypos) -> {
//            mouseX[0] = xpos;
//            mouseY[0] = ypos;
//
//            if (currentState == GameState.PLAYING) {
//                if (firstMouse) {
//                    lastX = xpos;
//                    lastY = ypos;
//                    firstMouse = false;
//                }
//                double xoffset = (xpos - lastX);
//                double yoffset = (lastY - ypos);
//                lastX = xpos;
//                lastY = ypos;
//
//                camera.processMouseMovement(xoffset, yoffset);
//            }
//        });
//
//        glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
//            if (currentState == GameState.MENU && button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS) {
//                if (singlePlayerButton != null && singlePlayerButton.isHovered(mouseX[0], mouseY[0])) {
//                    serverProcess = integratedservermgr.extractAndStartServer(worldFilePath,serverProcess);
//                    ClientLauncher.WorldConnectionResult result = network.fetchWorldFromServer("localhost", 35565,serverSocket,serverOut, otherPlayers, blocknameidmgr);
//                    this.world = result.world();
//                    this.serverSocket = result.socket();
//                    this.serverOut = result.serverOut();
//                    currentState = GameState.PLAYING;
//                    glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
//                    firstMouse = true;
//                } else if (multiPlayerButton != null && multiPlayerButton.isHovered(mouseX[0], mouseY[0])) {
//                    // マルチプレイボタンを押したらアドレス入力画面へ切り替える
//                    currentState = GameState.ADDRESS_INPUT;
//                }
//            } else if (currentState == GameState.PLAYING && action == GLFW_PRESS) {
//                RaycastResult hit = camera.raycast(6.0f,world,camera);
//                if (hit.hit) {
//                    if (button == GLFW_MOUSE_BUTTON_LEFT) {
//                        // 左クリック：問答無用で空気（air）にする
//                        int airId = blocknameidmgr.getId("lwjglvoxelgame:air");
//                        world.setBlock(hit.x, hit.y, hit.z, airId);
//                        network.sendBlockChange(hit.x, hit.y, hit.z, airId, serverOut);
//                    } else if (button == GLFW_MOUSE_BUTTON_RIGHT) {
//                        // 右クリック：現状は草ブロック（grass_block）にする
//                        int grassId = blocknameidmgr.getId("lwjglvoxelgame:grass_block");
//                        System.out.println("取得したgrassId: " + grassId); // ここで 0 になっていないか確認！
//                        world.setBlock(hit.prevX, hit.prevY, hit.prevZ, grassId);
//                        network.sendBlockChange(hit.prevX, hit.prevY, hit.prevZ, grassId, serverOut);
//                    }
//                }
//            }
//        });
//
//        // アドレス入力中の文字入力を受け取るコールバック
//        glfwSetCharCallback(window, (w, codepoint) -> {
//            if (currentState == GameState.ADDRESS_INPUT) {
//                addressInput.append((char) codepoint);
//            }
//        });
//
//        glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {
//            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
//                if (currentState == GameState.ADDRESS_INPUT) {
//                    currentState = GameState.MENU;
//                } else {
//                    glfwSetWindowShouldClose(window, true);
//                }
//            }
//
//            // アドレス入力中の特殊キー処理（バックスペースとエンター）
//            if (currentState == GameState.ADDRESS_INPUT && (action == GLFW_PRESS || action == GLFW_REPEAT)) {
//                if (key == GLFW_KEY_BACKSPACE && !addressInput.isEmpty()) {
//                    addressInput.deleteCharAt(addressInput.length() - 1);
//                } else if (key == GLFW_KEY_ENTER) {
//                    ConnectionResult result = network.connectToServerWithInput(addressInput,window,world,currentState,firstMouse,serverSocket,serverOut, otherPlayers,blocknameidmgr);
//                    this.world = result.world();
//                    this.currentState = result.currentState();
//                    this.firstMouse = result.firstMouse();
//                    this.serverSocket = result.socket();
//                    this.serverOut = result.serverOut();
//                }
//            }
//
//            if (key >= 0 && key < keys.length) {
//                if (action == GLFW_PRESS) {
//                    keys[key] = true;
//                } else if (action == GLFW_RELEASE) {
//                    keys[key] = false;
//                }
//            }
//        });
//        try (MemoryStack stack = MemoryStack.stackPush()) {
//            IntBuffer pWidth = stack.mallocInt(1);
//            IntBuffer pHeight = stack.mallocInt(1);
//            glfwGetWindowSize(window, pWidth, pHeight);
//            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
//            glfwSetWindowPos(
//                    window,
//                    (vidmode.width() - pWidth.get(0)) / 2,
//                    (vidmode.height() - pHeight.get(0)) / 2
//            );
//        }

        // --- Vulkanの初期化処理 ---
        initVulkan();

        glfwShowWindow(window);

        // 初期UIやフォントの準備
//        fontRenderer = new FontRenderer("/NotoSansJP-Regular.ttf");
//        singlePlayerButton = new Button(440, 260, 400, 50, "Single Player");
//        multiPlayerButton  = new Button(440, 330, 400, 50, "Multi Player");
    }

    private void initVulkan() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // 1. Vulkanインスタンスの作成
            VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                    .pApplicationName(stack.UTF8("Voxel Game Client"))
                    .applicationVersion(VK_MAKE_VERSION(1, 0, 0))
                    .pEngineName(stack.UTF8("No Engine"))
                    .engineVersion(VK_MAKE_VERSION(1, 0, 0))
                    .apiVersion(VK_API_VERSION_1_4);

            VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                    .pApplicationInfo(appInfo);

            // GLFWが必要とするVulkan拡張機能を取得
            org.lwjgl.PointerBuffer exts = glfwGetRequiredInstanceExtensions();
            if (exts == null) {
                throw new RuntimeException("Vulkanに対応しているウィンドウ拡張機能が見つかりません。");
            }

            createInfo.ppEnabledExtensionNames(exts);

            PointerBuffer pInstance = stack.mallocPointer(1);
            if (vkCreateInstance(createInfo, null, pInstance) != VK_SUCCESS) {
                throw new RuntimeException("Vulkanインスタンスの作成に失敗しました。");
            }
            vkInstance = new VkInstance(pInstance.get(0), createInfo);

            // 2. ウィンドウサーフェス（描画先）の作成
            LongBuffer pSurface = stack.mallocLong(1);
            if (glfwCreateWindowSurface(vkInstance, window, null, pSurface) != VK_SUCCESS) {
                throw new RuntimeException("Vulkanウィンドウサーフェスの作成に失敗しました。");
            }
            surface = pSurface.get(0);

            IntBuffer pDeviceCount = stack.mallocInt(1);
            vkEnumeratePhysicalDevices(vkInstance, pDeviceCount, null);
            int deviceCount = pDeviceCount.get(0);

            if (deviceCount == 0) {
                throw new RuntimeException("Vulkanに対応したGPUが見つかりませんでした。");
            }

            org.lwjgl.PointerBuffer pPhysicalDevices = stack.mallocPointer(deviceCount);
            vkEnumeratePhysicalDevices(vkInstance, pDeviceCount, pPhysicalDevices);

            physicalDevice = new VkPhysicalDevice(pPhysicalDevices.get(0), vkInstance);

            // --- 4. 論理デバイスの作成 ---
            // キューファミリー（グラフィックス用）を探す処理やインデックス特定が必要ですが、
            // 簡易的にキュー作成情報を構築します。
            float[] queuePriorities = { 1.0f };
            VkDeviceQueueCreateInfo.Buffer queueCreateInfo = VkDeviceQueueCreateInfo.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                    .queueFamilyIndex(0) // ※本来はサーフェスに対応したファミリーを探すべきですが、多くの場合0です
                    .pQueuePriorities(stack.floats(1.0f));

            // スワップチェーン拡張機能（VK_KHR_swapchain）を有効にする
            org.lwjgl.PointerBuffer extensions = stack.mallocPointer(1);
            extensions.put(stack.UTF8(VK_KHR_SWAPCHAIN_EXTENSION_NAME));
            extensions.flip();

            VkDeviceCreateInfo deviceCreateInfo = VkDeviceCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                    .pQueueCreateInfos(queueCreateInfo)
                    .ppEnabledExtensionNames(extensions);

            org.lwjgl.PointerBuffer pDevice = stack.mallocPointer(1);
            if (vkCreateDevice(physicalDevice, deviceCreateInfo, null, pDevice) != VK_SUCCESS) {
                throw new RuntimeException("Vulkan論理デバイスの作成に失敗しました。");
            }
            device = new VkDevice(pDevice.get(0), physicalDevice, deviceCreateInfo);
        }
    }



    private void loop() {
        Matrix4d projection = new Matrix4d().perspective(Math.toRadians(45.0f), (double) WIDTH / HEIGHT, 0.1f, 100.0f);
        double lastFrameTime = glfwGetTime();

        while (!glfwWindowShouldClose(window)) {
            double currentFrameTime = glfwGetTime();
            double deltaTime = (currentFrameTime - lastFrameTime);
            lastFrameTime = currentFrameTime;

            // --- Vulkanの描画フレーム処理（背景クリアなど） ---
            // TODO: スワップチェーンからの画像取得とコマンドバッファの実行をここに記述します

//            if (currentState == GameState.MENU) {
//                double[] mx = new double[1];
//                double[] my = new double[1];
//                glfwGetCursorPos(window, mx, my);
//
//                boolean isSingleHovered = singlePlayerButton.isHovered(mx[0], my[0]);
//                // renderer.renderButton(...) -> 将来的にVulkan版に置き換え
//                fontRenderer.drawText("SinglePlayer", 470.0f, 272.0f, 1.0f, WIDTH, HEIGHT, new Vector3d(1.0f, 1.0f, 1.0f));
//
//                boolean isMultiHovered = multiPlayerButton.isHovered(mx[0], my[0]);
//                fontRenderer.drawText("MultiPlayer", 485.0f, 342.0f, 1.0f, WIDTH, HEIGHT, new Vector3d(1.0f, 1.0f, 1.0f));
//
//            } else if (currentState == GameState.ADDRESS_INPUT) {
//                fontRenderer.drawText("Enter Server IP:", 440.0f, 220.0f, 1.0f, WIDTH, HEIGHT, new Vector3d(1.0f, 1.0f, 1.0f));
//                fontRenderer.drawText(addressInput.toString(), 460.0f, 342.0f, 1.0f, WIDTH, HEIGHT, new Vector3d(1.0f, 1.0f, 1.0f));
//
//            } else if (currentState == GameState.PLAYING) {
//                camera.processInput(keys, deltaTime, world);
//
//                double currentTime = glfwGetTime();
//                if (currentTime - lastSendTime > 0.05) {
//                    network.sendPosition(camera.pos.x, camera.pos.y, camera.pos.z, serverOut);
//                    lastSendTime = currentTime;
//                }
//                // renderer.render(...) -> Vulkan版へ置き換え予定
//            }

            // 画面の更新（VulkanではvkQueuePresentKHRなどを使いますが、ウィンドウのイベント処理を回します）
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

        // --- Vulkanのリソース解放 ---
        if (surface != 0) {
            org.lwjgl.vulkan.KHRSurface.vkDestroySurfaceKHR(vkInstance, surface, null);
        }
        if (vkInstance != null) {
            vkDestroyInstance(vkInstance, null);
        }

        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();

        GLFWErrorCallback callback = glfwSetErrorCallback(null);
        if (callback != null) {
            callback.free();
        }
    }



}