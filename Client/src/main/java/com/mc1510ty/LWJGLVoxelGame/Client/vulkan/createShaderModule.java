package com.mc1510ty.LWJGLVoxelGame.Client.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;

public class createShaderModule {
    public long create(VkDevice device, String resourcePath) {
        IO.println("シェーダーモジュールの作成中: " + resourcePath);

        try (InputStream in = createShaderModule.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new RuntimeException("シェーダーファイルが見つかりません: " + resourcePath);
            }

            byte[] code = in.readAllBytes();

            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer byteCode = stack.malloc(code.length);
                byteCode.put(code);
                byteCode.flip();

                VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.calloc(stack);
                createInfo.sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO);
                createInfo.pCode(byteCode);

                LongBuffer pShaderModule = stack.mallocLong(1);
                int result = vkCreateShaderModule(device, createInfo, null, pShaderModule);
                if (result != VK_SUCCESS) {
                    throw new RuntimeException("シェーダーモジュールの作成に失敗しました: " + result + " (" + resourcePath + ")");
                }

                IO.println("シェーダーモジュールの作成完了");
                return pShaderModule.get(0);
            }
        } catch (IOException e) {
            throw new RuntimeException("シェーダーファイルの読み込みに失敗しました: " + resourcePath, e);
        }
    }
}