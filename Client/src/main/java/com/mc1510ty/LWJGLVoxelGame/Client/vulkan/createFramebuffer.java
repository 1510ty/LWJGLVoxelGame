package com.mc1510ty.LWJGLVoxelGame.Client.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkExtent2D;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;

public class createFramebuffer {
    public long[] create(VkDevice device, long renderPass, long[] imageViews, int width,int height) {
        IO.println("フレームバッファーを作成中");
        long[] framebuffers = new long[imageViews.length];

        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer pAttachments = stack.mallocLong(1);
            LongBuffer pFramebuffer = stack.mallocLong(1);

            VkFramebufferCreateInfo createInfo = VkFramebufferCreateInfo.calloc(stack);
            createInfo.sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO);
            createInfo.renderPass(renderPass);
            createInfo.width(width);
            createInfo.height(height);
            createInfo.layers(1);

            // イメージビューの枚数分だけループして、それぞれに対応するフレームバッファを作成する
            for (int i = 0; i < imageViews.length; i++) {
                pAttachments.put(0, imageViews[i]);
                createInfo.pAttachments(pAttachments);

                int result = vkCreateFramebuffer(device, createInfo, null, pFramebuffer);
                if (result != VK_SUCCESS) {
                    throw new RuntimeException("フレームバッファの作成に失敗しました: " + result);
                }

                framebuffers[i] = pFramebuffer.get(0);
            }
        }
        IO.println("フレームバッファーの作成完了");

        return framebuffers;
    }
}