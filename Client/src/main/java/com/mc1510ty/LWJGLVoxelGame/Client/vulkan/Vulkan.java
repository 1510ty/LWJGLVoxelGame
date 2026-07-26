package com.mc1510ty.LWJGLVoxelGame.Client.vulkan;

import org.lwjgl.vulkan.*;

public class Vulkan {

    //戻り値のセット
    public static class SwapchainBundle {
        public long swapchainHandle;
        public long[] images;
        public int imageFormat;
        public VkExtent2D extent;
    }


    //定数
    //バリデーションレイヤー(エラーとかが見やすくなるやつ)の名前の定義
    private static final String VALIDATION_LAYER = "VK_LAYER_KHRONOS_validation";
    //バリデーションレイヤーをONにするか
    private static final boolean ENABLE_VALIDATION_LAYERS = true; // 開発中はtrueにする

    //Vulkanインスタンス
    private VkInstance instance;
    private long surface;
    private VkPhysicalDevice physicalDevice;
    private VkDevice device;
    private VkQueue graphicsQueue;
    //スワップチェーン類
        private long swapchain;
        private long[] swapchainImages;
        private int swapchainImageFormat;
        private VkExtent2D swapchainExtent;
    private long[] imageviews;
    private long renderpass;

    public void initVulkan(long window) {
        long startTime = System.nanoTime();
        IO.println("Vulkanの初期化を開始します");

        instance = new createVulkanInstance().createVulkanInstance(ENABLE_VALIDATION_LAYERS, VALIDATION_LAYER);
        surface = new createSurface().createSurface(instance,window);
        physicalDevice = new selectPhysicalDevice().select(instance,surface);
        device = new createDevice().create(physicalDevice,surface);
        graphicsQueue = new getVkQueue().getGraphicsQueue(device, physicalDevice);
        SwapchainBundle bundle = new createSwapchain().create(device,physicalDevice,surface,window);
            this.swapchain = bundle.swapchainHandle;
            this.swapchainImages = bundle.images;
            this.swapchainImageFormat = bundle.imageFormat;
            this.swapchainExtent = bundle.extent;
        imageviews = new createImageView().create(device,swapchainImages,swapchainImageFormat);
        renderpass = new createRenderPass().create(device, swapchainImageFormat);



        long endTime = System.nanoTime();
        double elapsedSeconds = (endTime - startTime) / 1_000_000_000.0;
        IO.println("Vulkanの初期化が正常に完了しました: " + elapsedSeconds + "秒");
    }

}
