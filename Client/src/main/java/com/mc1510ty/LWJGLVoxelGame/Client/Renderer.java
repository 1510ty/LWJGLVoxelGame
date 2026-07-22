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

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class Renderer {
    private int shaderProgram;
    private int vao;
    private FloatBuffer matrixBuffer;

    private int uiVao;
    private int uiVbo;
    private int uiShaderProgram;

    public Renderer() {
        setupBuffersAndShaders();
        initUI(); // ここでUI用の初期化を呼び出すように追加
        matrixBuffer = MemoryUtil.memAllocFloat(16);
    }

    public void initUI() {
        float[] vertices = {
                0.0f, 1.0f,
                0.0f, 0.0f,
                1.0f, 0.0f,
                1.0f, 0.0f,
                1.0f, 1.0f,
                0.0f, 1.0f
        };

        uiVao = glGenVertexArrays();
        glBindVertexArray(uiVao);
        uiVbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, uiVbo);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);

        glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);

        String vsSource = "#version 330 core\n" +
                "layout (location = 0) in vec2 aPos;\n" +
                "uniform mat4 projection;\n" +
                "uniform vec2 position;\n" +
                "uniform vec2 scale;\n" +
                "void main() {\n" +
                "   vec2 pos = aPos * scale + position;\n" +
                "   gl_Position = projection * vec4(pos, 0.0, 1.0);\n" +
                "}";

        String fsSource = "#version 330 core\n" +
                "out vec4 FragColor;\n" +
                "uniform vec3 color;\n" +
                "void main() {\n" +
                "   FragColor = vec4(color, 1.0);\n" +
                "}";

        int vs = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vs, vsSource);
        glCompileShader(vs);
        int fs = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fs, fsSource);
        glCompileShader(fs);

        uiShaderProgram = glCreateProgram();
        glAttachShader(uiShaderProgram, vs);
        glAttachShader(uiShaderProgram, fs);
        glLinkProgram(uiShaderProgram);
        glDeleteShader(vs);
        glDeleteShader(fs);
    }

    public void renderButton(Button button, boolean isHovered, int screenWidth, int screenHeight) {
        glUseProgram(uiShaderProgram);
        glBindVertexArray(uiVao);

        Matrix4f ortho = new Matrix4f().ortho2D(0, screenWidth, screenHeight, 0);
        int projLoc = glGetUniformLocation(uiShaderProgram, "projection");

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(16);
            ortho.get(fb);
            glUniformMatrix4fv(projLoc, false, fb);
        }

        glUniform2f(glGetUniformLocation(uiShaderProgram, "position"), button.getX(), button.getY());
        glUniform2f(glGetUniformLocation(uiShaderProgram, "scale"), button.getWidth(), button.getHeight());

        if (isHovered) {
            glUniform3f(glGetUniformLocation(uiShaderProgram, "color"), 0.4f, 0.4f, 0.4f);
        } else {
            glUniform3f(glGetUniformLocation(uiShaderProgram, "color"), 0.2f, 0.2f, 0.2f);
        }

        // 深度テストとカリングを無効化して、確実に2Dを描画する
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE); // ←ここを追加！

        glDrawArrays(GL_TRIANGLES, 0, 6);

        glEnable(GL_CULL_FACE);  // ←元に戻す
        glEnable(GL_DEPTH_TEST);
    }

    private void setupBuffersAndShaders() {
        float[] topColor   = {0.3f, 0.75f, 0.3f};
        float[] sideColor  = {0.55f, 0.35f, 0.15f};
        float[] bottomColor = {0.4f, 0.25f, 0.1f};

        float size = 0.5f;
        float[] vertices = {
                -size, -size,  size,  sideColor[0], sideColor[1], sideColor[2],
                size, -size,  size,  sideColor[0], sideColor[1], sideColor[2],
                size,  size,  size,  sideColor[0], sideColor[1], sideColor[2],
                size,  size,  size,  sideColor[0], sideColor[1], sideColor[2],
                -size,  size,  size,  sideColor[0], sideColor[1], sideColor[2],
                -size, -size,  size,  sideColor[0], sideColor[1], sideColor[2],

                -size, -size, -size,  sideColor[0], sideColor[1], sideColor[2],
                -size,  size, -size,  sideColor[0], sideColor[1], sideColor[2],
                size,  size, -size,  sideColor[0], sideColor[1], sideColor[2],
                size,  size, -size,  sideColor[0], sideColor[1], sideColor[2],
                size, -size, -size,  sideColor[0], sideColor[1], sideColor[2],
                -size, -size, -size,  sideColor[0], sideColor[1], sideColor[2],

                -size,  size,  size,  sideColor[0], sideColor[1], sideColor[2],
                -size,  size, -size,  sideColor[0], sideColor[1], sideColor[2],
                -size, -size, -size,  sideColor[0], sideColor[1], sideColor[2],
                -size, -size, -size,  sideColor[0], sideColor[1], sideColor[2],
                -size, -size,  size,  sideColor[0], sideColor[1], sideColor[2],
                -size,  size,  size,  sideColor[0], sideColor[1], sideColor[2],

                size,  size,  size,  sideColor[0], sideColor[1], sideColor[2],
                size, -size,  size,  sideColor[0], sideColor[1], sideColor[2],
                size, -size, -size,  sideColor[0], sideColor[1], sideColor[2],
                size, -size, -size,  sideColor[0], sideColor[1], sideColor[2],
                size,  size, -size,  sideColor[0], sideColor[1], sideColor[2],
                size,  size,  size,  sideColor[0], sideColor[1], sideColor[2],

                -size,  size,  size,  topColor[0], topColor[1], topColor[2],
                size,  size,  size,  topColor[0], topColor[1], topColor[2],
                size,  size, -size,  topColor[0], topColor[1], topColor[2],
                size,  size, -size,  topColor[0], topColor[1], topColor[2],
                -size,  size, -size,  topColor[0], topColor[1], topColor[2],
                -size,  size,  size,  topColor[0], topColor[1], topColor[2],

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

        // UI用のリソース解放を追加
        glDeleteProgram(uiShaderProgram);
        glDeleteVertexArrays(uiVao);
        glDeleteBuffers(uiVbo);

        if (matrixBuffer != null) {
            MemoryUtil.memFree(matrixBuffer);
        }
    }
}