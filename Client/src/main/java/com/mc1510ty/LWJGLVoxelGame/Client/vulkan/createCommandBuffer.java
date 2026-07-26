package com.mc1510ty.LWJGLVoxelGame.Client.vulkan;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkDevice;

import static org.lwjgl.vulkan.VK10.*;

public class createCommandBuffer {
    public long[] create(VkDevice device, long commandPool, int count) {
        IO.println("コマンドバッファーの作成中");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack);
            allocInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
            allocInfo.commandPool(commandPool);
            // プライマリ（直接GPUのキューに送信できるメインのバッファ）を指定
            allocInfo.level(VK_COMMAND_BUFFER_LEVEL_PRIMARY);
            allocInfo.commandBufferCount(count);

            PointerBuffer pCommandBuffers = stack.mallocPointer(count);
            int result = vkAllocateCommandBuffers(device, allocInfo, pCommandBuffers);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("コマンドバッファの割り当てに失敗しました: " + result);
            }

            // スタックが消える前に、ハンドル（中身の数値）を安全な long[] 配列にコピーする
            long[] commandBuffers = new long[count];
            for (int i = 0; i < count; i++) {
                commandBuffers[i] = pCommandBuffers.get(i);
            }

            IO.println("コマンドバッファーの作成完了");
            return commandBuffers;
        }
    }
}