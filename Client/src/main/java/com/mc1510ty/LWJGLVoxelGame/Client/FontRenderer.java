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

import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.lwjgl.PointerBuffer;
import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.stb.STBTTBakedChar;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.stb.STBTruetype.stbtt_BakeFontBitmap;
import static org.lwjgl.stb.STBTruetype.stbtt_GetBakedQuad;
import static org.lwjgl.vulkan.VK10.*;

public class FontRenderer {
    private final VkDevice device;
    private final VkPhysicalDevice physicalDevice;

    private STBTTBakedChar.Buffer cdata;

    // Vulkan テクスチャ・サンプラー関連
    private long fontImage;
    private long fontImageMemory;
    private long fontImageView;
    private long fontSampler;

    // ディスクリプタ & パイプライン関連
    private long descriptorSetLayout;
    private long descriptorPool;
    private long descriptorSet;
    private long pipelineLayout;
    private long graphicsPipeline;

    // 頂点バッファ関連（動的確保）
    private long fontVertexBuffer;
    private long fontVertexMemory;
    private long vertexBufferCapacity = 1024 * 4 * 4 * Float.BYTES; // 1024文字分の容量を確保

    private static final int BITMAP_W = 1024;
    private static final int BITMAP_H = 1024;
    private static final int FIRST_CHAR = 32;
    private static final int NUM_CHARS = 96;

    public FontRenderer(VkDevice device, VkPhysicalDevice physicalDevice, long renderPass, int subpass, long commandPool, VkQueue graphicsQueue, String resourcePath) {
        this.device = device;
        this.physicalDevice = physicalDevice;
        System.out.println("[FontRenderer Debug] 初期化を開始します。リソースパス: " + resourcePath);

        try {
            ByteBuffer ttf = loadResourceToByteBuffer(resourcePath);
            System.out.println("[FontRenderer Debug] フォントのバイトバッファ読み込み成功. サイズ: " + ttf.remaining());

            ByteBuffer bitmap = MemoryUtil.memAlloc(BITMAP_W * BITMAP_H);
            cdata = STBTTBakedChar.malloc(NUM_CHARS);

            stbtt_BakeFontBitmap(ttf, 24.0f, bitmap, BITMAP_W, BITMAP_H, FIRST_CHAR, cdata);
            System.out.println("[FontRenderer Debug] stbtt_BakeFontBitmap によるフォントベイクが完了しました。");

            // 1. テクスチャ画像の作成とGPUへの転送
            createFontTexture(commandPool, graphicsQueue, bitmap);

            try {
                int width = BITMAP_W;
                int height = BITMAP_H;
                java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_BYTE_GRAY);
                byte[] bytes = new byte[width * height];
                bitmap.position(0);
                bitmap.get(bytes);
                img.getRaster().setDataElements(0, 0, width, height, bytes);
                javax.imageio.ImageIO.write(img, "png", new java.io.File("font_debug.png"));
                System.out.println("[FontRenderer Debug] font_debug.png の書き出しに成功しました。");
            } catch (Exception e) {
                System.err.println("[FontRenderer Error] font_debug.png の書き出しに失敗しました: " + e.getMessage());
                e.printStackTrace();
            }

            // 2. イメージビューとサンプラーの作成
            createImageViewAndSampler();

            MemoryUtil.memFree(bitmap);
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("[FontRenderer Error] 致命的エラー: フォントの読み込みに失敗しました: " + resourcePath);
            throw new RuntimeException("フォントの読み込みに失敗しました: " + resourcePath, e);
        }

        // 3. ディスクリプタとパイプラインの構築
        setupPipelineAndDescriptors(renderPass, subpass);

        ensureVertexBufferSize(1024 * 4 * 4 * Float.BYTES);

