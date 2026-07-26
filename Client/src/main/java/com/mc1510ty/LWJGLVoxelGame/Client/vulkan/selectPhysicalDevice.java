package com.mc1510ty.LWJGLVoxelGame.Client.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkPhysicalDevice;

import java.nio.IntBuffer;

public class selectPhysicalDevice {
    public VkPhysicalDevice select(VkInstance instance, long surface) {
        IO.println("物理デバイスの選択を開始します");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // 1. システムにある物理デバイスの数を取得する
            IntBuffer deviceCount = stack.mallocInt(1);
            int result = VK10.vkEnumeratePhysicalDevices(instance, deviceCount, null);
            if (result != VK10.VK_SUCCESS || deviceCount.get(0) == 0) {
                throw new RuntimeException("Vulkanに対応した物理デバイスが見つかりませんでした");
            }

            // 2. 物理デバイスのハンドルリストを取得する
            org.lwjgl.PointerBuffer devices = stack.mallocPointer(deviceCount.get(0));
            VK10.vkEnumeratePhysicalDevices(instance, deviceCount, devices);

            // 3. 条件に合うデバイスを順番に探す
            for (int i = 0; i < devices.capacity(); i++) {
                VkPhysicalDevice device = new VkPhysicalDevice(devices.get(i), instance);

                if (isDeviceSuitable(device, surface)) {
                    IO.println("物理デバイスの選択を完了しました");
                    return device;
                }
            }

            throw new RuntimeException("条件を満たす適切な物理デバイスが見つかりませんでした");
        }
    }

    private boolean isDeviceSuitable(VkPhysicalDevice device, long surface) {
        // 本来はここでキューファミリの確認や、
        // 外部GPU（ディスクリートGPU）かどうかなどの詳細なチェックを行います。
        // ここでは便宜上、見つかったデバイスをそのまま採用します。
        return true;
    }
}
