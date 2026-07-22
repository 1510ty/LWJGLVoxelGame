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

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class ClientLauncher {
    private long window;

    public void run() {
        init();
        loop();

        // 終了時のクリーンアップ
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);

        glfwTerminate();
        org.lwjgl.glfw.GLFWErrorCallback callback = glfwSetErrorCallback(null);
        if (callback != null) {
            callback.free();
        }
    }

    private void init() {
        // エラー出力の設定
        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) {
            throw new IllegalStateException("GLFWの初期化に失敗しました。");
        }

        // ウィンドウの設定
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        // ウィンドウの作成 (幅 1280, 高さ 720)
        window = glfwCreateWindow(1280, 720, "Voxel Game Client", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("GLFWウィンドウの作成に失敗しました。");
        }

        // 画面中央にウィンドウを配置
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);

            glfwGetWindowSize(window, pWidth, pHeight);
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());

            glfwSetWindowPos(
                    window,
                    (vidmode.width() - pWidth.get(0)) / 2,
                    (vidmode.height() - pHeight.get(0)) / 2
            );
        }

        // OpenGLのコンテキストを現在のスレッドに紐付け
        glfwMakeContextCurrent(window);
        // VSyncを有効化
        glfwSwapInterval(1);

        // ウィンドウを表示
        glfwShowWindow(window);

        // OpenGLの機能をバインド（これがないとOpenGLの関数が使えません）
        GL.createCapabilities();
    }

    private void loop() {
        // 背景色をきれいな空色に設定
        glClearColor(0.4f, 0.7f, 1.0f, 0.0f);

        while (!glfwWindowShouldClose(window)) {
            // カラーバッファと深度バッファをクリア
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // ここに描画処理（チャンクの描画など）が入っていきます

            glfwSwapBuffers(window); // 画面をスワップ
            glfwPollEvents();        // キーボードやマウスなどのイベントを処理
        }
    }
}