#version 460

layout(location = 0) out vec4 fragColor;

//vec2 positions[6] = vec2[](
//    // 1つ目の三角形（左側に配置）
//    vec2(0, 0.5),
//    vec2(0.3, -0.3),
//    vec2(-0.3, -0.3),
//
//    vec2(0.5, 0.5),
//    vec2(0.8, -0.3),
//    vec2(0.2, -0.3)
//);

vec2 positions[6] = vec2[](
    // 1つ目の三角形（左側に配置）
    vec2(0, 0), //左上
    vec2(0.3, -0.3), //右下
    vec2(0, -0.3), //左下

    vec2(0.5, 0.5),
    vec2(0.8, -0.3),
    vec2(0.2, -0.3)
);



void main() {
    // 現在の頂点位置を取得
    vec2 pos = positions[gl_VertexIndex];

    pos.y = -pos.y;

    gl_Position = vec4(pos, 0.0, 1.0);

    if (gl_VertexIndex == 0) {
        fragColor = vec4(1.0, 0.0, 0.0, 1.0); //赤
    } else if (gl_VertexIndex == 1) {
        fragColor = vec4(0.0, 1.0, 0.0, 1.0); //緑
    } else if (gl_VertexIndex == 2) {
        fragColor = vec4(0.0, 0.0, 1.0, 1.0); //青


    } else if (gl_VertexIndex == 3) {
        fragColor = vec4(0.0, 1.0, 0.0, 1.0); //緑
    } else if (gl_VertexIndex == 4) {
        fragColor = vec4(0.0, 0.0, 1.0, 1.0); //青
    } else if (gl_VertexIndex == 5) {
        fragColor = vec4(1.0, 0.0, 0.0, 1.0); //赤
    }
}