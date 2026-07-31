//        LWJGLVoxelGame
//        Copyright (C) 2026  1510ty
//
//        This program is free software: you can redistribute it and/or modify
//        it under the terms of the GNU General Public License as published by
//        the Free Software Foundation, either version 3 of the License, or
//        (at your option) any later version.
//
//        This program is distributed in the hope that it will be useful,
//        but WITHOUT ANY WARRANTY; without even the implied warranty of
//        MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//        GNU General Public License for more details.
//
//        You should have received a copy of the GNU General Public License
//        along with this program.  If not, see <https://www.gnu.org/licenses/>.
package com.mc1510ty.LWJGLVoxelGame.Client;

import com.mc1510ty.LWJGLVoxelGame.Client.vulkan.Vulkan;
import org.lwjgl.glfw.GLFWErrorCallback;

import java.io.*;
import java.net.Socket;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;


import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwWindowShouldClose;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

public class ClientLauncher {

    //ゲームステート
    public enum GameState {
        MENU,
        ADDRESS_INPUT, // アドレス入力画面
        PLAYING
    }


    //return用の型
    public record ConnectionResult(
            World world,
            ClientLauncher.GameState currentState,
            boolean firstMouse,
            Socket socket,
            DataOutputStream serverOut) {}
    public record WorldConnectionResult(
            World world,
            Socket socket,
            DataOutputStream serverOut
    ) {}


    //ウィンドウ関連
    private long window;
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;

    //IngratedServer関連
    private Process serverProcess;
    private DataOutputStream serverOut;
    private Socket serverSocket;
    private String worldFilePath;


    //Vulkan関連
    private Vulkan vulkan = new Vulkan();


    public void run(String[] args) {
        if (args.length > 0) {
            worldFilePath = args[0];
        } else {
            File tempDir = new File(System.getProperty("java.io.tmpdir"), "voxelgame_server");
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }
            worldFilePath = new File(tempDir, "world.dat").getAbsolutePath();
        }

