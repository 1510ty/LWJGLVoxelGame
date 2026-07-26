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
import org.lwjgl.glfw.GLFWVidMode;
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
import static org.lwjgl.vulkan.KHRSwapchain.VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
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

    private long renderPass;

    private long[] swapchainFramebuffers;


    //Vulkan
    private VkInstance vkInstance;
    private long surface;
    private VkPhysicalDevice physicalDevice;
    private VkDevice device;
    private long swapchain;

    private int swapchainImageFormat;
    private int swapchainImageWidth;
    private int swapchainImageHeight;
    private long[] swapchainImages;
    private long[] swapchainImageViews;
    long commandPool;


    VkQueue graphicsQueue;

    long[] imageAvailableSemaphores = new long[2]; // フレーム数（2つ）に合わせます
    long[] renderFinishedSemaphores;             // スワップチェーンの画像数に合わせて動的に作成します
    long[] renderFences = new long[2];
    VkCommandBuffer[] commandBuffers = new VkCommandBuffer[2];

    // それぞれ生成して、ループの中ではフレーム番号（0 または 1）で切り替える
    int currentFrame = 0;


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
        // OpenGLではなくVulkanを使うことをGLFWに伝える
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);

        window = glfwCreateWindow(WIDTH, HEIGHT, "Voxel Game Client (Vulkan)", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("GLFWウィンドウの作成に失敗しました。");
        }

        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
        camera = new Camera();

        final double[] mouseX = {0.0};
        final double[] mouseY = {0.0};

        // --- マウス移動コールバックの復活 ---
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

        // --- マウスクリックコールバックの復活 ---
        glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            if (currentState == GameState.MENU && button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS) {
                if (singlePlayerButton != null && singlePlayerButton.isHovered(mouseX[0], mouseY[0])) {
                    serverProcess = integratedservermgr.extractAndStartServer(worldFilePath, serverProcess);
                    ClientLauncher.WorldConnectionResult result = network.fetchWorldFromServer("localhost", 35565, serverSocket, serverOut, otherPlayers, blocknameidmgr);
                    this.world = result.world();
                    this.serverSocket = result.socket();
                    this.serverOut = result.serverOut();
                    currentState = GameState.PLAYING;
                    glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                    firstMouse = true;
                } else if (multiPlayerButton != null && multiPlayerButton.isHovered(mouseX[0], mouseY[0])) {
                    currentState = GameState.ADDRESS_INPUT;
                }
            } else if (currentState == GameState.PLAYING && action == GLFW_PRESS) {
                RaycastResult hit = camera.raycast(6.0f, world, camera);
                if (hit.hit) {
                    if (button == GLFW_MOUSE_BUTTON_LEFT) {
                        int airId = blocknameidmgr.getId("lwjglvoxelgame:air");
                        world.setBlock(hit.x, hit.y, hit.z, airId);
                        network.sendBlockChange(hit.x, hit.y, hit.z, airId, serverOut);
                    } else if (button == GLFW_MOUSE_BUTTON_RIGHT) {
                        int grassId = blocknameidmgr.getId("lwjglvoxelgame:grass_block");
                        world.setBlock(hit.prevX, hit.prevY, hit.prevZ, grassId);
                        network.sendBlockChange(hit.prevX, hit.prevY, hit.prevZ, grassId, serverOut);
                    }
                }
            }
        });

        // --- 文字入力コールバックの復活 ---
        glfwSetCharCallback(window, (w, codepoint) -> {
            if (currentState == GameState.ADDRESS_INPUT) {
                addressInput.append((char) codepoint);
            }
        });

        // --- キー入力コールバックの復活 ---
        glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
                if (currentState == GameState.ADDRESS_INPUT) {
                    currentState = GameState.MENU;
                } else {
                    glfwSetWindowShouldClose(window, true);
                }
            }

            if (currentState == GameState.ADDRESS_INPUT && (action == GLFW_PRESS || action == GLFW_REPEAT)) {
                if (key == GLFW_KEY_BACKSPACE && !addressInput.isEmpty()) {
                    addressInput.deleteCharAt(addressInput.length() - 1);
                } else if (key == GLFW_KEY_ENTER) {
                    ConnectionResult result = network.connectToServerWithInput(addressInput, window, world, currentState, firstMouse, serverSocket, serverOut, otherPlayers, blocknameidmgr);
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

        // --- ウィンドウを画面中央に配置する処理の復活 ---
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

        // --- Vulkanの初期化処理 ---
        initVulkan();

        glfwShowWindow(window);

        renderer = new Renderer(
                device,
                physicalDevice,
                renderPass,
                0 // subpass index
        );

        // 初期UIやフォントの準備
        fontRenderer = new FontRenderer(
                device,
                physicalDevice,
                renderPass,
                0, // subpass index
                commandPool,
                graphicsQueue,
                "/NotoSansJP-Regular.ttf"
        );
        singlePlayerButton = new Button(440, 260, 400, 50, "Single Player");
        multiPlayerButton  = new Button(440, 330, 400, 50, "Multi Player");
    }

    private void initVulkan() {
        try (MemoryStack stack = MemoryStack.stackPush()) {

            PointerBuffer ppEnabledLayerNames = stack.mallocPointer(1);
            ppEnabledLayerNames.put(stack.UTF8("VK_LAYER_KHRONOS_validation"));
            ppEnabledLayerNames.flip();

            // --- 1. Vulkanインスタンスの作成 ---
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
            createInfo.ppEnabledLayerNames(ppEnabledLayerNames);

            PointerBuffer pInstance = stack.mallocPointer(1);
            if (vkCreateInstance(createInfo, null, pInstance) != VK_SUCCESS) {
                throw new RuntimeException("Vulkanインスタンスの作成に失敗しました。");
            }
            vkInstance = new VkInstance(pInstance.get(0), createInfo);


            // --- 2. ウィンドウサーフェス（描画先）の作成 ---
            LongBuffer pSurface = stack.mallocLong(1);
            if (glfwCreateWindowSurface(vkInstance, window, null, pSurface) != VK_SUCCESS) {
                throw new RuntimeException("Vulkanウィンドウサーフェスの作成に失敗しました。");
            }
            surface = pSurface.get(0);


            // --- 3. 物理デバイス（GPU）の選択 ---
            IntBuffer pDeviceCount = stack.mallocInt(1);
            vkEnumeratePhysicalDevices(vkInstance, pDeviceCount, null);
            int deviceCount = pDeviceCount.get(0);

            if (deviceCount == 0) {
                throw new RuntimeException("Vulkanに対応したGPUが見つかりませんでした。");
            }

            org.lwjgl.PointerBuffer pPhysicalDevices = stack.mallocPointer(deviceCount);
            vkEnumeratePhysicalDevices(vkInstance, pDeviceCount, pPhysicalDevices);
            physicalDevice = new VkPhysicalDevice(pPhysicalDevices.get(0), vkInstance);


            // --- 4. 論理デバイスとキューの作成 ---
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

            org.lwjgl.PointerBuffer pQueue = stack.mallocPointer(1);
            vkGetDeviceQueue(device, 0, 0, pQueue);
            graphicsQueue = new VkQueue(pQueue.get(0), device);


            // --- 5. スワップチェーン、レンダーパス、フレームバッファの作成 ---
            createSwapchain();

            VkAttachmentDescription.Buffer colorAttachment = VkAttachmentDescription.calloc(1, stack);
            colorAttachment.format(swapchainImageFormat);
            colorAttachment.samples(VK_SAMPLE_COUNT_1_BIT);
            colorAttachment.loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR);
            colorAttachment.storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            colorAttachment.stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE);
            colorAttachment.stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE);
            colorAttachment.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            colorAttachment.finalLayout(org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);

            VkAttachmentReference.Buffer colorAttachmentRef = VkAttachmentReference.calloc(1, stack);
            colorAttachmentRef.attachment(0);
            colorAttachmentRef.layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

            VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack);
            subpass.pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS);
            subpass.colorAttachmentCount(1);
            subpass.pColorAttachments(colorAttachmentRef);

            VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
                    .pAttachments(colorAttachment)
                    .pSubpasses(subpass);

            LongBuffer pRenderPass = stack.mallocLong(1);
            if (vkCreateRenderPass(device, renderPassInfo, null, pRenderPass) != VK_SUCCESS) {
                throw new RuntimeException("レンダーパスの作成に失敗しました。");
            }
            renderPass = pRenderPass.get(0);

            createFramebuffers();


            // --- 6. コマンドプールとコマンドバッファの作成 ---
            VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                    .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                    .queueFamilyIndex(0);

            LongBuffer pCommandPool = stack.mallocLong(1);
            if (vkCreateCommandPool(device, poolInfo, null, pCommandPool) != VK_SUCCESS) {
                throw new RuntimeException("コマンドプールの作成に失敗しました。");
            }
            commandPool = pCommandPool.get(0);

            VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                    .commandPool(commandPool)
                    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(2);

            // ※ここでポインタ用のバッファを生成し、コマンドバッファを2つ割り当てます
            PointerBuffer pCommandBuffers = stack.mallocPointer(2);
            if (vkAllocateCommandBuffers(device, allocInfo, pCommandBuffers) != VK_SUCCESS) {
                throw new RuntimeException("コマンドバッファの割り当てに失敗しました。");
            }

            commandBuffers = new VkCommandBuffer[2];
            for (int i = 0; i < 2; i++) {
                commandBuffers[i] = new VkCommandBuffer(pCommandBuffers.get(i), device);
            }


