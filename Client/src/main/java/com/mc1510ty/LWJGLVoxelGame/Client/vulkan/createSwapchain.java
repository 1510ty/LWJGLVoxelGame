package com.mc1510ty.LWJGLVoxelGame.Client.vulkan;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.KHRSurface.*;

public class createSwapchain {

    public Vulkan.SwapchainBundle create(VkDevice device, VkPhysicalDevice physicalDevice, long surface, long window) {
        IO.println("スワップチェーンの作成を開始します");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // 1. サーフェイスの機能（Capabilities）を取得する
            VkSurfaceCapabilitiesKHR capabilities = VkSurfaceCapabilitiesKHR.malloc(stack);
            KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, surface, capabilities);

            // 2. サーフェイスのフォーマットを取得して最適なものを選ぶ
            IntBuffer formatCount = stack.mallocInt(1);
            KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, formatCount, null);
            if (formatCount.get(0) == 0) {
                throw new RuntimeException("利用可能なサーフェイスフォーマットが見つかりませんでした");
            }
            VkSurfaceFormatKHR.Buffer formats = VkSurfaceFormatKHR.malloc(formatCount.get(0), stack);
            KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, formatCount, formats);
            int imageFormat = chooseSwapSurfaceFormat(formats);

            // 3. プレゼンテーションモード（垂直同期などの挙動）を選ぶ
            IntBuffer presentModeCount = stack.mallocInt(1);
            KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, presentModeCount, null);
            if (presentModeCount.get(0) == 0) {
                throw new RuntimeException("利用可能なプレゼンテーションモードが見つかりませんでした");
            }
            IntBuffer presentModes = stack.mallocInt(presentModeCount.get(0));
            KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, presentModeCount, presentModes);
            int presentMode = chooseSwapPresentMode(presentModes);

            // 4. ウィンドウの解像度（Extent）を決定する
            VkExtent2D extent = chooseSwapExtent(capabilities, window, stack);

            // 5. スワップチェーンの画像枚数を決める（最低枚数 + 1枚）
//            int imageCount = capabilities.minImageCount() + 1;
            int imageCount = Math.max(capabilities.minImageCount(), 2);
            if (capabilities.maxImageCount() > 0 && imageCount > capabilities.maxImageCount()) {
                imageCount = capabilities.maxImageCount();
            }

            // 6. スワップチェーンの作成情報を設定する
            VkSwapchainCreateInfoKHR createInfo = VkSwapchainCreateInfoKHR.calloc(stack);
            createInfo.sType(VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR);
            createInfo.surface(surface);
            createInfo.minImageCount(imageCount);
            createInfo.imageFormat(imageFormat);
            createInfo.imageColorSpace(formats.get(0).colorSpace());
            createInfo.imageExtent(extent);
            createInfo.imageArrayLayers(1);
            createInfo.imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT);
            createInfo.imageSharingMode(VK_SHARING_MODE_EXCLUSIVE);
            createInfo.preTransform(capabilities.currentTransform());
            createInfo.compositeAlpha(KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR);
            createInfo.presentMode(presentMode);
            createInfo.clipped(true);
            createInfo.oldSwapchain(VK_NULL_HANDLE);

            // 7. スワップチェーン本体を生成する
            LongBuffer pSwapchain = stack.mallocLong(1);
            int result = vkCreateSwapchainKHR(device, createInfo, null, pSwapchain);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("スワップチェーンの作成に失敗しました: " + result);
            }
            long swapchainHandle = pSwapchain.get(0);

            // 8. スワップチェーン内部の画像（VkImage）のハンドルリストを取得する
            IntBuffer actualImageCount = stack.mallocInt(1);
            vkGetSwapchainImagesKHR(device, swapchainHandle, actualImageCount, null);
            LongBuffer pImages = stack.mallocLong(actualImageCount.get(0));
            vkGetSwapchainImagesKHR(device, swapchainHandle, actualImageCount, pImages);

            int imageCountVal = actualImageCount.get(0);
            long[] images = new long[imageCountVal];
            for (int i = 0; i < imageCountVal; i++) {
                images[i] = pImages.get(i);
            }

            // 9. まとめてバンドルに詰めて返す
            Vulkan.SwapchainBundle bundle = new Vulkan.SwapchainBundle();
            bundle.swapchainHandle = swapchainHandle;
            bundle.images = images;
            bundle.imageFormat = imageFormat;
            bundle.width = extent.width();
            bundle.height = extent.height();

            IO.println("スワップチェーンの作成完了");
            return bundle;
        }
    }

    private int chooseSwapSurfaceFormat(VkSurfaceFormatKHR.Buffer formats) {
        for (VkSurfaceFormatKHR format : formats) {
            if (format.format() == VK_FORMAT_B8G8R8A8_SRGB && format.colorSpace() == KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                return format.format();
            }
        }
        return formats.get(0).format();
    }

    private int chooseSwapPresentMode(IntBuffer presentModes) {
        for (int i = 0; i < presentModes.capacity(); i++) {
            int mode = presentModes.get(i);
            if (mode == VK_PRESENT_MODE_MAILBOX_KHR) {
                return mode; // 低遅延かつ滑らかなメールボックスモードを優先
            }
        }
        return VK_PRESENT_MODE_FIFO_KHR; // 必ずサポートされている標準の垂直同期モード
    }

    private VkExtent2D chooseSwapExtent(VkSurfaceCapabilitiesKHR capabilities, long window, MemoryStack stack) {
        if (capabilities.currentExtent().width() != 0xFFFFFFFF) {
            return capabilities.currentExtent();
        } else {
            int[] width = new int[1];
            int[] height = new int[1];
            GLFW.glfwGetFramebufferSize(window, width, height);

            VkExtent2D actualExtent = VkExtent2D.calloc(stack);
            int widthVal = Math.max(capabilities.minImageExtent().width(), Math.min(capabilities.maxImageExtent().width(), width[0]));
            int heightVal = Math.max(capabilities.minImageExtent().height(), Math.min(capabilities.maxImageExtent().height(), height[0]));
            actualExtent.width(widthVal);
            actualExtent.height(heightVal);
            return actualExtent;
        }
    }
}