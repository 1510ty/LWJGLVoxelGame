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
import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.stb.STBTTBakedChar;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.stb.STBTruetype.*;

public class FontRenderer {
    private int fontTexId;
    private STBTTBakedChar.Buffer cdata;
    private int vao, vbo;
    private int shaderProgram;

    private static final int BITMAP_W = 1024;
    private static final int BITMAP_H = 1024;
    private static final int FIRST_CHAR = 32;
    private static final int NUM_CHARS = 96;

    public FontRenderer(String resourcePath) {
        try {
            ByteBuffer ttf = loadResourceToByteBuffer(resourcePath);

            ByteBuffer bitmap = MemoryUtil.memAlloc(BITMAP_W * BITMAP_H);
            cdata = STBTTBakedChar.malloc(NUM_CHARS);

            stbtt_BakeFontBitmap(ttf, 24.0f, bitmap, BITMAP_W, BITMAP_H, FIRST_CHAR, cdata);

            fontTexId = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, fontTexId);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RED, BITMAP_W, BITMAP_H, 0, GL_RED, GL_UNSIGNED_BYTE, bitmap);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

            MemoryUtil.memFree(bitmap);

        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("フォントの読み込みに失敗しました: " + resourcePath);
        }

        setupBuffersAndShader();
    }

    private ByteBuffer loadResourceToByteBuffer(String path) throws IOException {
        try (InputStream in = FontRenderer.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("ファイルが見つかりません: " + path);
            }
            byte[] bytes = in.readAllBytes();
            ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
            buffer.put(bytes);
            buffer.flip();
            return buffer;
        }
    }

    private void setupBuffersAndShader() {
        vao = glGenVertexArrays();
        glBindVertexArray(vao);
        vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, 4 * 4 * 6 * Float.BYTES, GL_DYNAMIC_DRAW);

        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);
        glEnableVertexAttribArray(1);

        String vs = "#version 330 core\n" +
                "layout (location = 0) in vec2 aPos;\n" +
                "layout (location = 1) in vec2 aTexCoord;\n" +
                "uniform mat4 projection;\n" +
                "out vec2 TexCoord;\n" +
                "void main() {\n" +
                "   gl_Position = projection * vec4(aPos, 0.0, 1.0);\n" +
                "   TexCoord = aTexCoord;\n" +
                "}";

        String fs = "#version 330 core\n" +
                "in vec2 TexCoord;\n" +
                "out vec4 FragColor;\n" +
                "uniform sampler2D tex;\n" +
                "uniform vec3 textColor;\n" +
                "void main() {\n" +
                "   float alpha = texture(tex, TexCoord).r;\n" +
                "   if (alpha < 0.1) discard;\n" +
                "   FragColor = vec4(textColor, alpha);\n" +
                "}";

        int vsId = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vsId, vs);
        glCompileShader(vsId);
        int fsId = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fsId, fs);
        glCompileShader(fsId);

        shaderProgram = glCreateProgram();
        glAttachShader(shaderProgram, vsId);
        glAttachShader(shaderProgram, fsId);
        glLinkProgram(shaderProgram);
        glDeleteShader(vsId);
        glDeleteShader(fsId);
    }

    public void drawText(String text, float x, float y, float scale, int screenWidth, int screenHeight, Vector3f color) {
        glUseProgram(shaderProgram);
        glBindVertexArray(vao);
        glBindTexture(GL_TEXTURE_2D, fontTexId);

        Matrix4f ortho = new Matrix4f().ortho2D(0, screenWidth, screenHeight, 0);
        int projLoc = glGetUniformLocation(shaderProgram, "projection");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(16);
            ortho.get(fb);
            glUniformMatrix4fv(projLoc, false, fb);
        }

        glUniform3f(glGetUniformLocation(shaderProgram, "textColor"), color.x, color.y, color.z);

        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            STBTTAlignedQuad q = STBTTAlignedQuad.malloc(stack);
            FloatBuffer verticesBuffer = stack.mallocFloat(text.length() * 24); // 1文字あたり6頂点×4float(x,y,s,t)

            float[] xpos = { x };
            float[] ypos = { y + 20.0f }; // ベースライン位置の調整

            int validCharCount = 0;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c < FIRST_CHAR || c >= FIRST_CHAR + NUM_CHARS) continue;

                stbtt_GetBakedQuad(cdata, BITMAP_W, BITMAP_H, c - FIRST_CHAR, xpos, ypos, q, true);

                float x0 = q.x0();
                float y0 = q.y0();
                float x1 = q.x1();
                float y1 = q.y1();
                float s0 = q.s0();
                float t0 = q.t0();
                float s1 = q.s1();
                float t1 = q.t1();

                // 三角形1 (左上、左下、右下)
                verticesBuffer.put(x0).put(y0).put(s0).put(t0);
                verticesBuffer.put(x0).put(y1).put(s0).put(t1);
                verticesBuffer.put(x1).put(y1).put(s1).put(t1);

                // 三角形2 (左上、右下、右上)
                verticesBuffer.put(x0).put(y0).put(s0).put(t0);
                verticesBuffer.put(x1).put(y1).put(s1).put(t1);
                verticesBuffer.put(x1).put(y0).put(s1).put(t0);

                validCharCount++;
            }
            verticesBuffer.flip();

            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            glBufferData(GL_ARRAY_BUFFER, verticesBuffer, GL_DYNAMIC_DRAW);

            glDrawArrays(GL_TRIANGLES, 0, validCharCount * 6);
        }

        glEnable(GL_CULL_FACE);
        glEnable(GL_DEPTH_TEST);
    }

    public void cleanup() {
        glDeleteProgram(shaderProgram);
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);
        glDeleteTextures(fontTexId);
        if (cdata != null) cdata.free();
    }
}