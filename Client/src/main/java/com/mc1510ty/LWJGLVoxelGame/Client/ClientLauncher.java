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
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.system.MemoryUtil.memAllocFloat;
import static org.lwjgl.system.MemoryUtil.memFree;

public class ClientLauncher {
    private long window;
    private int shaderProgram;
    private int vao;
    private FloatBuffer matrixBuffer;

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;

    // プレイヤーの位置（初期位置を床の上、少し高めに設定）
    private final Vector3f cameraPos = new Vector3f(8.0f, 2.5f, 8.0f);
    private final Vector3f cameraFront = new Vector3f(0.0f, 0.0f, -1.0f);
    private final Vector3f cameraUp = new Vector3f(0.0f, 1.0f, 0.0f);

    private float yaw = -90.0f;
    private float pitch = -20.0f;
    private boolean firstMouse = true;
    private double lastX = WIDTH / 2.0;
    private double lastY = HEIGHT / 2.0;

    private final boolean[] keys = new boolean[1024];

    private static final int WORLD_SIZE_X = 16;
    private static final int WORLD_SIZE_Y = 4;
    private static final int WORLD_SIZE_Z = 16;
    private final int[][][] worldData = new int[WORLD_SIZE_X][WORLD_SIZE_Y][WORLD_SIZE_Z];

    public void run() {
        init();
        loop();

        glDeleteProgram(shaderProgram);
        glDeleteVertexArrays(vao);
        if (matrixBuffer != null) {
            memFree(matrixBuffer);
        }

        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);

