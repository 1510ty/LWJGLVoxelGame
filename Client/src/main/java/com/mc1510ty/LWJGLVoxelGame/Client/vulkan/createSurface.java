package com.mc1510ty.LWJGLVoxelGame.Client.vulkan;

import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkInstance;

import java.nio.LongBuffer;

public class createSurface {
    public long createSurface(VkInstance instance, long window) {

        //解説 by Gemini
        //ウィンドウサーフェイスとは: Vulkanが描いた絵を、OSのウィンドウに映し出すための架け橋
        //なぜ必要なのか?
        // 1. OSの壁をなくして繋ぐため
        //    (WindowsならWindowsのウィンドウ、LinuxならX11やWaylandといった、OSごとに違うウィンドウの仕組みを、
        //    Vulkan側から共通して扱える形に包み込んでくれるのがサーフェイス(VkSurfaceKHR))
        // 2. 画面に映すための土台（スワップチェーン）を作るため
        //    Vulkanで本格的に絵を画面に出すときは「スワップチェーン」という仕組みを作るが、
        //    その大前提として「どのサーフェイスに対して描画するのか」を指定しなければいけないため、
        //    絶対に欠かせない必須アイテムになっている
        IO.println("サーフェイスの作成を開始します...");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // サーフェイスのハンドルを格納するためのバッファを用意する
            LongBuffer pSurface = stack.mallocLong(1);

            // GLFWとVulkanインスタンスを使ってサーフェイスを作成！
            int result = GLFWVulkan.glfwCreateWindowSurface(instance, window, null, pSurface);
            if (result != VK10.VK_SUCCESS) {
                throw new RuntimeException("ウィンドウサーフェイスの作成に失敗しました: " + result);
            }

            // 生成されたサーフェイスのハンドル（long型）を取り出して返す
            IO.println("サーフェイス作成完了");
            return pSurface.get(0);
        }
    }
}
