#version 450

// 頂点バッファを使わずに、コード内で直接三角形の座標を定義する
vec2 positions[3] = vec2[](
    vec2(0.0, -0.5), // 上
    vec2(0.5, 0.5),  // 右下
    vec2(-0.5, 0.5)  // 左下
);

void main() {
    gl_Position = vec4(positions[gl_VertexIndex], 0.0, 1.0);
}