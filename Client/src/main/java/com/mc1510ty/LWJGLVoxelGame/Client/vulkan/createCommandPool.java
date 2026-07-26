package com.mc1510ty.LWJGLVoxelGame.Client.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkDevice;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;

public class createCommandPool {
    public long create(VkDevice device, int graphicsQueueFamilyIndex) {
        IO.println("コマンドプールの作成中...");
        long commandPool;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandPoolCreateInfo createInfo = VkCommandPoolCreateInfo.calloc(stack);
            createInfo.sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO);
            createInfo.queueFamilyIndex(graphicsQueueFamilyIndex);
            // コマンドバッファを個別にリセット・再利用できるようにするためのフラグです
            createInfo.flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);

            LongBuffer pCommandPool = stack.mallocLong(1);
            int result = vkCreateCommandPool(device, createInfo, null, pCommandPool);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("コマンドプールの作成に失敗しました: " + result);
            }

            commandPool = pCommandPool.get(0);
        }
        IO.println("コマンドプール作成完了");

        return commandPool;
    }
}