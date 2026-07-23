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
package com.mc1510ty.LWJGLVoxelGame.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlockNameIDMgr {

    // 1. データを保持する変数
    // 名前("lwjglvoxelgame:stone")からID(2)を引くためのマップ
    public final Map<String, Integer> nameToId = new HashMap<>();

    // ID(2)から名前("lwjglvoxelgame:stone")を引くためのリスト
    public final List<String> idToName = new ArrayList<>();

    // 元となるブロック名の配列
    private final String[] BLOCK_NAMES = {
            "lwjglvoxelgame:air",
            "lwjglvoxelgame:grass_block",
            "lwjglvoxelgame:stone",
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

    public List<String> getIdToName() {
        return idToName;
    }

    public void registerFromServer(int id, String name) {
        nameToId.put(name, id);

        // リストの大きさがIDに追いついていない場合は、nullで隙間を埋めながら広げる
        while (idToName.size() <= id) {
            idToName.add(null);
        }
        idToName.set(id, name);

        System.out.println("Synced from Server: ID " + id + " -> " + name);
    }

}
