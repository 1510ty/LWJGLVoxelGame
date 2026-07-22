package com.mc1510ty.LWJGLVoxelGame.Client;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;
import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class Renderer {
    private int shaderProgram;
    private int vao;
    private FloatBuffer matrixBuffer;

    public Renderer() {
        setupBuffersAndShaders();
        matrixBuffer = MemoryUtil.memAllocFloat(16);
    }

    private void setupBuffersAndShaders() {
        float[] topColor   = {0.3f, 0.75f, 0.3f};
        float[] sideColor  = {0.55f, 0.35f, 0.15f};
        float[] bottomColor = {0.4f, 0.25f, 0.1f};

        float size = 0.5f;
        float[] vertices = {
                // 前面
                -size, -size,  size,  sideColor[0], sideColor[1], sideColor[2],
                size, -size,  size,  sideColor[0], sideColor[1], sideColor[2],
                size,  size,  size,  sideColor[0], sideColor[1], sideColor[2],
                size,  size,  size,  sideColor[0], sideColor[1], sideColor[2],
                -size,  size,  size,  sideColor[0], sideColor[1], sideColor[2],
                -size, -size,  size,  sideColor[0], sideColor[1], sideColor[2],

                // 後面
                -size, -size, -size,  sideColor[0], sideColor[1], sideColor[2],
                -size,  size, -size,  sideColor[0], sideColor[1], sideColor[2],
                size,  size, -size,  sideColor[0], sideColor[1], sideColor[2],
                size,  size, -size,  sideColor[0], sideColor[1], sideColor[2],
                size, -size, -size,  sideColor[0], sideColor[1], sideColor[2],
                -size, -size, -size,  sideColor[0], sideColor[1], sideColor[2],

                // 左面
                -size,  size,  size,  sideColor[0], sideColor[1], sideColor[2],
                -size,  size, -size,  sideColor[0], sideColor[1], sideColor[2],
                -size, -size, -size,  sideColor[0], sideColor[1], sideColor[2],
                -size, -size, -size,  sideColor[0], sideColor[1], sideColor[2],
                -size, -size,  size,  sideColor[0], sideColor[1], sideColor[2],
                -size,  size,  size,  sideColor[0], sideColor[1], sideColor[2],

                // 右面
                size,  size,  size,  sideColor[0], sideColor[1], sideColor[2],
                size, -size,  size,  sideColor[0], sideColor[1], sideColor[2],
                size, -size, -size,  sideColor[0], sideColor[1], sideColor[2],
                size, -size, -size,  sideColor[0], sideColor[1], sideColor[2],
                size,  size, -size,  sideColor[0], sideColor[1], sideColor[2],
                size,  size,  size,  sideColor[0], sideColor[1], sideColor[2],

                // 上面
                -size,  size,  size,  topColor[0], topColor[1], topColor[2],
                size,  size,  size,  topColor[0], topColor[1], topColor[2],
                size,  size, -size,  topColor[0], topColor[1], topColor[2],
                size,  size, -size,  topColor[0], topColor[1], topColor[2],
                -size,  size, -size,  topColor[0], topColor[1], topColor[2],
                -size,  size,  size,  topColor[0], topColor[1], topColor[2],

                // 下面
                -size, -size,  size,  bottomColor[0], bottomColor[1], bottomColor[2],
                -size, -size, -size,  bottomColor[0], bottomColor[1], bottomColor[2],
                size, -size, -size,  bottomColor[0], bottomColor[1], bottomColor[2],
                size, -size, -size,  bottomColor[0], bottomColor[1], bottomColor[2],
                size, -size,  size,  bottomColor[0], bottomColor[1], bottomColor[2],
                -size, -size,  size,  bottomColor[0], bottomColor[1], bottomColor[2]
        };

        vao = glGenVertexArrays();
        glBindVertexArray(vao);
        int vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);

        int stride = 6 * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);

        String vsSource = "#version 330 core\n" +
                "layout (location = 0) in vec3 aPos;\n" +
                "layout (location = 1) in vec3 aColor;\n" +
                "uniform mat4 mvp;\n" +
                "out vec3 ourColor;\n" +
                "void main() {\n" +
                "   gl_Position = mvp * vec4(aPos, 1.0);\n" +
                "   ourColor = aColor;\n" +
                "}";

        String fsSource = "#version 330 core\n" +
                "in vec3 ourColor;\n" +
                "out vec4 FragColor;\n" +
                "void main() {\n" +
                "   FragColor = vec4(ourColor, 1.0);\n" +
                "}";

        int vs = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vs, vsSource);
        glCompileShader(vs);
        int fs = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fs, fsSource);
        glCompileShader(fs);

        shaderProgram = glCreateProgram();
        glAttachShader(shaderProgram, vs);
        glAttachShader(shaderProgram, fs);
        glLinkProgram(shaderProgram);
        glDeleteShader(vs);
        glDeleteShader(fs);
    }

    // 毎フレーム呼ばれる描画処理
    public void render(World world, Camera camera, Matrix4f projection) {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        glUseProgram(shaderProgram);
        glBindVertexArray(vao);

        Matrix4f view = camera.getViewMatrix();
        Matrix4f mvp = new Matrix4f();
        Matrix4f model = new Matrix4f();

        for (int x = 0; x < World.SIZE_X; x++) {
            for (int y = 0; y < World.SIZE_Y; y++) {
                for (int z = 0; z < World.SIZE_Z; z++) {
                    if (world.getBlock(x, y, z) > 0) {
                        model.identity().translation(x, y, z);

                        // プロジェクション行列 × ビュー行列 × モデル行列
                        projection.mul(view, mvp);
                        mvp.mul(model);

                        mvp.get(matrixBuffer);
                        int mvpLocation = glGetUniformLocation(shaderProgram, "mvp");
                        glUniformMatrix4fv(mvpLocation, false, matrixBuffer);

                        glDrawArrays(GL_TRIANGLES, 0, 36);
                    }
                }
            }
        }
    }

    public void cleanup() {
        glDeleteProgram(shaderProgram);
        glDeleteVertexArrays(vao);
        if (matrixBuffer != null) {
            MemoryUtil.memFree(matrixBuffer);
        }
    }
}