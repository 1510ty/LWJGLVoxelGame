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
    private long startTime;

    // 画面サイズ定数
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;

    // プレイヤー（カメラ）の位置と向き
    private final Vector3f cameraPos = new Vector3f(0.0f, 0.0f, 3.0f);
    private final Vector3f cameraFront = new Vector3f(0.0f, 0.0f, -1.0f);
    private final Vector3f cameraUp = new Vector3f(0.0f, 1.0f, 0.0f);

    private float yaw = -90.0f; // 左右の向き
    private float pitch = 0.0f;  // 上下の向き
    private boolean firstMouse = true;
    private double lastX = WIDTH / 2.0;
    private double lastY = HEIGHT / 2.0;

    // キー入力の状態を保存する配列
    private final boolean[] keys = new boolean[1024];

    public void run() {
        init();
        loop();

        // クリーンアップ
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
        // エラー出力の設定
        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) {
            throw new IllegalStateException("GLFWの初期化に失敗しました。");
        }

        // ウィンドウの設定
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

        // ウィンドウの作成
        window = glfwCreateWindow(WIDTH, HEIGHT, "Voxel Game Client - Player", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("GLFWウィンドウの作成に失敗しました。");
        }

        // マウスカーソルをウィンドウ内にロックして非表示にする（FPS視点用）
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);

        // マウスの移動イベントを設定
        glfwSetCursorPosCallback(window, (w, xpos, ypos) -> {
            if (firstMouse) {
                lastX = xpos;
                lastY = ypos;
                firstMouse = false;
            }

            double xoffset = xpos - lastX;
            double yoffset = lastY - ypos; // Y座標は上がマイナスになるため反転
            lastX = xpos;
            lastY = ypos;

            float sensitivity = 0.1f;
            xoffset *= sensitivity;
            yoffset *= sensitivity;

            yaw += xoffset;
            pitch += yoffset;

            // 視点が真上や真下を向きすぎてバグらないように制限
            if (pitch > 89.0f) pitch = 89.0f;
            if (pitch < -89.0f) pitch = -89.0f;

            updateCameraVectors();
        });

        // キーボードの入力イベントを設定
        glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
                glfwSetWindowShouldClose(window, true); // ESCキーで終了
            }
            if (key >= 0 && key < keys.length) {
                if (action == GLFW_PRESS) {
                    keys[key] = true;
                } else if (action == GLFW_RELEASE) {
                    keys[key] = false;
                }
            }
        });

        // 画面中央にウィンドウを配置
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

        // 3D描画の設定
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);

        // 立方体の頂点データ
        float size = 0.5f;
        float[] vertices = {
                // 前面 (赤)
                -size, -size,  size,  1.0f, 0.0f, 0.0f,
                size, -size,  size,  1.0f, 0.0f, 0.0f,
                size,  size,  size,  1.0f, 0.0f, 0.0f,
                size,  size,  size,  1.0f, 0.0f, 0.0f,
                -size,  size,  size,  1.0f, 0.0f, 0.0f,
                -size, -size,  size,  1.0f, 0.0f, 0.0f,

                // 後面 (緑)
                -size, -size, -size,  0.0f, 1.0f, 0.0f,
                -size,  size, -size,  0.0f, 1.0f, 0.0f,
                size,  size, -size,  0.0f, 1.0f, 0.0f,
                size,  size, -size,  0.0f, 1.0f, 0.0f,
                size, -size, -size,  0.0f, 1.0f, 0.0f,
                -size, -size, -size,  0.0f, 1.0f, 0.0f,

                // 左面 (青)
                -size,  size,  size,  0.0f, 0.0f, 1.0f,
                -size,  size, -size,  0.0f, 0.0f, 1.0f,
                -size, -size, -size,  0.0f, 0.0f, 1.0f,
                -size, -size, -size,  0.0f, 0.0f, 1.0f,
                -size, -size,  size,  0.0f, 0.0f, 1.0f,
                -size,  size,  size,  0.0f, 0.0f, 1.0f,

                // 右面 (黄)
                size,  size,  size,  1.0f, 1.0f, 0.0f,
                size, -size,  size,  1.0f, 1.0f, 0.0f,
                size, -size, -size,  1.0f, 1.0f, 0.0f,
                size, -size, -size,  1.0f, 1.0f, 0.0f,
                size,  size, -size,  1.0f, 1.0f, 0.0f,
                size,  size,  size,  1.0f, 1.0f, 0.0f,

                // 上面 (マゼンタ)
                -size,  size,  size,  1.0f, 0.0f, 1.0f,
                size,  size,  size,  1.0f, 0.0f, 1.0f,
                size,  size, -size,  1.0f, 0.0f, 1.0f,
                size,  size, -size,  1.0f, 0.0f, 1.0f,
                -size,  size, -size,  1.0f, 0.0f, 1.0f,
                -size,  size,  size,  1.0f, 0.0f, 1.0f,

                // 下面 (シアン)
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

        // シェーダーのコンパイル
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
        startTime = System.currentTimeMillis();
        updateCameraVectors();
    }

    private void updateCameraVectors() {
        Vector3f front = new Vector3f();
        front.x = (float) (Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
        front.y = (float) Math.sin(Math.toRadians(pitch));
        front.z = (float) (Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
        cameraFront.set(front).normalize();
    }

    private void processInput() {
        float cameraSpeed = 0.05f; // 移動速度
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

        while (!glfwWindowShouldClose(window)) {
            // キー入力を毎フレーム処理してカメラ位置を更新
            processInput();

            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // ビュー行列を現在のカメラ位置と向きで更新
            view.identity().lookAt(cameraPos, new Vector3f(cameraPos).add(cameraFront), cameraUp);

            // 立方体の位置を原点（0,0,0）に固定して描画
            model.identity();

            // 投影 * ビュー * モデル行列の掛け合わせ
            projection.mul(view, mvp);
            mvp.mul(model);

            glUseProgram(shaderProgram);

            mvp.get(matrixBuffer);
            int mvpLocation = glGetUniformLocation(shaderProgram, "mvp");
            glUniformMatrix4fv(mvpLocation, false, matrixBuffer);

            glBindVertexArray(vao);
            glDrawArrays(GL_TRIANGLES, 0, 36);

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }
}