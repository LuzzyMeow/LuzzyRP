# LuzzyRP R8 混淆规则（v1.0.0 WebView 壳）

# ---- JSBridge：LuzzyBridge 的方法名被前端 JS 直接引用，禁止混淆（硬性规定 3 配套） ----
-keep class com.luzzymeow.luzzyrp.web.LuzzyBridge { *; }

# ---- 上游 JS 通过 assets 加载，不涉及混淆；此处仅保留壳工程自身规则 ----
