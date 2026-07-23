package com.mc1510ty.LWJGLVoxelGame.Server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlockNameIDMgr {

    // 1. データを保持する変数
    // 名前("lwjglvoxelgame:stone")からID(2)を引くためのマップ
    private final Map<String, Integer> nameToId = new HashMap<>();

    // ID(2)から名前("lwjglvoxelgame:stone")を引くためのリスト
    private final List<String> idToName = new ArrayList<>();

    // 元となるブロック名の配列
    private final String[] BLOCK_NAMES = {
            "lwjglvoxelgame:air",         // 0番
            "lwjglvoxelgame:grass_block", // 1番
            "lwjglvoxelgame:stone"        // 2番
    };

    // 2. 起動時に実行して登録するメソッド
    public void init() {
        for (String name : BLOCK_NAMES) {
            int id = idToName.size(); // 今のリストの大きさがそのまま次のIDになる
            nameToId.put(name, id);
            idToName.add(name);
            System.out.println("Registered: " + name + " -> ID: " + id);
        }
    }

    // 3. 読み出しメソッド①：名前からIDを取得する
    public int getId(String name) {
        // 登録されていない名前が来たら、とりあえず0番(airなど)を返すように安全策
        return nameToId.getOrDefault(name, 0);
    }

    // 4. 読み出しメソッド②：IDから名前を取得する
    public String getName(int id) {
        if (id >= 0 && id < idToName.size()) {
            return idToName.get(id);
        }
        return "lwjglvoxelgame:air"; // 範囲外のIDならairを返す
    }

}
