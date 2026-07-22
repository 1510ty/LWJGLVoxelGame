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

    // プレイヤー（カメラ）の位置と向き
    private final Vector3f cameraPos = new Vector3f(0.0f, 2.0f, 5.0f);
    private final Vector3f cameraFront = new Vector3f(0.0f, 0.0f, -1.0f);
    private final Vector3f cameraUp = new Vector3f(0.0f, 1.0f, 0.0f);

    private float yaw = -90.0f;
    private float pitch = -20.0f;
    private boolean firstMouse = true;
    private double lastX = WIDTH / 2.0;
    private double lastY = HEIGHT / 2.0;

    private final boolean[] keys = new boolean[1024];

    // ワールドデータの定義
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

        window = glfwCreateWindow(WIDTH, HEIGHT, "Voxel Game Client - DeltaTime", NULL, NULL);
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
                worldData[x][0][z] = 1;
            }
        }
        worldData[8][1][8] = 1;
        worldData[8][2][8] = 1;
        worldData[5][1][5] = 1;

        // 立方体の頂点データ
        float size = 0.5f;
        float[] vertices = {
                // 前面
                -size, -size,  size,  1.0f, 0.0f, 0.0f,
                size, -size,  size,  1.0f, 0.0f, 0.0f,
                size,  size,  size,  1.0f, 0.0f, 0.0f,
                size,  size,  size,  1.0f, 0.0f, 0.0f,
                -size,  size,  size,  1.0f, 0.0f, 0.0f,
                -size, -size,  size,  1.0f, 0.0f, 0.0f,

                // 後面
                -size, -size, -size,  0.0f, 1.0f, 0.0f,
                -size,  size, -size,  0.0f, 1.0f, 0.0f,
                size,  size, -size,  0.0f, 1.0f, 0.0f,
                size,  size, -size,  0.0f, 1.0f, 0.0f,
                size, -size, -size,  0.0f, 1.0f, 0.0f,
                -size, -size, -size,  0.0f, 1.0f, 0.0f,

                // 左面
                -size,  size,  size,  0.0f, 0.0f, 1.0f,
                -size,  size, -size,  0.0f, 0.0f, 1.0f,
                -size, -size, -size,  0.0f, 0.0f, 1.0f,
                -size, -size, -size,  0.0f, 0.0f, 1.0f,
                -size, -size,  size,  0.0f, 0.0f, 1.0f,
                -size,  size,  size,  0.0f, 0.0f, 1.0f,

                // 右面
                size,  size,  size,  1.0f, 1.0f, 0.0f,
                size, -size,  size,  1.0f, 1.0f, 0.0f,
                size, -size, -size,  1.0f, 1.0f, 0.0f,
                size, -size, -size,  1.0f, 1.0f, 0.0f,
                size,  size, -size,  1.0f, 1.0f, 0.0f,
                size,  size,  size,  1.0f, 1.0f, 0.0f,

                // 上面
                -size,  size,  size,  1.0f, 0.0f, 1.0f,
                size,  size,  size,  1.0f, 0.0f, 1.0f,
                size,  size, -size,  1.0f, 0.0f, 1.0f,
                size,  size, -size,  1.0f, 0.0f, 1.0f,
                -size,  size, -size,  1.0f, 0.0f, 1.0f,
                -size,  size,  size,  1.0f, 0.0f, 1.0f,

                // 下面
                -size, -size,  size,  0.0f, 1.0f, 1.0f,
                -size, -size, -size,  0.0f, 1.0f, 1.0f,
                size, -size, -size,  0.0f, 1.0f, 1.0f,
                size, -size, -size,  0.0f, 1.0f, 1.0f,
                size, -size,  size,  0.0f, 1.0f, 1.0f,
                -size, -size,  size,  0.0f, 1.0f, 1.0f
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

    // デルタタイムを受け取り、1秒あたりの移動距離（例: 4.5ブロック/秒）として計算する
    private void processInput(float deltaTime) {
        float cameraSpeed = 4.5f * deltaTime;
        if (keys[GLFW_KEY_W]) {
            cameraPos.fma(cameraSpeed, cameraFront);
        }
        if (keys[GLFW_KEY_S]) {
            cameraPos.fma(-cameraSpeed, cameraFront);
        }
        if (keys[GLFW_KEY_A]) {
            Vector3f side = new Vector3f();
            cameraFront.cross(cameraUp, side).normalize();
            cameraPos.fma(-cameraSpeed, side);
        }
        if (keys[GLFW_KEY_D]) {
            Vector3f side = new Vector3f();
            cameraFront.cross(cameraUp, side).normalize();
            cameraPos.fma(cameraSpeed, side);
        }
    }

    private void loop() {
        glClearColor(0.2f, 0.4f, 0.6f, 0.0f);

        Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(45.0f), (float)WIDTH / HEIGHT, 0.1f, 100.0f);
        Matrix4f view = new Matrix4f();
        Matrix4f model = new Matrix4f();
        Matrix4f mvp = new Matrix4f();

        // ループ開始前の時間を記録
        double lastFrameTime = glfwGetTime();

        while (!glfwWindowShouldClose(window)) {
            // 現在の時間を取得し、前フレームからの経過時間（デルタタイム）を計算
            double currentFrameTime = glfwGetTime();
            float deltaTime = (float) (currentFrameTime - lastFrameTime);
            lastFrameTime = currentFrameTime;

            // デルタタイムを渡して入力を処理
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