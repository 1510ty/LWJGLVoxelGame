package com.mc1510ty.LWJGLVoxelGame.Client.vulkan;

import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.util.HashSet;
import java.util.Set;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.vkEnumerateInstanceLayerProperties;
import static org.lwjgl.vulkan.VK14.VK_API_VERSION_1_4;

public class createVulkanInstance {


    public VkInstance createVulkanInstance(boolean ENABLE_VALIDATION_LAYERS, String VALIDATION_LAYER) {

        //説明 by 1510ty

        //バリデーションレイヤーとは: ミスってる意味不明な命令とか送っちゃったときに、
        // エラーを出すと同時に、GPUとかがフリーズするみたいな大惨事を防ぐための層(レイヤー)。

        //流れを説明します!!! (これを呼んで君もVulkanインスタンス作成マスターだ!(????????????))
        //1. Javaから、CのライブラリであるVulkanを安全、そして高速に呼び出すために、一時的にメモリ領域を確保。tryを抜けると、自動で開放される。
        //1.1 開発中は、エラーを見やすくするためのバリデーションレイヤーを有効化するために、それが、ドライバ側で対応しているかの確認
        //    (checkValidationLayerSupportは、独自の関数)
        //2. OSとかドライバが認識するための、いろんな情報(アプリ名とか、エンジン名とか)を設定
        //3. Vulkanのバージョンを設定 (1.4に設定) 補足: 1.4でいろいろ書きやすくなったらしい
        //4. 設定を詰め込む構造体(createInfo構造体)を作成し、
        //   作った構造体に"インスタンス作成用の設定を詰め込んだ構造体(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)"というラベルをくっつけ、
        //   その中にさっき設定したアプリ情報構造体(appInfoポインタ)を突っ込む
        //5. ウィンドウ(GLFW)と、Vulkanを連携させるために、GLFWが必要としているVulkanの拡張機能一覧を取得し、
        //   見つからない場合はおかしい(絶対に必要だから)から例外投げて
        //   さっき作ってた構造体(createInfo)に、拡張機能一覧を突っ込んで有効化させる!
        //5.1 バリデーションレイヤーがドライバ側で対応しているかつ、有効化している(上の方の定数で)なら、構造体(createInfo)にバリデーションレイヤーも突っ込む
        //6. 作成されたVulkanインスタンスのハンドルを受け取るためのポインタ変数用のメモリを確保して
        //   設定情報を詰めた構造体(createInfo)をドライバに投げてVulkanインスタンスを作って
        //   もし成功しなかった（VK_SUCCESS 以外が返ってきた）場合は例外を投げる
        //
        //7. 完成したVulkanインスタンスのハンドル(インスタンスがある場所を指し示す矢印みたいなもの)をJavaで扱いやすいように、"instance"変数に突っ込む
        //完成!!

        IO.println("Vulkanインスタンス作成を開始します");

        long startTime = System.nanoTime();

        //Start1
        try (MemoryStack stack = stackPush()) { //JavaからC言語のライブラリ（Vulkan）を安全かつ高速に呼び出すために、一時的なメモリ領域（スタック）を確保
            //End1


            //Start1.1
            if (ENABLE_VALIDATION_LAYERS && !checkValidationLayerSupport(VALIDATION_LAYER)) { //ドライバがバリデーションレイヤーに対応しているか
                throw new RuntimeException("有効化しようとしたバリデーションレイヤーがサポートされていません"); //対応してないなら例外投げる
            }
            //End1.1

            IO.println("appInfo構造体を作成中...");

            //Start2
            VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack); //ポインタを突っ込む構造体を用意して、ポインタを突っ込む
            appInfo.sType(VK_STRUCTURE_TYPE_APPLICATION_INFO); //この構造体(appInfo)はアプリ情報ですよーっていうラベルをくっつける
            appInfo.pApplicationName(stack.UTF8("LWJGL Voxel Game")); //タイトルを設定
            appInfo.applicationVersion(VK_MAKE_VERSION(1, 0, 0)); //バージョンを設定
            appInfo.pEngineName(stack.UTF8("No Engine")); //エンジン名を設定
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

            IO.println("appInfo構造体をもとにcreateInfo構造体完成!");
            IO.println("Vulkan対応に必要なGLFW拡張機能を取得中...");
            long startTime1 = System.nanoTime();


            //Start5
            PointerBuffer glfwExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions(); //取得して
            if (glfwExtensions == null) {
                throw new RuntimeException("Vulkan対応に必要なGLFWの拡張機能が見つかりません"); //見つからない場合はおかしい(絶対に必要だから)から例外投げて
            }
            createInfo.ppEnabledExtensionNames(glfwExtensions); //さっき4で作ってた構造体に拡張機能一覧を突っ込んで有効化!
            //End5


            long endTime1 = System.nanoTime();
            double elapsedSeconds1 = (endTime1 - startTime1) / 1_000_000_000.0;
            IO.println("取得完了: " + elapsedSeconds1 + "秒");
            IO.println("バリデーションレイヤーを有効化中...");


            //Start5.1
            if (ENABLE_VALIDATION_LAYERS) { //バリデーションレイヤーをONにする設定なら
                PointerBuffer ppEnabledLayerNames = stack.mallocPointer(1); //バリデーションレイヤーの名前を入れるポインタ確保して
                ppEnabledLayerNames.put(0, stack.UTF8(VALIDATION_LAYER)); //名前入れて
                createInfo.ppEnabledLayerNames(ppEnabledLayerNames); //createInfoに突っ込む!
            }
            //End5.1

            IO.println("バリデーションレイヤーを有効化完了");
            IO.println("createInfo構造体をもとにVulkanインスタンス作成中...");

            //Start6
            PointerBuffer pInstance = stack.mallocPointer(1); //確保して
            if (vkCreateInstance(createInfo, null, pInstance) != VK_SUCCESS) { //作って ※nullを渡しているところ(pAllocator)があるが、そこは独自のメモリ管理をしたいへんたi...じゃなくて、上級者向けの設定なので、今回は使いません
                throw new RuntimeException("Vulkanインスタンスの作成に失敗しました"); //成功しなかったら例外を投げる
            }
            //End6

            //Start7
            VkInstance instance = new VkInstance(pInstance.get(0), createInfo); //変数に突っ込む
            //End7

            long endTime = System.nanoTime();
            double elapsedSeconds = (endTime - startTime) / 1_000_000_000.0;
            IO.println("Vulkanインスタンス作成完了: " + elapsedSeconds + "秒");

            return instance;
        }


    }

    private boolean checkValidationLayerSupport(String VALIDATION_LAYER) {
        try (MemoryStack stack = stackPush()) {
            //説明 by Gemini

            // 1. パソコンにインストールされているレイヤーの数を取得する
            int[] layerCount = { 0 };
            vkEnumerateInstanceLayerProperties(layerCount, null);

            // 2. その数に合わせて、レイヤー情報を格納するメモリ領域を確保する
            VkLayerProperties.Buffer availableLayers = VkLayerProperties.malloc(layerCount[0], stack);
            vkEnumerateInstanceLayerProperties(layerCount, availableLayers);

            // 3. 利用可能なレイヤーの名前をリスト（Set）にすべて詰め込む
            Set<String> availableLayerNames = new HashSet<>();
            for (int i = 0; i < layerCount[0]; i++) {
                availableLayerNames.add(availableLayers.get(i).layerNameString());
            }

            // 4. お目当てのバリデーションレイヤーが含まれているか判定する
            return availableLayerNames.contains(VALIDATION_LAYER);
        }
    }
}