        init();
        loop();
        cleanup();
    }

    private void init() {

        //GLFWを初期化
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            throw new IllegalStateException("GLFWの初期化に失敗しました。");
        }

        //GLFW設定
        glfwDefaultWindowHints(); //リセット!
    //        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); //最初は非表示
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE); //サイズ変更可能
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API); //OpenGLを勝手にセットアップするな

        //ウィンドウ作成
        window = glfwCreateWindow(WIDTH, HEIGHT, "Voxel Game Client (Vulkan)", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("GLFWウィンドウの作成に失敗しました");
        }


        //Vulkanの初期化
        vulkan.initVulkan(window);


    }

    private void loop() {
        int currentFrame = 0;
        int maxFramesInFlight = 2;

        while (!glfwWindowShouldClose(window)) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                // 1. ウィンドウのイベント処理
                glfwPollEvents();

                // 2. 現在のフレームのGPU処理完了を待つ
                vkWaitForFences(vulkan.device, vulkan.inFlightFences[currentFrame], true, -1L);

                // 3. スワップチェーンから画像を取得する
                IntBuffer imageIndexBuffer = stack.mallocInt(1);
                int result = vkAcquireNextImageKHR(
                        vulkan.device,
                        vulkan.swapchain,
                        -1L,
                        vulkan.imageAvailableSemaphores[currentFrame],
                        VK_NULL_HANDLE,
                        imageIndexBuffer
                );
                int imageIndex = imageIndexBuffer.get(0);

                if (result == VK_ERROR_OUT_OF_DATE_KHR || result == VK_SUBOPTIMAL_KHR) {
                    vulkan.recreateSwapchain();
                    continue;
                } else if (result != VK_SUCCESS) {
                    throw new RuntimeException("スワップチェーン画像の取得に失敗しました");
                }

                // 4. フェンスをリセットする
                vkResetFences(vulkan.device, vulkan.inFlightFences[currentFrame]);

                // 5. コマンドバッファーに描画命令を記録する
                VkCommandBuffer commandBuffer = new VkCommandBuffer(vulkan.commandbuffers[currentFrame], vulkan.device);
                vkResetCommandBuffer(commandBuffer, 0);

                VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack);
                beginInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
                if (vkBeginCommandBuffer(commandBuffer, beginInfo) != VK_SUCCESS) {
                    throw new RuntimeException("コマンドバッファーの記録開始に失敗しました");
                }

                VkRenderPassBeginInfo renderPassInfo = VkRenderPassBeginInfo.calloc(stack);
                renderPassInfo.sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO);
                renderPassInfo.renderPass(vulkan.renderpass);
                renderPassInfo.framebuffer(vulkan.framebuffers[imageIndex]);
                renderPassInfo.renderArea().offset().set(0, 0);
                renderPassInfo.renderArea().extent().set(vulkan.width, vulkan.height);

                VkClearValue.Buffer clearValues = VkClearValue.calloc(1, stack);
                clearValues.color().float32(0, 0.1f); // 背景色（少し暗い青っぽくする場合など）
                clearValues.color().float32(1, 0.1f);
                clearValues.color().float32(2, 0.1f);
                clearValues.color().float32(3, 1.0f);
                renderPassInfo.pClearValues(clearValues);

                vkCmdBeginRenderPass(commandBuffer, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);
                vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, vulkan.graphicspipeline);
                vkCmdDraw(commandBuffer, 3, 1, 0, 0); // 三角形を描く
                vkCmdEndRenderPass(commandBuffer);

                if (vkEndCommandBuffer(commandBuffer) != VK_SUCCESS) {
                    throw new RuntimeException("コマンドバッファーの記録終了に失敗しました");
                }

                // 6. キューへ提出（Submit）する
                VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack);
                submitInfo.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO);

                submitInfo.waitSemaphoreCount(1); // ★ 待機するセマフォの数を明示
                submitInfo.pWaitSemaphores(stack.longs(vulkan.imageAvailableSemaphores[currentFrame]));
                submitInfo.pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT));
                submitInfo.pCommandBuffers(stack.pointers(commandBuffer));
                submitInfo.signalSemaphoreCount(); // ★ シグナルするセマフォの数を明示
                submitInfo.pSignalSemaphores(stack.longs(vulkan.renderFinishedSemaphores[currentFrame]));

                if (vkQueueSubmit(vulkan.graphicsQueue, submitInfo, vulkan.inFlightFences[currentFrame]) != VK_SUCCESS) {
                    throw new RuntimeException("描画コマンドキューの提出に失敗しました");
                }

                // 7. 画面に映し出す（Present）
                VkPresentInfoKHR presentInfo = VkPresentInfoKHR.calloc(stack);
                presentInfo.sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR);

                presentInfo.pWaitSemaphores(stack.longs(vulkan.renderFinishedSemaphores[currentFrame]));
                presentInfo.swapchainCount(1); // ★ここには「1」を指定してあげる必要があります！
                presentInfo.pSwapchains(stack.longs(vulkan.swapchain));
                presentInfo.pImageIndices(imageIndexBuffer);

                vkQueuePresentKHR(vulkan.graphicsQueue, presentInfo);

                // 8. フレームを交互に進める（0 -> 1 -> 0 -> 1...）
                currentFrame = (currentFrame + 1) % maxFramesInFlight;
            }
        }

        // 終了時はデバイスの処理完了を待つ
        vkDeviceWaitIdle(vulkan.device);
    }

    private void cleanup() {
        if (serverProcess != null && serverProcess.isAlive()) {
            System.out.println("Stopping embedded server gracefully...");

            if (serverOut != null) {
                try {
                    serverOut.writeInt(-1);
                    serverOut.flush();
                } catch (IOException ignored) {
                }
            }

            try {
                boolean exited = serverProcess.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
                if (!exited) {
                    serverProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                serverProcess.destroyForcibly();
            }
        }

        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}