// --- 7. 同期オブジェクト（画像取得セマフォとフレームフェンス）の作成 ---
            VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);

            VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
                    .flags(VK_FENCE_CREATE_SIGNALED_BIT);

            LongBuffer pSemaphore = stack.mallocLong(1);
            LongBuffer pFence = stack.mallocLong(1);

            if (imageAvailableSemaphores == null) imageAvailableSemaphores = new long[2];
            if (renderFences == null) renderFences = new long[2];

            for (int i = 0; i < 2; i++) {
                vkCreateSemaphore(device, semaphoreInfo, null, pSemaphore);
                imageAvailableSemaphores[i] = pSemaphore.get(0);

                vkCreateFence(device, fenceInfo, null, pFence);
                renderFences[i] = pFence.get(0);
            }




        }
    }

    private void createSwapchain() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSurfaceCapabilitiesKHR capabilities = VkSurfaceCapabilitiesKHR.calloc(stack);
            org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, surface, capabilities);

            swapchainImageWidth = capabilities.currentExtent().width();
            swapchainImageHeight = capabilities.currentExtent().height();

            if (swapchainImageWidth == 0 || swapchainImageHeight == 0) {
                return;
            }

            IntBuffer pFormatCount = stack.mallocInt(1);
            org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, pFormatCount, null);
            int formatCount = pFormatCount.get(0);

            org.lwjgl.vulkan.VkSurfaceFormatKHR.Buffer formats = org.lwjgl.vulkan.VkSurfaceFormatKHR.malloc(formatCount, stack);
            org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, pFormatCount, formats);

            int imageFormat = VK_FORMAT_B8G8R8A8_UNORM;
            for (int i = 0; i < formats.capacity(); i++) {
                if (formats.get(i).format() == VK_FORMAT_B8G8R8A8_UNORM) {
                    imageFormat = formats.get(i).format();
                    break;
                }
            }
            swapchainImageFormat = imageFormat;

            int imageCount = capabilities.minImageCount() + 1;
            if (capabilities.maxImageCount() > 0 && imageCount > capabilities.maxImageCount()) {
                imageCount = capabilities.maxImageCount();
            }

            VkSwapchainCreateInfoKHR swapchainCreateInfo = VkSwapchainCreateInfoKHR.calloc(stack)
                    .sType(org.lwjgl.vulkan.KHRSwapchain.VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
                    .surface(surface)
                    .minImageCount(imageCount)
                    .imageFormat(imageFormat)
                    .imageColorSpace(org.lwjgl.vulkan.KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR)
                    .imageExtent(extent -> extent.set(swapchainImageWidth, swapchainImageHeight))
                    .imageArrayLayers(1)
                    .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                    .imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .preTransform(capabilities.currentTransform())
                    .compositeAlpha(org.lwjgl.vulkan.KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                    .presentMode(org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_FIFO_KHR)
                    .clipped(true)
                    .oldSwapchain(VK_NULL_HANDLE);

            LongBuffer pSwapchain = stack.mallocLong(1);
            if (org.lwjgl.vulkan.KHRSwapchain.vkCreateSwapchainKHR(device, swapchainCreateInfo, null, pSwapchain) != VK_SUCCESS) {
                throw new RuntimeException("スワップチェーンの作成に失敗しました。");
            }
            swapchain = pSwapchain.get(0);

            IntBuffer pImageCount = stack.mallocInt(1);
            org.lwjgl.vulkan.KHRSwapchain.vkGetSwapchainImagesKHR(device, swapchain, pImageCount, null);
            int actualImageCount = pImageCount.get(0);

            LongBuffer pImages = stack.mallocLong(actualImageCount);
            org.lwjgl.vulkan.KHRSwapchain.vkGetSwapchainImagesKHR(device, swapchain, pImageCount, pImages);

            swapchainImages = new long[actualImageCount];
            for (int i = 0; i < actualImageCount; i++) {
                swapchainImages[i] = pImages.get(i);
            }

            swapchainImageViews = new long[actualImageCount];
            VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                    .format(imageFormat);
            viewInfo.components()
                    .r(VK_COMPONENT_SWIZZLE_IDENTITY)
                    .g(VK_COMPONENT_SWIZZLE_IDENTITY)
                    .b(VK_COMPONENT_SWIZZLE_IDENTITY)
                    .a(VK_COMPONENT_SWIZZLE_IDENTITY);
            viewInfo.subresourceRange()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1);

            LongBuffer pImageView = stack.mallocLong(1);
            for (int i = 0; i < actualImageCount; i++) {
                viewInfo.image(swapchainImages[i]);
                if (vkCreateImageView(device, viewInfo, null, pImageView) != VK_SUCCESS) {
                    throw new RuntimeException("イメージビューの作成に失敗しました。");
                }
                swapchainImageViews[i] = pImageView.get(0);
            }

            // --- 7. スワップチェーンの画像数に合わせた「描画完了セマフォ」の作成 ---
            if (renderFinishedSemaphores != null) {
                for (long sem : renderFinishedSemaphores) vkDestroySemaphore(device, sem, null);
            }

            renderFinishedSemaphores = new long[actualImageCount];

            VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);
            LongBuffer pSemaphore = stack.mallocLong(1);

            // 描画完了セマフォはスワップチェーンの画像数分作る
            for (int i = 0; i < actualImageCount; i++) {
                vkCreateSemaphore(device, semaphoreInfo, null, pSemaphore);
                renderFinishedSemaphores[i] = pSemaphore.get(0);
            }


        }
    }

    private void createFramebuffers() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int actualImageCount = swapchainImageViews.length;
            swapchainFramebuffers = new long[actualImageCount];
            LongBuffer pFramebuffer = stack.mallocLong(1);

            VkFramebufferCreateInfo framebufferInfo = VkFramebufferCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                    .renderPass(renderPass) // ← ここではすでに完成した renderPass が使える！
                    .width(swapchainImageWidth)
                    .height(swapchainImageHeight)
                    .layers(1);

            LongBuffer pAttachments = stack.mallocLong(1);
            for (int i = 0; i < actualImageCount; i++) {
                pAttachments.put(0, swapchainImageViews[i]);
                framebufferInfo.pAttachments(pAttachments);

                if (vkCreateFramebuffer(device, framebufferInfo, null, pFramebuffer) != VK_SUCCESS) {
                    throw new RuntimeException("フレームバッファの作成に失敗しました。");
                }
                swapchainFramebuffers[i] = pFramebuffer.get(0);
            }
        }
    }

    private void cleanupSwapchain() {
        if (swapchainFramebuffers != null) {
            for (long framebuffer : swapchainFramebuffers) {
                vkDestroyFramebuffer(device, framebuffer, null);
            }
        }
        if (swapchainImageViews != null) {
            for (long imageView : swapchainImageViews) {
                vkDestroyImageView(device, imageView, null);
            }
        }
        if (swapchain != 0) {
            org.lwjgl.vulkan.KHRSwapchain.vkDestroySwapchainKHR(device, swapchain, null);
        }
    }

    private void loop() {
        double lastFrameTime = glfwGetTime();

        while (!glfwWindowShouldClose(window)) {

            // ウィンドウが最小化されている（幅か高さが0）ときは、戻るまでイベントを待つ
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer width = stack.mallocInt(1);
                IntBuffer height = stack.mallocInt(1);
                glfwGetFramebufferSize(window, width, height);
                while (width.get(0) == 0 || height.get(0) == 0) {
                    glfwGetFramebufferSize(window, width, height);
                    glfwWaitEvents();
                    if (glfwWindowShouldClose(window)) {
                        return;
                    }
                }
            }
            long waitSemaphore = imageAvailableSemaphores[currentFrame];

            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer pImageIndex = stack.mallocInt(1);

                // ★ 1. 画像を取得する「前」に、前のフレームのGPU処理の完了をフェンスで待つ！
                vkWaitForFences(device, renderFences[currentFrame], true, -1L);
                vkResetFences(device, renderFences[currentFrame]);

                // ★ 2. その後で安全に画像を取得する
                int vkResult = KHRSwapchain.vkAcquireNextImageKHR(
                        device, swapchain, -1, waitSemaphore, VK_NULL_HANDLE, pImageIndex);

                // スワップチェーンが古くなっていたらの再作成処理はそのまま
                if (vkResult == org.lwjgl.vulkan.KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR || vkResult == org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR) {
                    vkDeviceWaitIdle(device);
                    cleanupSwapchain();
                    createSwapchain();
                    createFramebuffers();
                    continue;
                } else if (vkResult != VK_SUCCESS) {
                    throw new RuntimeException("スワップチェーン画像の取得に失敗しました。");
                }

                double currentFrameTime = glfwGetTime();
                double deltaTime = (currentFrameTime - lastFrameTime);
                lastFrameTime = currentFrameTime;

                int imageIndex = pImageIndex.get(0);

                // ★ 3. 取得した画像インデックス（imageIndex）に対応する描画完了セマフォを取得する
                long signalSemaphore = renderFinishedSemaphores[imageIndex];

                VkCommandBuffer commandBuffer = commandBuffers[currentFrame];
                vkResetCommandBuffer(commandBuffer, 0);


                VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
                vkBeginCommandBuffer(commandBuffer, beginInfo);

                // --- 1. レンダーパスの開始 ---
                VkRenderPassBeginInfo renderPassInfo = VkRenderPassBeginInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                        .renderPass(renderPass)
                        .framebuffer(swapchainFramebuffers[imageIndex]);
                renderPassInfo.renderArea().offset().set(0, 0);
                renderPassInfo.renderArea().extent().set(swapchainImageWidth, swapchainImageHeight);

                VkClearValue.Buffer clearValues = VkClearValue.calloc(1, stack);
                clearValues.get(0).color().float32(0, 0.2f).float32(1, 0.4f).float32(2, 0.6f).float32(3, 1.0f);
                renderPassInfo.pClearValues(clearValues);

                vkCmdBeginRenderPass(commandBuffer, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);

                // --- 画面状態ごとの描画 ---
                if (currentState == GameState.MENU) {
                    double[] mx = new double[1];
                    double[] my = new double[1];
                    glfwGetCursorPos(window, mx, my);

                    boolean isSingleHovered = singlePlayerButton.isHovered(mx[0], my[0]);

                    fontRenderer.drawText(
                            commandBuffer,
                            "SinglePlayer",
                            470.0f, 272.0f, 1.0,
                            swapchainImageWidth, swapchainImageHeight,
                            new Vector3d(1.0f, 1.0f, 1.0f)
                    );

                    boolean isMultiHovered = multiPlayerButton.isHovered(mx[0], my[0]);
                    fontRenderer.drawText(
                            commandBuffer,
                            "MultiPlayer",
                            485.0f, 342.0f, 1.0,
                            swapchainImageWidth, swapchainImageHeight,
                            new Vector3d(1.0f, 1.0f, 1.0f)
                    );
                    IO.println("とってるわぼけ");

                } else if (currentState == GameState.ADDRESS_INPUT) {
                    fontRenderer.drawText(
                            commandBuffer,
                            "Enter Server IP:",
                            440.0f, 220.0f, 1.0,
                            swapchainImageWidth, swapchainImageHeight,
                            new Vector3d(1.0f, 1.0f, 1.0f)
                    );
                    fontRenderer.drawText(
                            commandBuffer,
                            addressInput.toString(),
                            460.0f, 342.0f, 1.0,
                            swapchainImageWidth, swapchainImageHeight,
                            new Vector3d(1.0f, 1.0f, 1.0f)
                    );
                } else if (currentState == GameState.PLAYING) {
                    camera.processInput(keys, deltaTime, world);

                    double currentTime = glfwGetTime();
                    if (currentTime - lastSendTime > 0.05) {
                        network.sendPosition(camera.pos.x, camera.pos.y, camera.pos.z, serverOut);
                        lastSendTime = currentTime;
                    }

                    if (world != null && renderer != null) {
                        Matrix4d projection = new Matrix4d().perspective(
                                Math.toRadians(70.0),
                                (double) swapchainImageWidth / swapchainImageHeight,
                                0.1, 1000.0
                        ).scale(1.0, -1.0, 1.0); // Y軸をVulkan用に反転させる

                        renderer.render(
                                commandBuffer,
                                world,
                                camera,
                                projection,
                                otherPlayers,
                                blocknameidmgr,
                                swapchainImageWidth,
                                swapchainImageHeight
                        );
                    }
                }

                vkCmdEndRenderPass(commandBuffer);
                vkEndCommandBuffer(commandBuffer);

                // --- 送信 (Submit) ---
                VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO);
                submitInfo.waitSemaphoreCount(1);
                submitInfo.pWaitSemaphores(stack.longs(waitSemaphore));
                submitInfo.pWaitDstStageMask(stack.ints(org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT));
                submitInfo.pCommandBuffers(stack.pointers(commandBuffer.address()));
                submitInfo.pSignalSemaphores(stack.longs(signalSemaphore));

                vkQueueSubmit(graphicsQueue, submitInfo, renderFences[currentFrame]);

                // --- 画面提示 (Present) ---
                VkPresentInfoKHR presentInfo = VkPresentInfoKHR.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR);
                presentInfo.pWaitSemaphores(stack.longs(signalSemaphore));
                presentInfo.swapchainCount(1);
                presentInfo.pSwapchains(stack.longs(swapchain));
                presentInfo.pImageIndices(stack.ints(imageIndex));

                int presentResult = org.lwjgl.vulkan.KHRSwapchain.vkQueuePresentKHR(graphicsQueue, presentInfo);

                if (presentResult == org.lwjgl.vulkan.KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR || presentResult == org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR) {
                    vkDeviceWaitIdle(device);
                    cleanupSwapchain();
                    createSwapchain();
                    createFramebuffers();
                }
            }

            glfwPollEvents();
            currentFrame = (currentFrame + 1) % 2;
        }

        vkDeviceWaitIdle(device);
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

        // ★ ここに renderer の解放を追加する
        if (renderer != null) {
            renderer.cleanup();
        }

        // --- Vulkanのリソース解放 ---
        if (device != null) {
            // 念のためGPUの処理がすべて終わるのを待つ
            vkDeviceWaitIdle(device);

            // 1. 同期オブジェクト（セマフォ・フェンス）の破棄
            if (imageAvailableSemaphores != null) {
                for (long semaphore : imageAvailableSemaphores) {
                    if (semaphore != 0) vkDestroySemaphore(device, semaphore, null);
                }
            }
            if (renderFinishedSemaphores != null) {
                for (long semaphore : renderFinishedSemaphores) {
                    if (semaphore != 0) vkDestroySemaphore(device, semaphore, null);
                }
            }
            if (renderFences != null) {
                for (long fence : renderFences) {
                    if (fence != 0) vkDestroyFence(device, fence, null);
                }
            }

            // 2. コマンドプールの破棄（中のコマンドバッファも一緒に解放されます）
            if (commandPool != 0) {
                vkDestroyCommandPool(device, commandPool, null);
            }

            // 3. レンダーパスの破棄
            if (renderPass != 0) {
                vkDestroyRenderPass(device, renderPass, null);
            }

            // 4. スワップチェーンやフレームバッファ、イメージビューの破棄
            cleanupSwapchain();

            // 5. 論理デバイスの破棄
            vkDestroyDevice(device, null);
        }

        // 6. サーフェスの破棄（※必ずスワップチェーンやデバイスを消した後に呼ぶ）
        if (surface != 0 && vkInstance != null) {
            org.lwjgl.vulkan.KHRSurface.vkDestroySurfaceKHR(vkInstance, surface, null);
        }

        // 7. インスタンスの破棄
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