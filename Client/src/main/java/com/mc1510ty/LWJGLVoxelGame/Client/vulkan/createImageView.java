package com.mc1510ty.LWJGLVoxelGame.Client.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkImageViewCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;

public class createImageView {

    public long[] create(VkDevice device, long[] images, int imageFormat) {
        IO.println("イメージビューの作成を開始します");
        long[] imageViews = new long[images.length];

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageViewCreateInfo createInfo = VkImageViewCreateInfo.calloc(stack);
            createInfo.sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO);
            createInfo.viewType(VK_IMAGE_VIEW_TYPE_2D);
            createInfo.format(imageFormat);

            // 色チャンネルの割り当て（デフォルトのまま素直に通す設定）
            createInfo.components().r(VK_COMPONENT_SWIZZLE_IDENTITY);
            createInfo.components().g(VK_COMPONENT_SWIZZLE_IDENTITY);
            createInfo.components().b(VK_COMPONENT_SWIZZLE_IDENTITY);
            createInfo.components().a(VK_COMPONENT_SWIZZLE_IDENTITY);

            // 画像のどの範囲を対象にするか（通常のカラー画像として全体を対象にする）
            createInfo.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
            createInfo.subresourceRange().baseMipLevel(0);
            createInfo.subresourceRange().levelCount(1);
            createInfo.subresourceRange().baseArrayLayer(0);
            createInfo.subresourceRange().layerCount(1);

            // PointerBuffer ではなく LongBuffer を使用する
            LongBuffer pImageView = stack.mallocLong(1);

            // スワップチェーンの画像枚数分だけループして、それぞれに対応するイメージビューを作る
            for (int i = 0; i < images.length; i++) {
                createInfo.image(images[i]);

                int result = vkCreateImageView(device, createInfo, null, pImageView);
                if (result != VK_SUCCESS) {
                    throw new RuntimeException("イメージビューの作成に失敗しました: " + result);
                }

                imageViews[i] = pImageView.get(0);
            }
        }
        IO.println("イメージビューの作成が完了しました");

        return imageViews;
    }
}