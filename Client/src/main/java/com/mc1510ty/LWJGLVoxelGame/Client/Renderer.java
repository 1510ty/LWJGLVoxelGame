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

import java.nio.DoubleBuffer;
import java.util.Map;

import static org.lwjgl.opengl.ARBGPUShaderFP64.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class Renderer {
    private int shaderProgram;

    // 草ブロック用
    private int vao;
    private int vbo;

    // 石ブロック用を追加
    private int stoneVao;
    private int stoneVbo;

    private int playerVao;
    private int playerVbo;

    private DoubleBuffer matrixBuffer;

    private int uiVao;
    private int uiVbo;
    private int uiShaderProgram;

    public Renderer() {
        setupBuffersAndShaders();
        initUI();
        matrixBuffer = MemoryUtil.memAllocDouble(16);
    }

    public void initUI() {
        double[] vertices = {
                0.0d, 1.0d,
                0.0d, 0.0d,
                1.0d, 0.0d,
                1.0d, 0.0d,
                1.0d, 1.0d,
                0.0d, 1.0d
        };

        uiVao = glGenVertexArrays();
        glBindVertexArray(uiVao);
        uiVbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, uiVbo);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);

        glVertexAttribPointer(0, 2, GL_DOUBLE, false, 2 * Double.BYTES, 0);
        glEnableVertexAttribArray(0);

        String vsSource = "#version 410 core\n" +
                "layout (location = 0) in dvec2 aPos;\n" +
                "uniform dmat4 projection;\n" +
                "uniform dvec2 position;\n" +
                "uniform dvec2 scale;\n" +
                "void main() {\n" +
                "   dvec2 pos = aPos * scale + position;\n" +
                "   gl_Position = vec4(projection * dvec4(pos, 0.0, 1.0));\n" +
                "}";

        String fsSource = "#version 410 core\n" +
                "out vec4 FragColor;\n" +
                "uniform vec3 textColor;\n" +
                "void main() {\n" +
                "   FragColor = vec4(textColor, 1.0);\n" +
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

    public void renderCrosshair(int screenWidth, int screenHeight) {
        glUseProgram(uiShaderProgram);
        glBindVertexArray(uiVao);

        Matrix4d ortho = new Matrix4d().ortho2D(0, screenWidth, screenHeight, 0);
        int projLoc = glGetUniformLocation(uiShaderProgram, "projection");

        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer fb = stack.mallocDouble(16);
            ortho.get(fb);
            glUniformMatrix4dv(projLoc, false, fb);
        }

        double centerX = screenWidth / 2.0;
        double centerY = screenHeight / 2.0;
        double size = 10.0;
        double thickness = 2.0;

        int colorLoc = glGetUniformLocation(uiShaderProgram, "textColor");
        glUniform3f(colorLoc, 1.0f, 1.0f, 1.0f);

        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);

        glUniform2d(glGetUniformLocation(uiShaderProgram, "position"), centerX - size, centerY - thickness / 2.0);
        glUniform2d(glGetUniformLocation(uiShaderProgram, "scale"), size * 2.0, thickness);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        glUniform2d(glGetUniformLocation(uiShaderProgram, "position"), centerX - thickness / 2.0, centerY - size);
        glUniform2d(glGetUniformLocation(uiShaderProgram, "scale"), thickness, size * 2.0);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        glEnable(GL_CULL_FACE);
        glEnable(GL_DEPTH_TEST);
    }

    public void renderButton(Button button, boolean isHovered, int screenWidth, int screenHeight) {
        glUseProgram(uiShaderProgram);
        glBindVertexArray(uiVao);

        Matrix4d ortho = new Matrix4d().ortho2D(0, screenWidth, screenHeight, 0);
        int projLoc = glGetUniformLocation(uiShaderProgram, "projection");

        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer fb = stack.mallocDouble(16);
            ortho.get(fb);
            glUniformMatrix4dv(projLoc, false, fb);
        }

        glUniform2d(glGetUniformLocation(uiShaderProgram, "position"), button.getX(), button.getY());
        glUniform2d(glGetUniformLocation(uiShaderProgram, "scale"), button.getWidth(), button.getHeight());

        int colorLoc = glGetUniformLocation(uiShaderProgram, "textColor");
        if (isHovered) {
            glUniform3f(colorLoc, 0.4f, 0.4f, 0.4f);
        } else {
            glUniform3f(colorLoc, 0.2f, 0.2f, 0.2f);
        }

        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);

        glDrawArrays(GL_TRIANGLES, 0, 6);

        glEnable(GL_CULL_FACE);
        glEnable(GL_DEPTH_TEST);
    }

    private void setupBuffersAndShaders() {
        double size = 0.5d;
        int stride = 6 * Double.BYTES;

        // --- 1. 草ブロック用の頂点データ ---
        double[] topColor    = {0.3d, 0.75d, 0.3d};
        double[] sideColor   = {0.55d, 0.35d, 0.15d};
        double[] bottomColor = {0.4d, 0.25d, 0.1d};

        double[] vertices = {
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
        vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);

        glVertexAttribPointer(0, 3, GL_DOUBLE, false, stride, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_DOUBLE, false, stride, 3 * Double.BYTES);
        glEnableVertexAttribArray(1);


        // --- 2. 石ブロック用の頂点データ（灰色） ---
        double[] stoneColor = {0.5d, 0.5d, 0.5d}; // 全面共通の落ち着いた灰色
        double[] stoneVertices = {
                -size, -size,  size,  stoneColor[0], stoneColor[1], stoneColor[2],
                size, -size,  size,  stoneColor[0], stoneColor[1], stoneColor[2],
                size,  size,  size,  stoneColor[0], stoneColor[1], stoneColor[2],
                size,  size,  size,  stoneColor[0], stoneColor[1], stoneColor[2],
                -size,  size,  size,  stoneColor[0], stoneColor[1], stoneColor[2],
                -size, -size,  size,  stoneColor[0], stoneColor[1], stoneColor[2],

                -size, -size, -size,  stoneColor[0], stoneColor[1], stoneColor[2],
                -size,  size, -size,  stoneColor[0], stoneColor[1], stoneColor[2],
                size,  size, -size,  stoneColor[0], stoneColor[1], stoneColor[2],
                size,  size, -size,  stoneColor[0], stoneColor[1], stoneColor[2],
                size, -size, -size,  stoneColor[0], stoneColor[1], stoneColor[2],
                -size, -size, -size,  stoneColor[0], stoneColor[1], stoneColor[2],

                -size,  size,  size,  stoneColor[0], stoneColor[1], stoneColor[2],
                -size,  size, -size,  stoneColor[0], stoneColor[1], stoneColor[2],
                -size, -size, -size,  stoneColor[0], stoneColor[1], stoneColor[2],
                -size, -size, -size,  stoneColor[0], stoneColor[1], stoneColor[2],
                -size, -size,  size,  stoneColor[0], stoneColor[1], stoneColor[2],
                -size,  size,  size,  stoneColor[0], stoneColor[1], stoneColor[2],

                size,  size,  size,  stoneColor[0], stoneColor[1], stoneColor[2],
                size, -size,  size,  stoneColor[0], stoneColor[1], stoneColor[2],
                size, -size, -size,  stoneColor[0], stoneColor[1], stoneColor[2],
                size, -size, -size,  stoneColor[0], stoneColor[1], stoneColor[2],
                size,  size, -size,  stoneColor[0], stoneColor[1], stoneColor[2],
                size,  size,  size,  stoneColor[0], stoneColor[1], stoneColor[2],

                -size,  size,  size,  stoneColor[0], stoneColor[1], stoneColor[2],
                size,  size,  size,  stoneColor[0], stoneColor[1], stoneColor[2],
                size,  size, -size,  stoneColor[0], stoneColor[1], stoneColor[2],
                size,  size, -size,  stoneColor[0], stoneColor[1], stoneColor[2],
                -size,  size, -size,  stoneColor[0], stoneColor[1], stoneColor[2],
                -size,  size,  size,  stoneColor[0], stoneColor[1], stoneColor[2],

                -size, -size,  size,  stoneColor[0], stoneColor[1], stoneColor[2],
                -size, -size, -size,  stoneColor[0], stoneColor[1], stoneColor[2],
                size, -size, -size,  stoneColor[0], stoneColor[1], stoneColor[2],
                size, -size, -size,  stoneColor[0], stoneColor[1], stoneColor[2],
                size, -size,  size,  stoneColor[0], stoneColor[1], stoneColor[2],
                -size, -size,  size,  stoneColor[0], stoneColor[1], stoneColor[2]
        };

        stoneVao = glGenVertexArrays();
        glBindVertexArray(stoneVao);
        stoneVbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, stoneVbo);
        glBufferData(GL_ARRAY_BUFFER, stoneVertices, GL_STATIC_DRAW);

        glVertexAttribPointer(0, 3, GL_DOUBLE, false, stride, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_DOUBLE, false, stride, 3 * Double.BYTES);
        glEnableVertexAttribArray(1);


        // --- 3. プレイヤー用の頂点データ ---
        double[] pTopColor    = {0.1d, 0.5d, 0.9d};
        double[] pSideColor   = {0.2d, 0.4d, 0.8d};
        double[] pBottomColor = {0.05d, 0.2d, 0.5d};

        double[] playerVertices = {
                -size, -size,  size,  pSideColor[0], pSideColor[1], pSideColor[2],
                size, -size,  size,  pSideColor[0], pSideColor[1], pSideColor[2],
                size,  size,  size,  pSideColor[0], pSideColor[1], pSideColor[2],
                size,  size,  size,  pSideColor[0], pSideColor[1], pSideColor[2],
                -size,  size,  size,  pSideColor[0], pSideColor[1], pSideColor[2],
                -size, -size,  size,  pSideColor[0], pSideColor[1], pSideColor[2],

                -size, -size, -size,  pSideColor[0], pSideColor[1], pSideColor[2],
                -size,  size, -size,  pSideColor[0], pSideColor[1], pSideColor[2],
                size,  size, -size,  pSideColor[0], pSideColor[1], pSideColor[2],
                size,  size, -size,  pSideColor[0], pSideColor[1], pSideColor[2],
                size, -size, -size,  pSideColor[0], pSideColor[1], pSideColor[2],
                -size, -size, -size,  pSideColor[0], pSideColor[1], pSideColor[2],

                -size,  size,  size,  pSideColor[0], pSideColor[1], pSideColor[2],
                -size,  size, -size,  pSideColor[0], pSideColor[1], pSideColor[2],
                -size, -size, -size,  pSideColor[0], pSideColor[1], pSideColor[2],
                -size, -size, -size,  pSideColor[0], pSideColor[1], pSideColor[2],
                -size, -size,  size,  pSideColor[0], pSideColor[1], pSideColor[2],
                -size,  size,  size,  pSideColor[0], pSideColor[1], pSideColor[2],

                size,  size,  size,  pSideColor[0], pSideColor[1], pSideColor[2],
                size, -size,  size,  pSideColor[0], pSideColor[1], pSideColor[2],
                size, -size, -size,  pSideColor[0], pSideColor[1], pSideColor[2],
                size, -size, -size,  pSideColor[0], pSideColor[1], pSideColor[2],
                size,  size, -size,  pSideColor[0], pSideColor[1], pSideColor[2],
                size,  size,  size,  pSideColor[0], pSideColor[1], pSideColor[2],

                -size,  size,  size,  pTopColor[0], pTopColor[1], pTopColor[2],
                size,  size,  size,  pTopColor[0], pTopColor[1], pTopColor[2],
                size,  size, -size,  pTopColor[0], pTopColor[1], pTopColor[2],
                size,  size, -size,  pTopColor[0], pTopColor[1], pTopColor[2],
                -size,  size, -size,  pTopColor[0], pTopColor[1], pTopColor[2],
                -size,  size,  size,  pTopColor[0], pTopColor[1], pTopColor[2],

                -size, -size,  size,  pBottomColor[0], pBottomColor[1], pBottomColor[2],
                -size, -size, -size,  pBottomColor[0], pBottomColor[1], pBottomColor[2],
                size, -size, -size,  pBottomColor[0], pBottomColor[1], pBottomColor[2],
                size, -size, -size,  pBottomColor[0], pBottomColor[1], pBottomColor[2],
                size, -size,  size,  pBottomColor[0], pBottomColor[1], pBottomColor[2],
                -size, -size,  size,  pBottomColor[0], pBottomColor[1], pBottomColor[2]
        };

        playerVao = glGenVertexArrays();
        glBindVertexArray(playerVao);
        playerVbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, playerVbo);
        glBufferData(GL_ARRAY_BUFFER, playerVertices, GL_STATIC_DRAW);

        glVertexAttribPointer(0, 3, GL_DOUBLE, false, stride, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_DOUBLE, false, stride, 3 * Double.BYTES);
        glEnableVertexAttribArray(1);


        // --- シェーダーのコンパイル ---
        String vsSource = "#version 410 core\n" +
                "layout (location = 0) in dvec3 aPos;\n" +
                "layout (location = 1) in dvec3 aColor;\n" +
                "uniform dmat4 mvp;\n" +
                "out vec3 ourColor;\n" +
                "void main() {\n" +
                "   gl_Position = vec4(mvp * dvec4(aPos, 1.0));\n" +
                "   ourColor = vec3(aColor);\n" +
                "}";

        String fsSource = "#version 410 core\n" +
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

    public void render(World world, Camera camera, Matrix4d projection, Map<Long, Vector3d> otherPlayers, BlockNameIDMgr blocknameidmgr) {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        glUseProgram(shaderProgram);

        Matrix4d view = camera.getViewMatrix();
        Matrix4d pv = new Matrix4d();
        projection.mul(view, pv);

        Matrix4d mvp = new Matrix4d();
        Matrix4d model = new Matrix4d();
        int mvpLocation = glGetUniformLocation(shaderProgram, "mvp");

        int stoneId = blocknameidmgr.getId("lwjglvoxelgame:stone");

        // 1. ワールドブロックの描画
        for (int x = 0; x < World.SIZE_X; x++) {
            for (int y = 0; y < World.SIZE_Y; y++) {
                for (int z = 0; z < World.SIZE_Z; z++) {
                    int blockId = world.getBlock(x, y, z);

                    if (blockId != 0) { // 空気(0)以外の場合に描画
                        // ブロックの種類に応じてVAOを切り替える
                        if (blockId == stoneId) {
                            glBindVertexArray(stoneVao);
                        } else {
                            glBindVertexArray(vao); // デフォルト（草ブロックなど）
                        }

                        model.identity().translation(x, y, z);
                        pv.mul(model, mvp);

                        mvp.get(matrixBuffer);
                        glUniformMatrix4dv(mvpLocation, false, matrixBuffer);

                        glDrawArrays(GL_TRIANGLES, 0, 36);
                    }
                }
            }
        }

        // 2. 他のプレイヤー（青い立方体）の描画
        if (otherPlayers != null && !otherPlayers.isEmpty()) {
            glBindVertexArray(playerVao);

            for (org.joml.Vector3d pos : otherPlayers.values()) {
                model.identity().translation(pos.x, pos.y, pos.z);
                pv.mul(model, mvp);

                mvp.get(matrixBuffer);
                glUniformMatrix4dv(mvpLocation, false, matrixBuffer);

                glDrawArrays(GL_TRIANGLES, 0, 36);
            }
        }
    }

    public void cleanup() {
        glDeleteProgram(shaderProgram);
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);

        glDeleteVertexArrays(stoneVao);
        glDeleteBuffers(stoneVbo);

        glDeleteProgram(uiShaderProgram);
        glDeleteVertexArrays(uiVao);
        glDeleteBuffers(uiVbo);

        glDeleteVertexArrays(playerVao);
        glDeleteBuffers(playerVbo);

        if (matrixBuffer != null) {
            MemoryUtil.memFree(matrixBuffer);
        }
    }
}