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
import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.stb.STBTTBakedChar;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;

import static org.lwjgl.opengl.ARBGPUShaderFP64.glUniformMatrix4dv;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL40.glUniform3d;
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
        glBufferData(GL_ARRAY_BUFFER, 4 * 4 * 6 * Double.BYTES, GL_DYNAMIC_DRAW);

        glVertexAttribPointer(0, 2, GL_DOUBLE, false, 4 * Double.BYTES, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_DOUBLE, false, 4 * Double.BYTES, 2 * Double.BYTES);
        glEnableVertexAttribArray(1);

        String vs = "#version 410 core\n" +
                "layout (location = 0) in dvec2 aPos;\n" +
                "layout (location = 1) in dvec2 aTexCoord;\n" +
                "uniform dmat4 projection;\n" +
                "out vec2 TexCoord;\n" +
                "void main() {\n" +
                "   gl_Position = vec4(projection * dvec4(aPos, 0.0, 1.0));\n" +
                "   TexCoord = vec2(aTexCoord);\n" +
                "}";

        String fs = "#version 410 core\n" +
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

    public void drawText(String text, float x, float y, double scale, int screenWidth, int screenHeight, Vector3d color) {
        glUseProgram(shaderProgram);
        glBindVertexArray(vao);
        glBindTexture(GL_TEXTURE_2D, fontTexId);

        Matrix4d ortho = new Matrix4d().ortho2D(0, screenWidth, screenHeight, 0);
        int projLoc = glGetUniformLocation(shaderProgram, "projection");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer fb = stack.mallocDouble(16);
            ortho.get(fb);
            glUniformMatrix4dv(projLoc, false, fb);
        }

        glUniform3d(glGetUniformLocation(shaderProgram, "textColor"), color.x, color.y, color.z);

        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            STBTTAlignedQuad q = STBTTAlignedQuad.malloc(stack);
            DoubleBuffer verticesBuffer = stack.mallocDouble(text.length() * 24); // 1文字あたり6頂点×4double(x,y,s,t)

            //ここはfloatじゃないといけない
            //下のstbtt_みたいなやつがfloatしか受け付けない
            float[] xpos = { x };
            float[] ypos = { y + 20.0f }; // ベースライン位置の調整

            int validCharCount = 0;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c < FIRST_CHAR || c >= FIRST_CHAR + NUM_CHARS) continue;

                stbtt_GetBakedQuad(cdata, BITMAP_W, BITMAP_H, c - FIRST_CHAR, xpos, ypos, q, true);

                double x0 = q.x0();
                double y0 = q.y0();
                double x1 = q.x1();
                double y1 = q.y1();
                double s0 = q.s0();
                double t0 = q.t0();
                double s1 = q.s1();
                double t1 = q.t1();

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