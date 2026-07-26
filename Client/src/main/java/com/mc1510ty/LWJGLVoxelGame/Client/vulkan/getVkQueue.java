package com.mc1510ty.LWJGLVoxelGame.Client.vulkan;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;

public class getVkQueue {
    public VkQueue getGraphicsQueue(VkDevice device, VkPhysicalDevice physicalDevice) {
        IO.println("グラフィックキューを取得中...");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // 1. グラフィックスを処理できるキューファミリのインデックスを探す
            int queueFamilyIndex = findGraphicsQueueFamily(physicalDevice);

            // 2. 論理デバイスからキューのハンドルを取得する
            PointerBuffer pQueue = stack.mallocPointer(1);
            VK10.vkGetDeviceQueue(device, queueFamilyIndex, 0, pQueue);

            IO.println("キューの取得完了");
            return new VkQueue(pQueue.get(0), device);
        }
    }

    private int findGraphicsQueueFamily(VkPhysicalDevice physicalDevice) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer queueFamilyCount = stack.mallocInt(1);
            VK10.vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, queueFamilyCount, null);

            VkQueueFamilyProperties.Buffer queueFamilies = VkQueueFamilyProperties.malloc(queueFamilyCount.get(0), stack);
            VK10.vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, queueFamilyCount, queueFamilies);

            for (int i = 0; i < queueFamilies.capacity(); i++) {
                if ((queueFamilies.get(i).queueFlags() & VK10.VK_QUEUE_GRAPHICS_BIT) != 0) {
                    return i;
                }
            }

            throw new RuntimeException("グラフィックスキューをサポートするキューファミリが見つかりませんでした");
        }
    }
}
