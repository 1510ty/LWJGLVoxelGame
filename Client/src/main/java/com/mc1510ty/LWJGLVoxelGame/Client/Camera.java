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

import org.joml.Matrix4d;
import org.joml.Vector3d;
import static org.lwjgl.glfw.GLFW.*;

public class Camera {
    public final Vector3d pos = new Vector3d(8.0f, 4.0f, 8.0f);
    public final Vector3d front = new Vector3d(0.0f, 0.0f, -1.0f);
    public final Vector3d up = new Vector3d(0.0f, 1.0f, 0.0f);

    private double yaw = -90.0f;
    private double pitch = -20.0f;

    private double velocityY = 0.0f;
    private boolean isGrounded = false;

    public Camera() {
        updateVectors();
    }

    // マウス入力で視点を動かす
    public void processMouseMovement(double xoffset, double yoffset) {
        double sensitivity = 0.1f;
        yaw += xoffset * sensitivity;
        pitch += yoffset * sensitivity;

        if (pitch > 89.0f) pitch = 89.0f;
        if (pitch < -89.0f) pitch = -89.0f;

        updateVectors();
    }

    private void updateVectors() {
        Vector3d newFront = new Vector3d();
        newFront.x = (Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
        newFront.y = Math.sin(Math.toRadians(pitch));
        newFront.z = (Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
        front.set(newFront).normalize();
    }

    // キーボード入力と物理演算（重力・当たり判定）
    public void processInput(boolean[] keys, double deltaTime, World world) {
        double speed = 4.5f * deltaTime;
        Vector3d moveDir = new Vector3d();

        if (keys[GLFW_KEY_W]) moveDir.add(front.x, 0.0f, front.z);
        if (keys[GLFW_KEY_S]) moveDir.sub(front.x, 0.0f, front.z);
        if (keys[GLFW_KEY_A]) {
            Vector3d side = new Vector3d();
            front.cross(up, side).normalize();
            moveDir.sub(side);
        }
        if (keys[GLFW_KEY_D]) {
            Vector3d side = new Vector3d();
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
        int blockX = (int) Math.round(pos.x);
        int blockZ = (int) Math.round(pos.z);
        double eyeHeight = 1.5f;
        double groundLevel = 1.0f + eyeHeight;

        for (int y = World.SIZE_Y - 1; y >= 0; y--) {
            if (world.getBlock(blockX, y, blockZ) > 0) {
                double surfaceY = (y + 0.5f) + eyeHeight;
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
    public Matrix4d getViewMatrix() {
        return new Matrix4d().lookAt(pos, new Vector3d(pos).add(front), up);
    }

    public RaycastResult raycast(double maxDistance, World world, Camera camera) {
        RaycastResult result = new RaycastResult();
        if (world == null) return result;

        Vector3d rayPos = new Vector3d(camera.pos);
        Vector3d rayDir = new Vector3d(camera.front);

        double step = 0.05f;
        int lastX = (int) Math.round(rayPos.x);
        int lastY = (int) Math.round(rayPos.y);
        int lastZ = (int) Math.round(rayPos.z);

        for (double d = 0; d < maxDistance; d += step) {
            rayPos.add(new Vector3d(rayDir).mul(step));

            int bx = (int) Math.round(rayPos.x);
            int by = (int) Math.round(rayPos.y);
            int bz = (int) Math.round(rayPos.z);

            if (bx != lastX || by != lastY || bz != lastZ) {
                if (world.getBlock(bx, by, bz) > 0) {
                    result.hit = true;
                    result.x = bx;
                    result.y = by;
                    result.z = bz;
                    result.prevX = lastX;
                    result.prevY = lastY;
                    result.prevZ = lastZ;
                    return result;
                }
                lastX = bx;
                lastY = by;
                lastZ = bz;
            }
        }
        return result;
    }
}