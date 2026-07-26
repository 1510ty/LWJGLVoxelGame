package com.mc1510ty.LWJGLVoxelGame.Client;

import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK14.VK_API_VERSION_1_4;

public class Vulkan {

    //Vulkanインスタンス
    private VkInstance instance;

    public void initVulkan() {
        createVulkanInstance();

    }

    public void createVulkanInstance() {

        //流れを説明します!!! (これを呼んで君もVulkanインスタンス作成マスターだ!(????????????))
        //1. Javaから、CのライブラリであるVulkanを安全、そして高速に呼び出すために、一時的にメモリ領域を確保。tryを抜けると、自動で開放される。
        //2. OSとかドライバが認識するための、いろんな情報(アプリ名とか、エンジン名とか)を設定
        //3. Vulkanのバージョンを設定 (1.4に設定) 補足: 1.4でいろいろ書きやすくなったらしい
        //4. 設定を詰め込む構造体(createInfo構造体)を作成し、
        //   作った構造体に"インスタンス作成用の設定を詰め込んだ構造体(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)"というラベルをくっつけ、
        //   その中にさっき設定したアプリ情報構造体(appInfoポインタ)を突っ込む
        //5. ウィンドウ(GLFW)と、Vulkanを連携させるために、GLFWが必要としているVulkanの拡張機能一覧を取得し、
        //   見つからない場合はおかしい(絶対に必要だから)から例外投げて
        //   さっき作ってた構造体(createInfo)に、拡張機能一覧を突っ込んで有効化させる!
        //6. 作成されたVulkanインスタンスのハンドルを受け取るためのポインタ変数用のメモリを確保して
        //   設定情報を詰めた構造体(createInfo)をドライバに投げてVulkanインスタンスを作って
        //   もし成功しなかった（VK_SUCCESS 以外が返ってきた）場合は例外を投げる
        //
        //7. 完成したVulkanインスタンスのハンドル(インスタンスがある場所を指し示す矢印みたいなもの)をJavaで扱いやすいように、"instance"変数に突っ込む
        //完成!!

        //Start1
        try (MemoryStack stack = stackPush()) { //JavaからC言語のライブラリ（Vulkan）を安全かつ高速に呼び出すために、一時的なメモリ領域（スタック）を確保
        //End1
            //Start2
            VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack); //ポインタを突っ込む構造体を用意して、ポインタを突っ込む
            appInfo.sType(VK_STRUCTURE_TYPE_APPLICATION_INFO); //この構造体(appInfo)はアプリ情報ですよーっていうラベルをくっつける
            appInfo.pApplicationName(stack.UTF8Safe("LWJGL Voxel Game")); //タイトルを設定
            appInfo.applicationVersion(VK_MAKE_VERSION(1, 0, 0)); //バージョンを設定
            appInfo.pEngineName(stack.UTF8Safe("No Engine")); //エンジン名を設定
            appInfo.engineVersion(VK_MAKE_VERSION(1, 0, 0)); //エンジンのバージョンを設定
            //End2

            //Start3
            appInfo.apiVersion(VK_API_VERSION_1_4); //Vulkanバージョンを設定
            //End3

            //Start4
            VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack); //構造体を用意して
            createInfo.sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO); //ラベルをくっつけて
            createInfo.pApplicationInfo(appInfo); //突っ込む!
            //End4


            //Start5
            PointerBuffer glfwExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions(); //取得して
            if (glfwExtensions == null) {
                throw new RuntimeException("Vulkanに対応したGLFWの拡張機能が見つかりません。"); //見つからない場合はおかしい(絶対に必要だから)から例外投げて
            }
            createInfo.ppEnabledExtensionNames(glfwExtensions); //さっき4で作ってた構造体に拡張機能一覧を突っ込んで有効化!
            //End5


            //Start6
            PointerBuffer pInstance = stack.mallocPointer(1); //確保して
            if (vkCreateInstance(createInfo, null, pInstance) != VK_SUCCESS) { //作って
                throw new RuntimeException("Vulkanインスタンスの作成に失敗しました。"); //成功しなかったら例外を投げる
            }
            //End6

            //Start7
            instance = new VkInstance(pInstance.get(0), createInfo); //変数に突っ込む
            //End7
        }
    }


}
