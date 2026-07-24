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

import com.mc1510ty.LWJGLVoxelGame.common.BlockNameIDMgr;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.Map;

import static org.lwjgl.vulkan.VK10.*;

public class Renderer {
    private final VkDevice device;
    private final VkPhysicalDevice physicalDevice;

    // パイプライン関連
    private long pipelineLayout;
    private long graphicsPipeline;

    // バッファ関連（例：ブロック用頂点バッファ）
    private long blockVertexBuffer;
    private long blockVertexMemory;
    private int blockVertexCount;

    public Renderer(VkDevice device, VkPhysicalDevice physicalDevice, long renderPass, int subpass) {
        this.device = device;
        this.physicalDevice = physicalDevice;

        createBuffers();
        createShadersAndPipeline(renderPass, subpass);
    }

    private void createBuffers() {
        double size = 0.5;
        int stride = 6 * Float.BYTES; // 位置(3) + 色(3) = 6 floats

        // --- 1. 草ブロック用の頂点データ (floatに変換) ---
        float[] topColor    = {0.3f, 0.75f, 0.3f};
        float[] sideColor   = {0.55f, 0.35f, 0.15f};
        float[] bottomColor = {0.4f, 0.25f, 0.1f};

        float[] vertices = {
                (float)-size, (float)-size,  (float)size,  sideColor[0], sideColor[1], sideColor[2],
                (float)size, (float)-size,  (float)size,  sideColor[0], sideColor[1], sideColor[2],
                (float)size,  (float)size,  (float)size,  sideColor[0], sideColor[1], sideColor[2],
                (float)size,  (float)size,  (float)size,  sideColor[0], sideColor[1], sideColor[2],
                (float)-size,  (float)size,  (float)size,  sideColor[0], sideColor[1], sideColor[2],
                (float)-size, (float)-size,  (float)size,  sideColor[0], sideColor[1], sideColor[2],

                (float)-size, (float)-size, (float)-size,  sideColor[0], sideColor[1], sideColor[2],
                (float)-size,  (float)size, (float)-size,  sideColor[0], sideColor[1], sideColor[2],
                (float)size,  (float)size, (float)-size,  sideColor[0], sideColor[1], sideColor[2],
                (float)size,  (float)size, (float)-size,  sideColor[0], sideColor[1], sideColor[2],
                (float)size, (float)-size, (float)-size,  sideColor[0], sideColor[1], sideColor[2],
                (float)-size, (float)-size, (float)-size,  sideColor[0], sideColor[1], sideColor[2],

                (float)-size,  (float)size,  (float)size,  sideColor[0], sideColor[1], sideColor[2],
                (float)-size,  (float)size, (float)-size,  sideColor[0], sideColor[1], sideColor[2],
                (float)-size, (float)-size, (float)-size,  sideColor[0], sideColor[1], sideColor[2],
                (float)-size, (float)-size, (float)-size,  sideColor[0], sideColor[1], sideColor[2],
                (float)-size, (float)-size,  (float)size,  sideColor[0], sideColor[1], sideColor[2],
                (float)-size,  (float)size,  (float)size,  sideColor[0], sideColor[1], sideColor[2],

                (float)size,  (float)size,  (float)size,  sideColor[0], sideColor[1], sideColor[2],
                (float)size, (float)-size,  (float)size,  sideColor[0], sideColor[1], sideColor[2],
                (float)size, (float)-size, (float)-size,  sideColor[0], sideColor[1], sideColor[2],
                (float)size, (float)-size, (float)-size,  sideColor[0], sideColor[1], sideColor[2],
                (float)size,  (float)size, (float)-size,  sideColor[0], sideColor[1], sideColor[2],
                (float)size,  (float)size,  (float)size,  sideColor[0], sideColor[1], sideColor[2],

                (float)-size,  (float)size,  (float)size,  topColor[0], topColor[1], topColor[2],
                (float)size,  (float)size,  (float)size,  topColor[0], topColor[1], topColor[2],
                (float)size,  (float)size, (float)-size,  topColor[0], topColor[1], topColor[2],
                (float)size,  (float)size, (float)-size,  topColor[0], topColor[1], topColor[2],
                (float)-size,  (float)size, (float)-size,  topColor[0], topColor[1], topColor[2],
                (float)-size,  (float)size,  (float)size,  topColor[0], topColor[1], topColor[2],

                (float)-size, (float)-size,  (float)size,  bottomColor[0], bottomColor[1], bottomColor[2],
                (float)-size, (float)-size, (float)-size,  bottomColor[0], bottomColor[1], bottomColor[2],
                (float)size, (float)-size, (float)-size,  bottomColor[0], bottomColor[1], bottomColor[2],
                (float)size, (float)-size, (float)-size,  bottomColor[0], bottomColor[1], bottomColor[2],
                (float)size, (float)-size,  (float)size,  bottomColor[0], bottomColor[1], bottomColor[2],
                (float)-size, (float)-size,  (float)size,  bottomColor[0], bottomColor[1], bottomColor[2]
        };

        long[] bufInfo = createVertexBuffer(vertices);
        blockVertexBuffer = bufInfo[0];
        blockVertexMemory = bufInfo[1];
        blockVertexCount = vertices.length / 6; // 1頂点あたり6要素(位置3+色3)

        // TODO: 石ブロックやプレイヤー用も同様にフィールドを追加して作成していきます
    }

