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
import static org.lwjgl.glfw.GLFW.*;

public class Camera {
    public final Vector3f pos = new Vector3f(8.0f, 4.0f, 8.0f);
    public final Vector3f front = new Vector3f(0.0f, 0.0f, -1.0f);
    public final Vector3f up = new Vector3f(0.0f, 1.0f, 0.0f);

    private float yaw = -90.0f;
    private float pitch = -20.0f;

    private float velocityY = 0.0f;
    private boolean isGrounded = false;

    public Camera() {
        updateVectors();
    }

    // マウス入力で視点を動かす
    public void processMouseMovement(float xoffset, float yoffset) {
        float sensitivity = 0.1f;
        yaw += xoffset * sensitivity;
        pitch += yoffset * sensitivity;

        if (pitch > 89.0f) pitch = 89.0f;
        if (pitch < -89.0f) pitch = -89.0f;

        updateVectors();
    }

    private void updateVectors() {
        Vector3f newFront = new Vector3f();
        newFront.x = (float) (Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
        newFront.y = (float) Math.sin(Math.toRadians(pitch));
        newFront.z = (float) (Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
        front.set(newFront).normalize();
    }

    // キーボード入力と物理演算（重力・当たり判定）
    public void processInput(boolean[] keys, float deltaTime, World world) {
        float speed = 4.5f * deltaTime;
        Vector3f moveDir = new Vector3f();

        if (keys[GLFW_KEY_W]) moveDir.add(front.x, 0.0f, front.z);
        if (keys[GLFW_KEY_S]) moveDir.sub(front.x, 0.0f, front.z);
        if (keys[GLFW_KEY_A]) {
            Vector3f side = new Vector3f();
            front.cross(up, side).normalize();
            moveDir.sub(side);
        }
        if (keys[GLFW_KEY_D]) {
            Vector3f side = new Vector3f();
            front.cross(up, side).normalize();
            moveDir.add(side);
        }

        if (moveDir.lengthSquared() > 0) {
            moveDir.normalize().mul(speed);
            pos.add(moveDir);
        }

        // 重力とジャンプ
        if (keys[GLFW_KEY_SPACE] && isGrounded) {
            velocityY = 6.0f;
            isGrounded = false;
        }

        velocityY -= 15.0f * deltaTime;
        pos.y += velocityY * deltaTime;

        // 床との当たり判定
        int blockX = Math.round(pos.x);
        int blockZ = Math.round(pos.z);
        float eyeHeight = 1.5f;
        float groundLevel = 1.0f + eyeHeight;

        for (int y = World.SIZE_Y - 1; y >= 0; y--) {
            if (world.getBlock(blockX, y, blockZ) > 0) {
                float surfaceY = (y + 0.5f) + eyeHeight;
                if (pos.y <= surfaceY && pos.y >= surfaceY - 1.0f && velocityY <= 0) {
                    groundLevel = surfaceY;
                    isGrounded = true;
                    velocityY = 0.0f;
                    break;
                }
            }
        }

        if (pos.y <= groundLevel) {
            pos.y = groundLevel;
            isGrounded = true;
            velocityY = 0.0f;
        }
    }

    // 描画用のView行列を取得
    public Matrix4f getViewMatrix() {
        return new Matrix4f().lookAt(pos, new Vector3f(pos).add(front), up);
    }
}