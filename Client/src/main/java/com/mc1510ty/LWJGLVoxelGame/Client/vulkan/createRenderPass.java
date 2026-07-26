package com.mc1510ty.LWJGLVoxelGame.Client.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;

public class createRenderPass {
    public long create(VkDevice device, int swapchainImageFormat) {
        IO.println("レンダーパスの作成を開始します");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // カラーアタッチメントの設定（描画先のフォーマットやクリア処理など）
            VkAttachmentDescription.Buffer colorAttachment = VkAttachmentDescription.calloc(1, stack);
            colorAttachment.format(swapchainImageFormat);
            colorAttachment.samples(VK_SAMPLE_COUNT_1_BIT);
            colorAttachment.loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR); // 描画前に画面をクリアする
            colorAttachment.storeOp(VK_ATTACHMENT_STORE_OP_STORE); // 描画結果を保持して画面に表示する
            colorAttachment.stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE);
            colorAttachment.stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE);
            colorAttachment.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            colorAttachment.finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR); // スワップチェーンで表示するためのレイアウト

            // アタッチメントの参照
            VkAttachmentReference.Buffer colorAttachmentRef = VkAttachmentReference.calloc(1, stack);
            colorAttachmentRef.attachment(0);
            colorAttachmentRef.layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

            // サブパスの設定
            VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack);
            subpass.pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS);
            subpass.colorAttachmentCount(1);
            subpass.pColorAttachments(colorAttachmentRef);

            // サブパスの依存関係（レイアウト移行のタイミングを調整）
            VkSubpassDependency.Buffer dependency = VkSubpassDependency.calloc(1, stack);
            dependency.srcSubpass(VK_SUBPASS_EXTERNAL);
            dependency.dstSubpass(0);
            dependency.srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
            dependency.srcAccessMask(0);
            dependency.dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
            dependency.dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);

            // レンダーパス全体の作成情報
            VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.calloc(stack);
            renderPassInfo.sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO);
            renderPassInfo.pAttachments(colorAttachment);
            renderPassInfo.pSubpasses(subpass);        // ここをセットすればカウントも自動で入ります
            renderPassInfo.pDependencies(dependency);  // こちらも同様です

            LongBuffer pRenderPass = stack.mallocLong(1);
            int result = vkCreateRenderPass(device, renderPassInfo, null, pRenderPass);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("レンダーパスの作成に失敗しました: " + result);
            }
            IO.println("レンダーパスの作成完了");
            return pRenderPass.get(0);
        }
    }
}