    private void createShadersAndPipeline(long renderPass, int subpass) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // 1. 生成した .spv ファイルをクラスパス（resources）から読み込む
            ByteBuffer vertShaderCode = loadShaderFile("shaders/block.vert.spv");
            ByteBuffer fragShaderCode = loadShaderFile("shaders/block.frag.spv");

            long vertShaderModule = createShaderModule(vertShaderCode);
            long fragShaderModule = createShaderModule(fragShaderCode);


// シェーダーステージの指定（直接バッファの要素に書き込む）
            VkPipelineShaderStageCreateInfo.Buffer shaderStages = VkPipelineShaderStageCreateInfo.calloc(2, stack);

            // 0番目：頂点シェーダー
            shaderStages.get(0)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_VERTEX_BIT)
                    .module(vertShaderModule)
                    .pName(stack.UTF8("main"));

            // 1番目：フラグメントシェーダー
            shaderStages.get(1)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_FRAGMENT_BIT)
                    .module(fragShaderModule)
                    .pName(stack.UTF8("main"));

            // 2. 頂点入力レイアウトの定義（位置: vec3 / 色: vec3 = 合計 6 floats）
            VkVertexInputBindingDescription.Buffer bindingDescription = VkVertexInputBindingDescription.calloc(1, stack)
                    .binding(0)
                    .stride(6 * Float.BYTES)
                    .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

            VkVertexInputAttributeDescription.Buffer attributeDescriptions = VkVertexInputAttributeDescription.calloc(2, stack);
            // location = 0 : aPos (vec3)
            attributeDescriptions.get(0)
                    .binding(0)
                    .location(0)
                    .format(VK_FORMAT_R32G32B32_SFLOAT)
                    .offset(0);
            // location = 1 : aColor (vec3)
            attributeDescriptions.get(1)
                    .binding(0)
                    .location(1)
                    .format(VK_FORMAT_R32G32B32_SFLOAT)
                    .offset(3 * Float.BYTES);

            VkPipelineVertexInputStateCreateInfo vertexInputInfo = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
                    .pVertexBindingDescriptions(bindingDescription)
                    .pVertexAttributeDescriptions(attributeDescriptions);

            // 入力アセンブリ（三角形リスト）
            VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                    .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                    .primitiveRestartEnable(false);

            // ビューポートとシザー（動的ステート）
            VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                    .viewportCount(1)
                    .scissorCount(1);

            // ラスタライザ
            VkPipelineRasterizationStateCreateInfo rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                    .depthClampEnable(false)
                    .rasterizerDiscardEnable(false)
                    .polygonMode(VK_POLYGON_MODE_FILL)
                    .lineWidth(1.0f)
                    .cullMode(VK_CULL_MODE_BACK_BIT)
                    .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
                    .depthBiasEnable(false);

            // マルチサンプリング
            VkPipelineMultisampleStateCreateInfo multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                    .sampleShadingEnable(false)
                    .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);

            // 深度・ステンシルテスト
            VkPipelineDepthStencilStateCreateInfo depthStencil = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
                    .depthTestEnable(true)
                    .depthWriteEnable(true)
                    .depthCompareOp(VK_COMPARE_OP_LESS)
                    .depthBoundsTestEnable(false)
                    .stencilTestEnable(false);

            // カラーブレンド
            VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack)
                    .colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT)
                    .blendEnable(false);

            VkPipelineColorBlendStateCreateInfo colorBlending = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                    .logicOpEnable(false)
                    .pAttachments(colorBlendAttachment);

            // ダイナミックステート（ビューポートとシザー）
            int[] dynamicStates = { VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR };
            VkPipelineDynamicStateCreateInfo dynamicState = VkPipelineDynamicStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO)
                    .pDynamicStates(stack.ints(dynamicStates));

            // プッシュ定数の定義（MVP行列: 4x4 float = 64バイト）
            VkPushConstantRange.Buffer pushConstantRange = VkPushConstantRange.calloc(1, stack)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT)
                    .offset(0)
                    .size(64);

            // パイプラインレイアウトの作成
            VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                    .pPushConstantRanges(pushConstantRange);

            LongBuffer pPipelineLayout = stack.mallocLong(1);
            if (vkCreatePipelineLayout(device, pipelineLayoutInfo, null, pPipelineLayout) != VK_SUCCESS) {
                throw new RuntimeException("パイプラインレイアウトの作成に失敗しました。");
            }
            pipelineLayout = pPipelineLayout.get(0);

            // グラフィックスパイプラインの作成
            VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                    .stageCount(2) // ← ここにシェーダーの数（2）を追加します！
                    .pStages(shaderStages)
                    .pVertexInputState(vertexInputInfo)
                    .pInputAssemblyState(inputAssembly)
                    .pViewportState(viewportState)
                    .pRasterizationState(rasterizer)
                    .pMultisampleState(multisampling)
                    .pDepthStencilState(depthStencil)
                    .pColorBlendState(colorBlending)
                    .pDynamicState(dynamicState)
                    .layout(pipelineLayout)
                    .renderPass(renderPass)
                    .subpass(subpass);


            LongBuffer pPipeline = stack.mallocLong(1);
            if (vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, pipelineInfo, null, pPipeline) != VK_SUCCESS) {
                throw new RuntimeException("グラフィックスパイプラインの作成に失敗しました。");
            }
            graphicsPipeline = pPipeline.get(0);

            // 一時シェーダーモジュールの破棄
            vkDestroyShaderModule(device, vertShaderModule, null);
            vkDestroyShaderModule(device, fragShaderModule, null);
        }
    }

    private long createShaderModule(ByteBuffer byteCode) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
                    .pCode(byteCode);

            LongBuffer pShaderModule = stack.mallocLong(1);
            if (vkCreateShaderModule(device, createInfo, null, pShaderModule) != VK_SUCCESS) {
                throw new RuntimeException("シェーダーモジュールの作成に失敗しました。");
            }
            return pShaderModule.get(0);
        }
    }

    private ByteBuffer loadShaderFile(String path) {
        try (java.io.InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
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

    public void render(VkCommandBuffer commandBuffer, World world, Camera camera, Matrix4d projection, Map<Long, Vector3d> otherPlayers, BlockNameIDMgr blocknameidmgr, int fbWidth, int fbHeight) {
        // 1. パイプラインのバインド
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicsPipeline);

        // --- ダイナミックステートの設定（ビューポートとシザー） ---
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkViewport.Buffer pViewports = VkViewport.calloc(1, stack)
                    .x(0.0f)
                    .y(0.0f)
                    .width(fbWidth)
                    .height(fbHeight)
                    .minDepth(0.0f)
                    .maxDepth(1.0f);
            vkCmdSetViewport(commandBuffer, 0, pViewports);

            VkRect2D.Buffer pScissors = VkRect2D.calloc(1, stack);
            pScissors.offset().set(0, 0);
            pScissors.extent().set(fbWidth, fbHeight);
            vkCmdSetScissor(commandBuffer, 0, pScissors);
        }

        // 2. MVP行列の計算とプッシュ定数（Push Constants）を通じたシェーダーへの送信
        Matrix4d view = camera.getViewMatrix();
        Matrix4d pv = new Matrix4d();
        projection.mul(view, pv);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer pushConstantBuffer = stack.malloc(64); // 4x4 float 行列用 (16 * 4bytes = 64bytes)

            // JOMLの行列をFloatBufferに変換してバッファに書き込む
            org.joml.Matrix4f floatPv = new org.joml.Matrix4f(
                    (float)pv.m00(), (float)pv.m01(), (float)pv.m02(), (float)pv.m03(),
                    (float)pv.m10(), (float)pv.m11(), (float)pv.m12(), (float)pv.m13(),
                    (float)pv.m20(), (float)pv.m21(), (float)pv.m22(), (float)pv.m23(),
                    (float)pv.m30(), (float)pv.m31(), (float)pv.m32(), (float)pv.m33()
            );
            floatPv.get(pushConstantBuffer);

            vkCmdPushConstants(commandBuffer, pipelineLayout, VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstantBuffer);
        }

        // 3. 頂点バッファのバインドと描画コマンドの記録
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer offsets = stack.longs(0);
            LongBuffer pBuffers = stack.longs(blockVertexBuffer);
            vkCmdBindVertexBuffers(commandBuffer, 0, pBuffers, offsets);

            // まずは原点に単体の草ブロックを描画！
            vkCmdDraw(commandBuffer, blockVertexCount, 1, 0, 0);
        }
    }

    public void cleanup() {
        if (graphicsPipeline != 0) vkDestroyPipeline(device, graphicsPipeline, null);
        if (pipelineLayout != 0) vkDestroyPipelineLayout(device, pipelineLayout, null);

        if (blockVertexBuffer != 0) {
            vkDestroyBuffer(device, blockVertexBuffer, null);
            vkFreeMemory(device, blockVertexMemory, null);
        }
    }

    public long[] createVertexBuffer(float[] vertices) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // 1. 頂点データをByteBufferに詰める
            ByteBuffer vertexBuffer = stack.malloc(vertices.length * Float.BYTES);
            for (float v : vertices) {
                vertexBuffer.putFloat(v);
            }
            vertexBuffer.flip();

            long bufferSize = vertexBuffer.remaining();

            // 2. VkBuffer の作成情報設定
            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                    .size(bufferSize)
                    .usage(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

            LongBuffer pBuffer = stack.mallocLong(1);
            if (vkCreateBuffer(device, bufferInfo, null, pBuffer) != VK_SUCCESS) {
                throw new RuntimeException("頂点バッファの作成に失敗しました。");
            }
            long buffer = pBuffer.get(0);

            // 3. メモリ要件の取得
            VkMemoryRequirements memRequirements = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(device, buffer, memRequirements);

            // 4. CPUから書き込み可能なメモリタイプを検索
            int memoryTypeIndex = findMemoryType(
                    memRequirements.memoryTypeBits(),
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
            );

            // 5. メモリの割り当て
            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                    .allocationSize(memRequirements.size());
            allocInfo.memoryTypeIndex(memoryTypeIndex);

            LongBuffer pMemory = stack.mallocLong(1);
            if (vkAllocateMemory(device, allocInfo, null, pMemory) != VK_SUCCESS) {
                throw new RuntimeException("頂点バッファ用メモリの割り当てに失敗しました。");
            }
            long bufferMemory = pMemory.get(0);

            // 6. バッファとメモリの紐付け
            vkBindBufferMemory(device, buffer, bufferMemory, 0);

            // 7. データをGPUメモリへコピー
            org.lwjgl.PointerBuffer pData = stack.mallocPointer(1);
            vkMapMemory(device, bufferMemory, 0, bufferSize, 0, pData);
            MemoryUtil.memCopy(MemoryUtil.memAddress(vertexBuffer), pData.get(0), bufferSize);
            vkUnmapMemory(device, bufferMemory);

            // バッファのハンドルと確保したメモリを返す（後で解放するため）
            return new long[] { buffer, bufferMemory };
        }
    }

    // 利用可能なメモリタイプから条件に合うインデックスを探すヘルパー
    private int findMemoryType(int typeFilter, int properties) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceMemoryProperties memProperties = VkPhysicalDeviceMemoryProperties.calloc(stack);
            vkGetPhysicalDeviceMemoryProperties(physicalDevice, memProperties);

            for (int i = 0; i < memProperties.memoryTypeCount(); i++) {
                if ((typeFilter & (1 << i)) != 0 && (memProperties.memoryTypes(i).propertyFlags() & properties) == properties) {
                    return i;
                }
            }
            throw new RuntimeException("適切なメモリタイプが見つかりませんでした。");
        }
    }
}