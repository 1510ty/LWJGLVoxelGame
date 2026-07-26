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
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); //最初は非表示
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