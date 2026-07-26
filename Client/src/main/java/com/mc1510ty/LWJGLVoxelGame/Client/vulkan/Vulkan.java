package com.mc1510ty.LWJGLVoxelGame.Client.vulkan;

import org.lwjgl.vulkan.*;

public class Vulkan {

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

    public long[] imagesInFlight;

    public void initVulkan(long window) {
        long startTime = System.nanoTime();
        IO.println("Vulkanの初期化を開始します");

        instance = new createVulkanInstance().createVulkanInstance(ENABLE_VALIDATION_LAYERS, VALIDATION_LAYER);
        surface = new createSurface().createSurface(instance,window);
        physicalDevice = new selectPhysicalDevice().select(instance,surface);
        device = new createDevice().create(physicalDevice,surface);
        getVkQueue queueGetter = new getVkQueue();
        graphicsQueue = queueGetter.getGraphicsQueue(device, physicalDevice);
        graphicsQueueFamilyIndex = queueGetter.findGraphicsQueueFamily(physicalDevice);
        SwapchainBundle bundle = new createSwapchain().create(device,physicalDevice,surface,window);
            this.swapchain = bundle.swapchainHandle;
            this.swapchainImages = bundle.images;
            this.swapchainImageFormat = bundle.imageFormat;
            this.width = bundle.width;
            this.height = bundle.height;
        imagesInFlight = new long[swapchainImages.length];
        imageviews = new createImageView().create(device,swapchainImages,swapchainImageFormat);
        renderpass = new createRenderPass().create(device, swapchainImageFormat);
        framebuffers = new createFramebuffer().create(device,renderpass,imageviews,width,height);
        commandpool = new createCommandPool().create(device,graphicsQueueFamilyIndex);
        commandbuffers = new createCommandBuffer().create(device,commandpool,swapchainImages.length);
        SyncObjects syncobjects = new createFenceAndSemaphore().create(device,2,swapchainImages.length);
            this.imageAvailableSemaphores = syncobjects.imageAvailableSemaphores;
            this.renderFinishedSemaphores = syncobjects.renderFinishedSemaphores;
            this.inFlightFences = syncobjects.inFlightFences;
        vertShaderModule = new createShaderModule().create(device, "shaders/vert.spv");
        fragShaderModule = new createShaderModule().create(device, "shaders/frag.spv");
        graphicspipeline = new createGraphicsPipeline().create(device,renderpass,vertShaderModule,fragShaderModule,width,height);

        long endTime = System.nanoTime();
        double elapsedSeconds = (endTime - startTime) / 1_000_000_000.0;
        IO.println("Vulkanの初期化が正常に完了しました: " + elapsedSeconds + "秒");
    }

}
