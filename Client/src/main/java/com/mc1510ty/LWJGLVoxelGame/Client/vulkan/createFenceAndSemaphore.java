package com.mc1510ty.LWJGLVoxelGame.Client.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;

public class createFenceAndSemaphore {

    // 引数にスワップチェーンの画像枚数 (imageCount) を追加します
    public Vulkan.SyncObjects create(VkDevice device, int maxFramesInFlight, int imageCount) {
        IO.println("同期オブジェクト（セマフォ・フェンス）の作成中...");

        Vulkan.SyncObjects objects = new Vulkan.SyncObjects();
        // セマフォはスワップチェーンの画像枚数分作成します
        objects.imageAvailableSemaphores = new long[imageCount];
        objects.renderFinishedSemaphores = new long[imageCount];
        // フェンスはフレーム数分でOKです
        objects.inFlightFences = new long[maxFramesInFlight];

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.calloc(stack);
            semaphoreInfo.sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);

            VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc(stack);
            fenceInfo.sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
            fenceInfo.flags(VK_FENCE_CREATE_SIGNALED_BIT);

            LongBuffer pImageAvailableSemaphore = stack.mallocLong(1);
            LongBuffer pRenderFinishedSemaphore = stack.mallocLong(1);
            LongBuffer pFence = stack.mallocLong(1);

            // セマフォは画像枚数（imageCount）の分だけループして作成します
            for (int i = 0; i < imageCount; i++) {
                if (vkCreateSemaphore(device, semaphoreInfo, null, pImageAvailableSemaphore) != VK_SUCCESS ||
                        vkCreateSemaphore(device, semaphoreInfo, null, pRenderFinishedSemaphore) != VK_SUCCESS) {
                    throw new RuntimeException("セマフォの作成に失敗しました");
                }
                objects.imageAvailableSemaphores[i] = pImageAvailableSemaphore.get(0);
                objects.renderFinishedSemaphores[i] = pRenderFinishedSemaphore.get(0);
            }

            // フェンスは maxFramesInFlight の分だけ作成します
            for (int i = 0; i < maxFramesInFlight; i++) {
                if (vkCreateFence(device, fenceInfo, null, pFence) != VK_SUCCESS) {
                    throw new RuntimeException("フェンスの作成に失敗しました");
                }
                objects.inFlightFences[i] = pFence.get(0);
            }
        }

        IO.println("同期オブジェクトの作成完了");
        return objects;
    }
}