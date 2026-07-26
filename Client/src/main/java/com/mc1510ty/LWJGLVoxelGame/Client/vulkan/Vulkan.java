package com.mc1510ty.LWJGLVoxelGame.Client.vulkan;

import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkPhysicalDevice;

public class Vulkan {

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

    public void initVulkan(long window) {
        long startTime = System.nanoTime();
        IO.println("Vulkanの初期化を開始します");
        instance = new createVulkanInstance().createVulkanInstance(ENABLE_VALIDATION_LAYERS, VALIDATION_LAYER);
        surface = new createSurface().createSurface(instance,window);
        physicalDevice = new selectPhysicalDevice().select(instance,surface);
        device = new createDevice().create(physicalDevice,surface);
        long endTime = System.nanoTime();
        double elapsedSeconds = (endTime - startTime) / 1_000_000_000.0;
        IO.println("Vulkanの初期化が正常に完了しました: " + elapsedSeconds + "秒");
    }

}
