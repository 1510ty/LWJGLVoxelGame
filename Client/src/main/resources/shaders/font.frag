#version 450

layout (location = 0) in vec2 TexCoord;
layout (location = 0) out vec4 FragColor;

layout (binding = 0) uniform sampler2D tex;

layout (push_constant) uniform FragPushConsts {
    layout (offset = 64) vec3 textColor;
} fpc;

void main() {
    float alpha = texture(tex, TexCoord).r;
    if (alpha < 0.1) discard;
    FragColor = vec4(fpc.textColor, alpha);
}