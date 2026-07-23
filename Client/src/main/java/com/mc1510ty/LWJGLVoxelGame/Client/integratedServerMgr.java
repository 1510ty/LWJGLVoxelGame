package com.mc1510ty.LWJGLVoxelGame.Client;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class integratedServerMgr {

    public Process extractAndStartServer(String worldFilePath, Process serverProcess) {
        try {
            System.out.println("Extracting embedded server.jar...");
            InputStream in = ClientLauncher.class.getResourceAsStream("/server.jar");
            if (in == null) {
                throw new RuntimeException("内蔵された server.jar が見つかりません。");
            }

            File tempDir = new File(System.getProperty("java.io.tmpdir"), "voxelgame_server");
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }
            File serverFile = new File(tempDir, "server.jar");
            Files.copy(in, serverFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            System.out.println("Starting embedded server process with save path: " + worldFilePath);
            String javaPath = System.getProperty("java.home") + "/bin/java";

            ProcessBuilder pb = new ProcessBuilder(
                    javaPath,
                    "-jar",
                    serverFile.getAbsolutePath(),
                    worldFilePath,
                    "integrated" // ★ ここにオプションを追加！
            );

            pb.inheritIO();
            return pb.start();



        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("内蔵サーバーの起動に失敗しました。");
        }
    }


}
