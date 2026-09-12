package com.luzzymeow.luzzyrp.testing

/**
 * 共享的**小号真实形态**旧数据导出（结构与真夹具
 * `app/src/test/resources/legacy/webview-db-fixture.json` 逐字段同源，只是量小）。
 *
 * 放在共享位置的理由：数据层测试与聊天页持久化测试都要用它，
 * 两份拷贝迟早会漂移，而「测试夹具与生产输入一致」正是这套测试的价值所在。
 *
 * 形状要点（照真实数据来，不图省事）：
 * - 用户消息带 `isSelf` / `avatar` / `imageAttachments`（旧结构的多余字段，必须能往返）；
 * - assistant 消息带 `reasoning` 与界面态字段（`shouldAnimate` / `isCotOpen`）；
 * - 记忆分片带量化后的向量四字段（`embeddingQ/Scale/Dims/Encoding`）。
 */
object SampleLegacyExport {

    const val JSON = """
{
  "fixtureVersion": 1,
  "databases": {
    "RPHubDB": {
      "version": 1,
      "entries": {
        "rp_hub_characters": [
          {
            "uuid": "char-1",
            "name": "样例角色",
            "createdAt": 1700000000000,
            "avatar": "data:image/png;base64,QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVo=",
            "first_mes": "首句",
            "worldInfo": [],
            "regexScripts": [],
            "uiTemplates": []
          },
          {
            "uuid": "char-2",
            "name": "第二张",
            "createdAt": 1700000001000,
            "avatar": "",
            "first_mes": "另一句"
          }
        ],
        "rp_hub_branches_char-1": {
          "version": 1,
          "activeBranchId": "b1",
          "branches": [
            { "id": "main", "name": "主线", "parentId": null, "createdAt": 1700000000000, "updatedAt": 1700000000000, "forkFloor": 0, "floorCount": 1, "messageCount": 1, "wordCount": 10 },
            { "id": "b1", "name": "分支甲", "parentId": "main", "createdAt": 1700000002000, "updatedAt": 1700000002000, "forkFloor": 1, "floorCount": 1, "messageCount": 1, "wordCount": 8 }
          ]
        },
        "rp_hub_chat_char-1": [
          { "role": "assistant", "name": "样例角色", "content": "首句", "reasoning": "", "id": "m0", "shouldAnimate": true, "isCotOpen": false },
          { "role": "user", "name": "我", "content": "第二句", "isSelf": true, "avatar": "", "imageAttachments": [] }
        ],
        "rp_hub_chat_char-1__branch__b1": [
          { "role": "assistant", "name": "样例角色", "content": "分支首句", "id": "m0" },
          { "role": "user", "name": "我", "content": "分支里的问句", "isSelf": true, "avatar": "", "imageAttachments": [] }
        ],
        "rp_hub_memories_char-1": [
          { "id": "v1", "turn": 1, "enabled": true, "vectorMemory": true, "chunkMode": "paragraph", "embeddingQ": "AAAA", "embeddingScale": 0.01, "embeddingDims": 8, "embeddingEncoding": "int8:maxabs:v1" }
        ],
        "rp_hub_classic_memories_char-1": [
          { "id": "c1", "turn": 1, "enabled": true, "classicMemory": true, "summary": "一段总结" }
        ],
        "rp_hub_worldinfo": [
          { "comment": "一条世界书", "keys": ["钥匙"], "content": "内容", "enabled": true, "scope": "character" }
        ],
        "rp_hub_regex": [
          { "name": "一条正则", "regex": "/a/g", "replacement": "b", "scope": "character", "enabled": true }
        ],
        "rp_hub_presets": [
          { "name": "一条预设", "role": "system", "content": "内容", "enabled": true }
        ],
        "rp_hub_user": { "name": "我", "uuid": "user-1", "avatar": "" },
        "rp_hub_user_profiles": [ { "uuid": "user-1", "name": "我", "avatar": "", "person": "second" } ],
        "rp_hub_active_profile_id": "user-1",
        "rp_hub_last_active_char": 0,
        "rp_hub_memory_settings": { "enabled": true, "mode": "vector", "emptyTurns": { "char-1:vector": [] } }
      }
    },
    "SillyTavernDB": { "version": 1, "entries": {} }
  }
}
"""
}
