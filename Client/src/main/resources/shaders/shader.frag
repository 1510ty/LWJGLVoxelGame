#version 460

// 頂点シェーダーから受け取る変数
layout(location = 0) in vec4 fragColor;

layout(location = 0) out vec4 outColor;

void main() {
    outColor = fragColor;
}