        System.out.println("[FontRenderer Debug] フォントレンダラーの初期化がすべて完了しました。");
    }

    private ByteBuffer loadResourceToByteBuffer(String path) throws IOException {
        try (InputStream in = FontRenderer.class.getResourceAsStream(path)) {
            if (in == null) {
                System.err.println("[FontRenderer Error] ファイルが見つかりません: " + path);
                throw new IOException("ファイルが見つかりません: " + path);
            }
            byte[] bytes = in.readAllBytes();
            ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
            buffer.put(bytes);
            buffer.flip();
            return buffer;
        }
    }

    private void createFontTexture(long commandPool, VkQueue graphicsQueue, ByteBuffer bitmap) {
        System.out.println("[FontRenderer Debug] createFontTexture 開始");
        long imageSize = BITMAP_W * BITMAP_H;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            // ステージングバッファの作成
            VkBufferCreateInfo stagingBufferInfo = VkBufferCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                    .size(imageSize)
                    .usage(VK_BUFFER_USAGE_TRANSFER_SRC_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

            LongBuffer pStagingBuffer = stack.mallocLong(1);
            vkCreateBuffer(device, stagingBufferInfo, null, pStagingBuffer);
            long stagingBuffer = pStagingBuffer.get(0);

            VkMemoryRequirements memRequirements = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(device, stagingBuffer, memRequirements);

            int memTypeIndex = findMemoryType(memRequirements.memoryTypeBits(),
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);

            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                    .allocationSize(memRequirements.size())
                    .memoryTypeIndex(memTypeIndex);

            LongBuffer pStagingMemory = stack.mallocLong(1);
            vkAllocateMemory(device, allocInfo, null, pStagingMemory);
            long stagingMemory = pStagingMemory.get(0);

            vkBindBufferMemory(device, stagingBuffer, stagingMemory, 0);

            org.lwjgl.PointerBuffer pData = stack.mallocPointer(1);
            vkMapMemory(device, stagingMemory, 0, imageSize, 0, pData);
            MemoryUtil.memCopy(MemoryUtil.memAddress(bitmap), pData.get(0), imageSize);
            vkUnmapMemory(device, stagingMemory);

            // VkImage の作成
            VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                    .imageType(VK_IMAGE_TYPE_2D)
                    .extent(e -> e.width(BITMAP_W).height(BITMAP_H).depth(1))
                    .mipLevels(1)
                    .arrayLayers(1)
                    .format(VK_FORMAT_R8_UNORM)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .usage(VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

            LongBuffer pImage = stack.mallocLong(1);
            vkCreateImage(device, imageInfo, null, pImage);
            fontImage = pImage.get(0);

            vkGetImageMemoryRequirements(device, fontImage, memRequirements);
            allocInfo.allocationSize(memRequirements.size());
            allocInfo.memoryTypeIndex(findMemoryType(memRequirements.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));

            LongBuffer pImageMemory = stack.mallocLong(1);
            vkAllocateMemory(device, allocInfo, null, pImageMemory);
            fontImageMemory = pImageMemory.get(0);

            vkBindImageMemory(device, fontImage, fontImageMemory, 0);

            // 一時コマンドバッファでレイアウト変更とコピーを実行
            transitionImageLayout(commandPool, graphicsQueue, fontImage, VK_FORMAT_R8_UNORM, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
            copyBufferToImage(commandPool, graphicsQueue, stagingBuffer, fontImage, BITMAP_W, BITMAP_H);
            transitionImageLayout(commandPool, graphicsQueue, fontImage, VK_FORMAT_R8_UNORM, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

            vkDestroyBuffer(device, stagingBuffer, null);
            vkFreeMemory(device, stagingMemory, null);
        }
        System.out.println("[FontRenderer Debug] createFontTexture 完了");
    }

    private void createImageViewAndSampler() {
        System.out.println("[FontRenderer Debug] createImageViewAndSampler 開始");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                    .image(fontImage)
                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                    .format(VK_FORMAT_R8_UNORM);

            viewInfo.components()
                    .r(VK_COMPONENT_SWIZZLE_IDENTITY)
                    .g(VK_COMPONENT_SWIZZLE_IDENTITY)
                    .b(VK_COMPONENT_SWIZZLE_IDENTITY)
                    .a(VK_COMPONENT_SWIZZLE_IDENTITY);

            viewInfo.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
            viewInfo.subresourceRange().baseMipLevel(0);
            viewInfo.subresourceRange().levelCount(1);
            viewInfo.subresourceRange().baseArrayLayer(0);
            viewInfo.subresourceRange().layerCount(1);

            LongBuffer pView = stack.mallocLong(1);
            int err = vkCreateImageView(device, viewInfo, null, pView);
            if (err != VK_SUCCESS) {
                System.err.println("[FontRenderer Error] vkCreateImageView 失敗. Error code: " + err);
            }
            fontImageView = pView.get(0);

            VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
                    .magFilter(VK_FILTER_LINEAR)
                    .minFilter(VK_FILTER_LINEAR)
                    .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .anisotropyEnable(false)
                    .maxAnisotropy(1.0f)
                    .borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK)
                    .unnormalizedCoordinates(false)
                    .compareEnable(false)
                    .compareOp(VK_COMPARE_OP_ALWAYS)
                    .mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR);

            LongBuffer pSampler = stack.mallocLong(1);
            err = vkCreateSampler(device, samplerInfo, null, pSampler);
            if (err != VK_SUCCESS) {
                System.err.println("[FontRenderer Error] vkCreateSampler 失敗. Error code: " + err);
            }
            fontSampler = pSampler.get(0);
        }
        System.out.println("[FontRenderer Debug] createImageViewAndSampler 完了");
    }

    private void setupPipelineAndDescriptors(long renderPass, int subpass) {
        System.out.println("[FontRenderer Debug] setupPipelineAndDescriptors 開始");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // 1. ディスクリプタセットレイアウトの作成 (サンプラー用)
            VkDescriptorSetLayoutBinding.Buffer setLayoutBinding = VkDescriptorSetLayoutBinding.calloc(1, stack)
                    .binding(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);

            VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                    .pBindings(setLayoutBinding);

            LongBuffer pSetLayout = stack.mallocLong(1);
            vkCreateDescriptorSetLayout(device, layoutInfo, null, pSetLayout);
            descriptorSetLayout = pSetLayout.get(0);

            // 2. ディスクリプタプールとセットの作成
            VkDescriptorPoolSize.Buffer poolSize = VkDescriptorPoolSize.calloc(1, stack)
                    .type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1);

            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                    .maxSets(1)
                    .pPoolSizes(poolSize);

            LongBuffer pPool = stack.mallocLong(1);
            vkCreateDescriptorPool(device, poolInfo, null, pPool);
            descriptorPool = pPool.get(0);

            VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                    .descriptorPool(descriptorPool)
                    .pSetLayouts(stack.longs(descriptorSetLayout));

            LongBuffer pSet = stack.mallocLong(1);
            vkAllocateDescriptorSets(device, allocInfo, pSet);
            descriptorSet = pSet.get(0);

            // ディスクリプタに画像を書き込む
            VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack)
                    .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .imageView(fontImageView)
                    .sampler(fontSampler);

            VkWriteDescriptorSet.Buffer descriptorWrite = VkWriteDescriptorSet.calloc(1, stack);
            descriptorWrite.get(0)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(descriptorSet)
                    .dstBinding(0)
                    .dstArrayElement(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(imageInfo);

            vkUpdateDescriptorSets(device, descriptorWrite, null);

            // 3. パイプラインレイアウト
            VkPushConstantRange.Buffer pushConstantRange = VkPushConstantRange.calloc(1, stack)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT)
                    .offset(0)
                    .size(80);

            VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                    .pSetLayouts(stack.longs(descriptorSetLayout))
                    .pPushConstantRanges(pushConstantRange);

            LongBuffer pPipelineLayout = stack.mallocLong(1);
            vkCreatePipelineLayout(device, pipelineLayoutInfo, null, pPipelineLayout);
            pipelineLayout = pPipelineLayout.get(0);

            // 4. シェーダーモジュールのロードとパイプライン構築
            ByteBuffer vertCode = loadShaderFile("shaders/font.vert.spv");
            ByteBuffer fragCode = loadShaderFile("shaders/font.frag.spv");
            long vertModule = createShaderModule(vertCode);
            long fragModule = createShaderModule(fragCode);

            VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            stages.get(0)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_VERTEX_BIT)
                    .module(vertModule)
                    .pName(stack.UTF8("main"));

            stages.get(1)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_FRAGMENT_BIT)
                    .module(fragModule)
                    .pName(stack.UTF8("main"));

            VkVertexInputBindingDescription.Buffer bindingDesc = VkVertexInputBindingDescription.calloc(1, stack)
                    .binding(0)
                    .stride(4 * Float.BYTES)
                    .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

            VkVertexInputAttributeDescription.Buffer attribDesc = VkVertexInputAttributeDescription.calloc(2, stack);
            attribDesc.get(0).binding(0).location(0).format(VK_FORMAT_R32G32_SFLOAT).offset(0);
            attribDesc.get(1).binding(0).location(1).format(VK_FORMAT_R32G32_SFLOAT).offset(2 * Float.BYTES);

            VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
                    .pVertexBindingDescriptions(bindingDesc)
                    .pVertexAttributeDescriptions(attribDesc);

            VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                    .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                    .primitiveRestartEnable(false);

            VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                    .viewportCount(1).scissorCount(1);

            VkPipelineRasterizationStateCreateInfo rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                    .polygonMode(VK_POLYGON_MODE_FILL).lineWidth(1.0f)
                    .cullMode(VK_CULL_MODE_NONE).frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE);

            VkPipelineMultisampleStateCreateInfo multisample = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                    .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);

            VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack)
                    .colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT)
                    .blendEnable(true)
                    .srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA)
                    .dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                    .colorBlendOp(VK_BLEND_OP_ADD)
                    .srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
                    .dstAlphaBlendFactor(VK_BLEND_FACTOR_ZERO)
                    .alphaBlendOp(VK_BLEND_OP_ADD);

            VkPipelineColorBlendStateCreateInfo colorBlending = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                    .pAttachments(colorBlendAttachment);

            int[] dynamicStates = { VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR };
            VkPipelineDynamicStateCreateInfo dynamicState = VkPipelineDynamicStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO)
                    .pDynamicStates(stack.ints(dynamicStates));

            VkPipelineDepthStencilStateCreateInfo depthStencil = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
                    .depthTestEnable(false)
                    .depthWriteEnable(false)
                    .depthCompareOp(VK_COMPARE_OP_LESS)
                    .depthBoundsTestEnable(false)
                    .stencilTestEnable(false);

            VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                    .stageCount(stages.capacity())
                    .pStages(stages)
                    .pVertexInputState(vertexInput)
                    .pInputAssemblyState(inputAssembly)
                    .pViewportState(viewportState)
                    .pRasterizationState(rasterizer)
                    .pMultisampleState(multisample)
                    .pDepthStencilState(depthStencil)
                    .pColorBlendState(colorBlending)
                    .pDynamicState(dynamicState)
                    .layout(pipelineLayout)
                    .renderPass(renderPass)
                    .subpass(subpass);

            LongBuffer pPipeline = stack.mallocLong(1);
            int err = vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, pipelineInfo, null, pPipeline);
            if (err != VK_SUCCESS) {
                System.err.println("[FontRenderer Error] vkCreateGraphicsPipelines 失敗. Error code: " + err);
            }
            graphicsPipeline = pPipeline.get(0);

            vkDestroyShaderModule(device, vertModule, null);
            vkDestroyShaderModule(device, fragModule, null);
        }
        System.out.println("[FontRenderer Debug] setupPipelineAndDescriptors 完了");
    }

    private ByteBuffer loadShaderFile(String path) {
        try (java.io.InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                System.err.println("[FontRenderer Error] シェーダーファイルが見つかりません: " + path);
                throw new RuntimeException("シェーダーファイルが見つかりません: " + path);
            }
            byte[] bytes = is.readAllBytes();
            ByteBuffer buffer = org.lwjgl.BufferUtils.createByteBuffer(bytes.length);
            buffer.put(bytes);
            buffer.flip();
            return buffer;
        } catch (Exception e) {
            throw new RuntimeException("シェーダーの読み込みに失敗しました: " + path, e);
        }
    }

    public void drawText(VkCommandBuffer commandBuffer, String text, float x, float y, double scale, int screenWidth, int screenHeight, Vector3d color) {


        if (text == null || text.isEmpty()) {
            System.out.println("[FontRenderer Debug] drawText: テキストが空のため描画をスキップします。");
            return;
        }

        System.out.println("[FontRenderer Debug] drawText 呼び出し: \"" + text + "\" at (" + x + ", " + y + "), 画面サイズ: " + screenWidth + "x" + screenHeight);

        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicsPipeline);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkViewport.Buffer pViewports = VkViewport.calloc(1, stack)
                    .x(0.0f)
                    .y(0.0f)
                    .width(screenWidth)
                    .height(screenHeight)
                    .minDepth(0.0f)
                    .maxDepth(1.0f);
            vkCmdSetViewport(commandBuffer, 0, pViewports);

            VkRect2D.Buffer pScissors = VkRect2D.calloc(1, stack);
            pScissors.offset().set(0, 0);
            pScissors.extent().set(screenWidth, screenHeight);
            vkCmdSetScissor(commandBuffer, 0, pScissors);
        }

        vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipelineLayout, 0, culLongBuffer(descriptorSet), null);

        Matrix4d ortho = new Matrix4d().ortho2D(0, screenWidth, screenHeight, 0);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer pushConst = stack.malloc(80);
            org.joml.Matrix4f floatOrtho = new org.joml.Matrix4f(
                    (float)ortho.m00(), (float)ortho.m01(), (float)ortho.m02(), (float)ortho.m03(),
                    (float)ortho.m10(), (float)ortho.m11(), (float)ortho.m12(), (float)ortho.m13(),
                    (float)ortho.m20(), (float)ortho.m21(), (float)ortho.m22(), (float)ortho.m23(),
                    (float)ortho.m30(), (float)ortho.m31(), (float)ortho.m32(), (float)ortho.m33()
            );
            floatOrtho.get(pushConst);
            pushConst.putFloat((float) color.x);
            pushConst.putFloat((float) color.y);
            pushConst.putFloat((float) color.z);
            pushConst.putInt(0);
            pushConst.flip();

            vkCmdPushConstants(commandBuffer, pipelineLayout, VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT, 0, pushConst);
        }


        try (MemoryStack stack = MemoryStack.stackPush()) {
            STBTTAlignedQuad q = STBTTAlignedQuad.malloc(stack);
            FloatBuffer verticesBuffer = stack.mallocFloat(text.length() * 24);

            float[] xpos = { x };
            float[] ypos = { y + 20.0f };
            int validCharCount = intDebugCharCount(text); // デバッグ用文字数カウント

            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c < FIRST_CHAR || c >= FIRST_CHAR + NUM_CHARS) {
                    System.out.println("[FontRenderer Debug] 文字範囲外スキップ: '" + c + "' (コード: " + (int)c + ")");
                    continue;
                }

                stbtt_GetBakedQuad(cdata, BITMAP_W, BITMAP_H, c - FIRST_CHAR, xpos, ypos, q, true);

                float x0 = q.x0();
                float y0 = q.y0();
                float x1 = q.x1();
                float y1 = q.y1();

                System.out.println("[FontDebug] 文字 '" + c + "': x(" + x0 + " ~ " + x1 + "), y(" + y0 + " ~ " + y1 + ")");

                float s0 = q.s0();
                float t0 = q.t0();
                float s1 = q.s1();
                float t1 = q.t1();

                verticesBuffer.put(x0).put(y0).put(s0).put(t0);
                verticesBuffer.put(x0).put(y1).put(s0).put(t1);
                verticesBuffer.put(x1).put(y1).put(s1).put(t1);

                verticesBuffer.put(x0).put(y0).put(s0).put(t0);
                verticesBuffer.put(x1).put(y1).put(s1).put(t1);
                verticesBuffer.put(x1).put(y0).put(s1).put(t0);

                validCharCount++;
            }
            verticesBuffer.flip();

            System.out.println("[FontRenderer Debug] 有効な文字数 (validCharCount): " + validCharCount);
            if (validCharCount == 0) {
                System.out.println("[FontRenderer Debug] 描画可能な文字が0個のため描画を中断します。");
                return;
            }


            long requiredSize = verticesBuffer.remaining() * Float.BYTES;
            if (requiredSize > vertexBufferCapacity) {
                System.err.println("[FontRenderer Error] 描画する文字数がバッファ容量を超えています！");
                return;
            }

            try (MemoryStack innerStack = MemoryStack.stackPush()) {
                org.lwjgl.PointerBuffer pData = innerStack.mallocPointer(1);
                vkMapMemory(device, fontVertexMemory, 0, requiredSize, 0, pData);
                MemoryUtil.memCopy(MemoryUtil.memAddress(verticesBuffer), pData.get(0), requiredSize);
                vkUnmapMemory(device, fontVertexMemory);
            }

            LongBuffer pBuffers = stack.longs(fontVertexBuffer);
            LongBuffer pOffsets = stack.longs(0);
            vkCmdBindVertexBuffers(commandBuffer, 0, pBuffers, pOffsets);

            System.out.println("[FontRenderer Debug] vkCmdDraw 実行. 頂点数: " + (validCharCount * 6));
            vkCmdDraw(commandBuffer, validCharCount * 6, 1, 0, 0);
        }
    }

    private int intDebugCharCount(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= FIRST_CHAR && c < FIRST_CHAR + NUM_CHARS) {
                count++;
            }
        }
        return count;
    }

    private void ensureVertexBufferSize(long requiredSize) {
        if (vertexBufferCapacity >= requiredSize) return;

        System.out.println("[FontRenderer Debug] 頂点バッファを拡張します。新サイズ要求: " + requiredSize);

        if (fontVertexBuffer != 0) {
            vkDestroyBuffer(device, fontVertexBuffer, null);
            vkFreeMemory(device, fontVertexMemory, null);
        }

//        vertexBufferCapacity = Math.max(requiredSize, 4096);
        vertexBufferCapacity = 1024 * 4 * 4 * Float.BYTES; // 1024文字分の容量を確保

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                    .size(vertexBufferCapacity)
                    .usage(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

            LongBuffer pBuffer = stack.mallocLong(1);
            vkCreateBuffer(device, bufferInfo, null, pBuffer);
            fontVertexBuffer = pBuffer.get(0);

            VkMemoryRequirements memReqs = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(device, fontVertexBuffer, memReqs);

            int memType = findMemoryType(memReqs.memoryTypeBits(),
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);

            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                    .allocationSize(memReqs.size())
                    .memoryTypeIndex(memType);

            LongBuffer pMemory = stack.mallocLong(1);
            vkAllocateMemory(device, allocInfo, null, pMemory);
            fontVertexMemory = pMemory.get(0);

            vkBindBufferMemory(device, fontVertexBuffer, fontVertexMemory, 0);
        }
    }

    private int findMemoryType(int typeFilter, int properties) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceMemoryProperties memProperties = VkPhysicalDeviceMemoryProperties.calloc(stack);
            vkGetPhysicalDeviceMemoryProperties(physicalDevice, memProperties);
            for (int i = 0; i < memProperties.memoryTypeCount(); i++) {
                if ((typeFilter & (1 << i)) != 0 && (memProperties.memoryTypes(i).propertyFlags() & properties) == properties) {
                    return i;
                }
            }
            System.err.println("[FontRenderer Error] 適切なメモリタイプが見つかりませんでした。typeFilter: " + typeFilter + ", properties: " + properties);
            throw new RuntimeException("適切なメモリタイプが見つかりませんでした。");
        }
    }

    private long createShaderModule(ByteBuffer code) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
                    .pCode(code);
            LongBuffer pModule = stack.mallocLong(1);
            int err = vkCreateShaderModule(device, createInfo, null, pModule);
            if (err != VK_SUCCESS) {
                System.err.println("[FontRenderer Error] シェーダーモジュールの作成に失敗しました. Error code: " + err);
            }
            return pModule.get(0);
        }
    }

    private LongBuffer culLongBuffer(long value) {
        MemoryStack stack = MemoryStack.stackGet();
        LongBuffer buf = stack.mallocLong(1);
        buf.put(0, value);
        return buf;
    }

    private void transitionImageLayout(long commandPool, VkQueue queue, long image, int format, int oldLayout, int newLayout) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandPool(commandPool)
                    .commandBufferCount(1);

            PointerBuffer pCommandBuffers = stack.mallocPointer(1);
            vkAllocateCommandBuffers(device, allocInfo, pCommandBuffers);
            VkCommandBuffer commandBuffer = new VkCommandBuffer(pCommandBuffers.get(0), device);

            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                    .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            vkBeginCommandBuffer(commandBuffer, beginInfo);

            VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                    .oldLayout(oldLayout)
                    .newLayout(newLayout)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(image);
            barrier.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
            barrier.subresourceRange().baseMipLevel(0);
            barrier.subresourceRange().levelCount(1);
            barrier.subresourceRange().baseArrayLayer(0);
            barrier.subresourceRange().layerCount(1);

            int sourceStage = 0;
            int destinationStage = 0;

            if (oldLayout == VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
                barrier.srcAccessMask(0);
                barrier.dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
                sourceStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
                destinationStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
            } else if (oldLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL && newLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
                barrier.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
                barrier.dstAccessMask(VK_ACCESS_SHADER_READ_BIT);
                sourceStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
                destinationStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
            }

            vkCmdPipelineBarrier(commandBuffer, sourceStage, destinationStage, 0, null, null, barrier);
            vkEndCommandBuffer(commandBuffer);

            VkSubmitInfo.Buffer submitInfo = VkSubmitInfo.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                    .pCommandBuffers(pCommandBuffers);

            vkQueueSubmit(queue, submitInfo, VK_NULL_HANDLE);
            vkQueueWaitIdle(queue);

            vkFreeCommandBuffers(device, commandPool, pCommandBuffers);
        }
    }

    private void copyBufferToImage(long commandPool, VkQueue queue, long buffer, long image, int width, int height) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandPool(commandPool)
                    .commandBufferCount(1);

            PointerBuffer pCommandBuffers = stack.mallocPointer(1);
            vkAllocateCommandBuffers(device, allocInfo, pCommandBuffers);
            VkCommandBuffer commandBuffer = new VkCommandBuffer(pCommandBuffers.get(0), device);

            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                    .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            vkBeginCommandBuffer(commandBuffer, beginInfo);

            VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1, stack);
            region.bufferOffset(0);
            region.bufferRowLength(0);
            region.bufferImageHeight(0);
            region.imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
            region.imageSubresource().mipLevel(0);
            region.imageSubresource().baseArrayLayer(0);
            region.imageSubresource().layerCount(1);
            region.imageOffset().set(0, 0, 0);
            region.imageExtent().set(width, height, 1);

            vkCmdCopyBufferToImage(commandBuffer, buffer, image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);
            vkEndCommandBuffer(commandBuffer);

            VkSubmitInfo.Buffer submitInfo = VkSubmitInfo.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                    .pCommandBuffers(pCommandBuffers);

            vkQueueSubmit(queue, submitInfo, VK_NULL_HANDLE);
            vkQueueWaitIdle(queue);

            vkFreeCommandBuffers(device, commandPool, pCommandBuffers);
        }
    }

    public void cleanup() {
        System.out.println("[FontRenderer Debug] cleanup 呼び出し");
        if (graphicsPipeline != 0) vkDestroyPipeline(device, graphicsPipeline, null);
        if (pipelineLayout != 0) vkDestroyPipelineLayout(device, pipelineLayout, null);
        if (descriptorPool != 0) vkDestroyDescriptorPool(device, descriptorPool, null);
        if (descriptorSetLayout != 0) vkDestroyDescriptorSetLayout(device, descriptorSetLayout, null);
        if (fontSampler != 0) vkDestroySampler(device, fontSampler, null);
        if (fontImageView != 0) vkDestroyImageView(device, fontImageView, null);
        if (fontImage != 0) {
            vkDestroyImage(device, fontImage, null);
            vkFreeMemory(device, fontImageMemory, null);
        }
        if (fontVertexBuffer != 0) {
            vkDestroyBuffer(device, fontVertexBuffer, null);
            vkFreeMemory(device, fontVertexMemory, null);
        }
        if (cdata != null) cdata.free();
    }
}