package com.mc1510ty.LWJGLVoxelGame.Client.vulkan;

import org.lwjgl.vulkan.*;

import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.glfw.GLFW.glfwWaitEvents;
import static org.lwjgl.vulkan.KHRSwapchain.vkDestroySwapchainKHR;
import static org.lwjgl.vulkan.VK10.*;

public class Vulkan {

    public long window;

    //戻り値のセット
    public static class SwapchainBundle {
        public long swapchainHandle;
        public long[] images;
        public int imageFormat;
        public int width;
        public int height;
    }

    public static class SyncObjects {
        public long[] imageAvailableSemaphores;
        public long[] renderFinishedSemaphores;
        public long[] inFlightFences;
    }

    public static class PipelineBundle {
        public long pipeline;
        public long pipelineLayout;
    }


    //定数
    //バリデーションレイヤー(エラーとかが見やすくなるやつ)の名前の定義
    public static final String VALIDATION_LAYER = "VK_LAYER_KHRONOS_validation";
    //バリデーションレイヤーをONにするか
    public static final boolean ENABLE_VALIDATION_LAYERS = true; // 開発中はtrueにする

    //Vulkanインスタンス
    public VkInstance instance;
    public long surface;
    public VkPhysicalDevice physicalDevice;
    public VkDevice device;
    public VkQueue graphicsQueue;
    int graphicsQueueFamilyIndex;
    //スワップチェーン類
        public long swapchain;
        public long[] swapchainImages;
        public int swapchainImageFormat;
        public int width;
        public int height;
    public long[] imageviews;
    public long renderpass;
    public long[] framebuffers;
    public long commandpool;
    public long[] commandbuffers;
        public long[] imageAvailableSemaphores;
        public long[] renderFinishedSemaphores;
        public long[] inFlightFences;
    public long vertShaderModule;
    public long fragShaderModule;
    public long graphicspipeline;
    public long pipelineLayout;

    public long[] imagesInFlight;

    public void initVulkan(long window) {
        long startTime = System.nanoTime();
        IO.println("Vulkanの初期化を開始します");

        this.window = window;

        instance = new createVulkanInstance().createVulkanInstance(ENABLE_VALIDATION_LAYERS, VALIDATION_LAYER);
        surface = new createSurface().createSurface(instance,window);
        physicalDevice = new selectPhysicalDevice().select(instance,surface);
        device = new createDevice().create(physicalDevice,surface);
        getVkQueue queueGetter = new getVkQueue();
        graphicsQueue = queueGetter.getGraphicsQueue(device, physicalDevice);
        graphicsQueueFamilyIndex = queueGetter.findGraphicsQueueFamily(physicalDevice);
        createSwapchainAndAttachments(VK_NULL_HANDLE);
        renderpass = new createRenderPass().create(device, swapchainImageFormat);
        framebuffers = new createFramebuffer().create(device, renderpass, imageviews, width, height);
        commandpool = new createCommandPool().create(device,graphicsQueueFamilyIndex);
        commandbuffers = new createCommandBuffer().create(device,commandpool,swapchainImages.length);
        SyncObjects syncobjects = new createFenceAndSemaphore().create(device,2,swapchainImages.length);
        this.imageAvailableSemaphores = syncobjects.imageAvailableSemaphores;
        this.renderFinishedSemaphores = syncobjects.renderFinishedSemaphores;
        this.inFlightFences = syncobjects.inFlightFences;
        vertShaderModule = new createShaderModule().create(device, "shaders/vert.spv");
        fragShaderModule = new createShaderModule().create(device, "shaders/frag.spv");
        PipelineBundle pipeBundle = new createGraphicsPipeline().create(device, renderpass, vertShaderModule, fragShaderModule, width, height);
        this.graphicspipeline = pipeBundle.pipeline;
        this.pipelineLayout = pipeBundle.pipelineLayout;


        long endTime = System.nanoTime();
        double elapsedSeconds = (endTime - startTime) / 1_000_000_000.0;
        IO.println("Vulkanの初期化が正常に完了しました: " + elapsedSeconds + "秒");
    }

    public void recreateSwapchain() {
        long startTime = System.nanoTime();
        IO.println("スワップチェーン類の(再)生成を開始します");

        int[] width = new int[1];
        int[] height = new int[1];
        glfwGetFramebufferSize(window, width, height);

        boolean zerodatta = false;
        if (width[0] == 0 || height[0] == 0) {
            IO.println("ウィンドウサイズが0なのでそれ以上になるまで待ちます...");
            zerodatta = true;
        }

        long startTime1 = System.nanoTime();
        while (width[0] == 0 || height[0] == 0) {
            glfwGetFramebufferSize(window, width, height);
            glfwWaitEvents();
        }
        long endTime1 = System.nanoTime();
        double elapsedSeconds1 = (endTime1 - startTime1);

        if (zerodatta) {
            IO.println("ウィンドウサイズが1以上になりました");
            IO.println("待機した時間: " + elapsedSeconds1 + "秒");
        }

        vkDeviceWaitIdle(device);

        // 古いフレームバッファーを破棄
        if (framebuffers != null) {
            for (long framebuffer : framebuffers) {
                vkDestroyFramebuffer(device, framebuffer, null);
            }
        }

        // 古いイメージビューを破棄
        if (imageviews != null) {
            for (long imageView : imageviews) {
                vkDestroyImageView(device, imageView, null);
            }
        }

        // ★ 古いグラフィックスパイプラインとレイアウトを破棄
        if (graphicspipeline != 0) {
            vkDestroyPipeline(device, graphicspipeline, null);
        }
        if (pipelineLayout != 0) {
            vkDestroyPipelineLayout(device, pipelineLayout, null);
        }

        long oldSwapchainHandle = this.swapchain;

        // 新しいスワップチェーン等を作成
        createSwapchainAndAttachments(oldSwapchainHandle);

        // 古いスワップチェーン本体を破棄
        vkDestroySwapchainKHR(device, oldSwapchainHandle, null);

        // ★ 新しいサイズでグラフィックスパイプラインを作り直す
        PipelineBundle pipeBundle = new createGraphicsPipeline().create(device, renderpass, vertShaderModule, fragShaderModule, this.width, this.height);
        this.graphicspipeline = pipeBundle.pipeline;
        this.pipelineLayout = pipeBundle.pipelineLayout;


        long endTime = System.nanoTime();
        double elapsedSeconds = (endTime - startTime) / 1_000_000_000.0;

        double zissai = (elapsedSeconds - elapsedSeconds1) / 1_000_000_000.0;

        IO.println("スワップチェーン類の再生成完了: " + elapsedSeconds + "秒、実際の処理時間: " + zissai + "秒");
    }

    private void createSwapchainAndAttachments(long oldSwapchainHandle) {
        SwapchainBundle bundle = new createSwapchain().create(device, physicalDevice, surface, window, oldSwapchainHandle);
        this.swapchain = bundle.swapchainHandle;
        this.swapchainImages = bundle.images;
        this.swapchainImageFormat = bundle.imageFormat;
        this.width = bundle.width;
        this.height = bundle.height;

        imagesInFlight = new long[swapchainImages.length];
        imageviews = new createImageView().create(device, swapchainImages, swapchainImageFormat);

        // ※ すでにレンダーパス（renderpass）が存在していればフレームバッファーもここで作れる
        if (renderpass != 0) {
            framebuffers = new createFramebuffer().create(device, renderpass, imageviews, width, height);
        }
    }

}