        glfwTerminate();
        GLFWErrorCallback callback = glfwSetErrorCallback(null);
        if (callback != null) {
            callback.free();
        }
    }

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) {
            throw new IllegalStateException("GLFWの初期化に失敗しました。");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

        window = glfwCreateWindow(WIDTH, HEIGHT, "Voxel Game Client - Grass & Collision", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("GLFWウィンドウの作成に失敗しました。");
        }

        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);

        glfwSetCursorPosCallback(window, (w, xpos, ypos) -> {
            if (firstMouse) {
                lastX = xpos;
                lastY = ypos;
                firstMouse = false;
            }

            double xoffset = xpos - lastX;
            double yoffset = lastY - ypos;
            lastX = xpos;
            lastY = ypos;

            float sensitivity = 0.1f;
            xoffset *= sensitivity;
            yoffset *= sensitivity;

            yaw += xoffset;
            pitch += yoffset;

            if (pitch > 89.0f) pitch = 89.0f;
            if (pitch < -89.0f) pitch = -89.0f;

            updateCameraVectors();
        });

        glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
                glfwSetWindowShouldClose(window, true);
            }
            if (key >= 0 && key < keys.length) {
                if (action == GLFW_PRESS) {
                    keys[key] = true;
                } else if (action == GLFW_RELEASE) {
                    keys[key] = false;
                }
            }
        });

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            glfwGetWindowSize(window, pWidth, pHeight);
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            glfwSetWindowPos(
                    window,
                    (vidmode.width() - pWidth.get(0)) / 2,
                    (vidmode.height() - pHeight.get(0)) / 2
            );
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);
        GL.createCapabilities();

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);

        // ワールドデータの初期化
        for (int x = 0; x < WORLD_SIZE_X; x++) {
            for (int z = 0; z < WORLD_SIZE_Z; z++) {
                worldData[x][0][z] = 1; // 最底面（Y=0）に床
            }
        }
        worldData[8][1][8] = 1;
        worldData[8][2][8] = 1;
        worldData[5][1][5] = 1;

        // --- 草ブロック風の色定義（上面: 緑, 側面・下面: 土色） ---
        float[] topColor   = {0.3f, 0.75f, 0.3f}; // 緑
        float[] sideColor  = {0.55f, 0.35f, 0.15f}; // 土色
        float[] bottomColor = {0.4f, 0.25f, 0.1f};  // 少し暗い土色

        float size = 0.5f;
        float[] vertices = {
                // 前面 (側面)
                -size, -size,  size,  sideColor[0], sideColor[1], sideColor[2],
                size, -size,  size,  sideColor[0], sideColor[1], sideColor[2],
                size,  size,  size,  sideColor[0], sideColor[1], sideColor[2],
                size,  size,  size,  sideColor[0], sideColor[1], sideColor[2],
                -size,  size,  size,  sideColor[0], sideColor[1], sideColor[2],
                -size, -size,  size,  sideColor[0], sideColor[1], sideColor[2],

                // 後面 (側面)
                -size, -size, -size,  sideColor[0], sideColor[1], sideColor[2],
                -size,  size, -size,  sideColor[0], sideColor[1], sideColor[2],
                size,  size, -size,  sideColor[0], sideColor[1], sideColor[2],
                size,  size, -size,  sideColor[0], sideColor[1], sideColor[2],
                size, -size, -size,  sideColor[0], sideColor[1], sideColor[2],
                -size, -size, -size,  sideColor[0], sideColor[1], sideColor[2],

                // 左面 (側面)
                -size,  size,  size,  sideColor[0], sideColor[1], sideColor[2],
                -size,  size, -size,  sideColor[0], sideColor[1], sideColor[2],
                -size, -size, -size,  sideColor[0], sideColor[1], sideColor[2],
                -size, -size, -size,  sideColor[0], sideColor[1], sideColor[2],
                -size, -size,  size,  sideColor[0], sideColor[1], sideColor[2],
                -size,  size,  size,  sideColor[0], sideColor[1], sideColor[2],

                // 右面 (側面)
                size,  size,  size,  sideColor[0], sideColor[1], sideColor[2],
                size, -size,  size,  sideColor[0], sideColor[1], sideColor[2],
                size, -size, -size,  sideColor[0], sideColor[1], sideColor[2],
                size, -size, -size,  sideColor[0], sideColor[1], sideColor[2],
                size,  size, -size,  sideColor[0], sideColor[1], sideColor[2],
                size,  size,  size,  sideColor[0], sideColor[1], sideColor[2],

                // 上面 (草の色)
                -size,  size,  size,  topColor[0], topColor[1], topColor[2],
                size,  size,  size,  topColor[0], topColor[1], topColor[2],
                size,  size, -size,  topColor[0], topColor[1], topColor[2],
                size,  size, -size,  topColor[0], topColor[1], topColor[2],
                -size,  size, -size,  topColor[0], topColor[1], topColor[2],
                -size,  size,  size,  topColor[0], topColor[1], topColor[2],

                // 下面 (土の色)
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

        matrixBuffer = memAllocFloat(16);
        updateCameraVectors();
    }

    private void updateCameraVectors() {
        Vector3f front = new Vector3f();
        front.x = (float) (Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
        front.y = (float) Math.sin(Math.toRadians(pitch));
        front.z = (float) (Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
        cameraFront.set(front).normalize();
    }

    private void processInput(float deltaTime) {
        float cameraSpeed = 4.5f * deltaTime;

        // 移動用の仮ベクトルを作成
        Vector3f moveDir = new Vector3f();
        if (keys[GLFW_KEY_W]) {
            moveDir.add(cameraFront.x, 0.0f, cameraFront.z); // Y軸方向の移動を無視して水平に移動
        }
        if (keys[GLFW_KEY_S]) {
            moveDir.sub(cameraFront.x, 0.0f, cameraFront.z);
        }
        if (keys[GLFW_KEY_A]) {
            Vector3f side = new Vector3f();
            cameraFront.cross(cameraUp, side).normalize();
            moveDir.sub(side);
        }
        if (keys[GLFW_KEY_D]) {
            Vector3f side = new Vector3f();
            cameraFront.cross(cameraUp, side).normalize();
            moveDir.add(side);
        }

        if (moveDir.lengthSquared() > 0) {
            moveDir.normalize().mul(cameraSpeed);
            cameraPos.add(moveDir);
        }

        // --- 簡易的な高さ制限（床の上を歩くようにする） ---
        // 床の上面（Y = 1.0）＋ プレイヤーの目の高さ（1.5） = 2.5 を基本の足場とする
        float groundHeight = 1.0f + 1.5f;

        // もし中央のブロック（X:8, Z:8）のあたりにいたら、その上のブロック（Y=2）の高さに乗れるようにする
        int blockX = Math.round(cameraPos.x);
        int blockZ = Math.round(cameraPos.z);

        if (blockX >= 0 && blockX < WORLD_SIZE_X && blockZ >= 0 && blockZ < WORLD_SIZE_Z) {
            // その場所にある一番高いブロックを探す
            for (int y = WORLD_SIZE_Y - 1; y >= 0; y--) {
                if (worldData[blockX][y][blockZ] > 0) {
                    float surfaceY = (y + 0.5f) + 1.5f; // ブロック上面 + 目の高さ
                    if (cameraPos.y < surfaceY + 0.5f && cameraPos.y > surfaceY - 1.0f) {
                        groundHeight = surfaceY;
                        break;
                    }
                }
            }
        }

        cameraPos.y = groundHeight; // 高さを固定して宙に浮いたり沈んだりしないようにする
    }

    private void loop() {
        glClearColor(0.2f, 0.4f, 0.6f, 0.0f);

        Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(45.0f), (float)WIDTH / HEIGHT, 0.1f, 100.0f);
        Matrix4f view = new Matrix4f();
        Matrix4f model = new Matrix4f();
        Matrix4f mvp = new Matrix4f();

        double lastFrameTime = glfwGetTime();

        while (!glfwWindowShouldClose(window)) {
            double currentFrameTime = glfwGetTime();
            float deltaTime = (float) (currentFrameTime - lastFrameTime);
            lastFrameTime = currentFrameTime;

            processInput(deltaTime);

            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            view.identity().lookAt(cameraPos, new Vector3f(cameraPos).add(cameraFront), cameraUp);

            glUseProgram(shaderProgram);
            glBindVertexArray(vao);

            for (int x = 0; x < WORLD_SIZE_X; x++) {
                for (int y = 0; y < WORLD_SIZE_Y; y++) {
                    for (int z = 0; z < WORLD_SIZE_Z; z++) {
                        if (worldData[x][y][z] > 0) {
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

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }
}