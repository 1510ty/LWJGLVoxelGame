package com.mc1510ty.LWJGLVoxelGame.Client.vulkan;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;

public class createDevice {
    public VkDevice create(VkPhysicalDevice physicalDevice, long surface) {
        IO.println("理論デバイスの作成を開始します");

        try (MemoryStack stack = MemoryStack.stackPush()) {
            // 1. 描画やプレゼンテーションに使えるキューファミリのインデックスを探す
            int queueFamilyIndex = findQueueFamily(physicalDevice);

            // 2. キューの作成情報を設定する
            VkDeviceQueueCreateInfo.Buffer queueCreateInfo = VkDeviceQueueCreateInfo.calloc(1, stack);
            queueCreateInfo.sType(VK10.VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO);
            queueCreateInfo.queueFamilyIndex(queueFamilyIndex);
            queueCreateInfo.pQueuePriorities(stack.floats(1.0f));

            // 3. デバイスの拡張機能（スワップチェーンなど）を指定する
            PointerBuffer extensions = stack.mallocPointer(1);
            extensions.put(stack.UTF8(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME));
            extensions.flip();

            // 4. 論理デバイスの作成情報をまとめる
            VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.calloc(stack);
            createInfo.sType(VK10.VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO);
            createInfo.pQueueCreateInfos(queueCreateInfo);
            createInfo.ppEnabledExtensionNames(extensions);

            // 5. 論理デバイスを生成する
            PointerBuffer pDevice = stack.mallocPointer(1);
            int result = VK10.vkCreateDevice(physicalDevice, createInfo, null, pDevice);
            if (result != VK10.VK_SUCCESS) {
                throw new RuntimeException("論理デバイスの作成に失敗しました: " + result);
            }

            IO.println("理論デバイスの作成が完了しました");
            return new VkDevice(pDevice.get(0), physicalDevice, createInfo);
        }
    }

    private int findQueueFamily(VkPhysicalDevice physicalDevice) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer queueFamilyCount = stack.mallocInt(1);
            VK10.vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, queueFamilyCount, null);

            VkQueueFamilyProperties.Buffer queueFamilies = VkQueueFamilyProperties.malloc(queueFamilyCount.get(0), stack);
            VK10.vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, queueFamilyCount, queueFamilies);

            for (int i = 0; i < queueFamilies.capacity(); i++) {
                // グラフィックス命令を処理できるキューファミリを探す
                if ((queueFamilies.get(i).queueFlags() & VK10.VK_QUEUE_GRAPHICS_BIT) != 0) {
                    return i;
                }
            }

            throw new RuntimeException("適切なキューファミリが見つかりませんでした。");
        }
    }
}