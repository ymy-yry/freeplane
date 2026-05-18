# AI 插件 .addon.mm 打包说明

## 📦 什么是 .addon.mm 格式

`.addon.mm` 是 Freeplane 的插件安装包格式，本质上是一个特殊的思维导图文件（.mm），包含：
- 插件元数据（名称、版本、作者等）
- 插件描述和许可证
- 多语言翻译
- 插件文件（ZIP 格式嵌入）
- 安装和卸载规则

## 🔧 打包方法

### 方法一：使用 Gradle 任务（推荐）

```bash
# 在项目根目录执行
cd freeplane_plugin_ai
gradle packageAddonMM

# 生成的文件位于：
# freeplane_plugin_ai/build/outputs/org.freeplane.plugin.ai.addon.mm
```

### 方法二：使用 Groovy 脚本

```bash
# 1. 先构建 JAR
gradle jar

# 2. 运行打包脚本
groovy package-addon.groovy \
  build/libs/org.freeplane.plugin.ai-<version>.jar \
  build/outputs/org.freeplane.plugin.ai.addon.mm \
  <version>
```

### 方法三：手动创建

1. 在 Freeplane 中创建新的思维导图
2. 按照以下结构添加节点：

```
AI Plugin for Freeplane (根节点)
├── properties (属性节点)
│   ├── name = org.freeplane.plugin.ai
│   ├── version = 1.0.0
│   ├── author = Freeplane Team
│   ├── freeplaneVersionFrom = 1.13.0
│   └── ...
├── description (描述节点，HTML 格式)
├── license (许可证节点，HTML 格式)
├── translations (翻译节点)
│   ├── en
│   └── zh_CN
├── default.properties (默认配置)
├── preferences.xml (偏好设置)
├── scripts (脚本)
├── zips (ZIP 包)
│   └── plugin.jar.zip (嵌入 JAR 的 ZIP 文件)
├── deinstall (卸载规则)
├── images (图片)
└── lib (依赖库)
```

3. 保存为 `org.freeplane.plugin.ai.addon.mm`

## 📋 .addon.mm 文件结构详解

### 1. properties 节点（必需）

包含插件的基本信息，作为根节点的第一个子节点：

```xml
<node TEXT="properties">
    <attribute NAME="name" VALUE="org.freeplane.plugin.ai"/>
    <attribute NAME="version" VALUE="1.0.0"/>
    <attribute NAME="author" VALUE="Freeplane Team"/>
    <attribute NAME="description" VALUE="AI-powered features"/>
    <attribute NAME="freeplaneVersionFrom" VALUE="1.13.0"/>
    <attribute NAME="freeplaneVersionTo" VALUE=""/>
</node>
```

**必需属性：**
- `name`: 插件唯一标识符
- `version`: 插件版本号
- `author`: 作者名称
- `freeplaneVersionFrom`: 最低支持的 Freeplane 版本

**可选属性：**
- `description`: 简短描述
- `freeplaneVersionTo`: 最高支持的 Freeplane 版本

### 2. description 节点（推荐）

插件详细描述，使用 HTML 格式：

```xml
<node TEXT="description">
    <html>
      <body>
        <h2>AI Plugin for Freeplane</h2>
        <p>功能描述...</p>
      </body>
    </html>
</node>
```

### 3. license 节点（推荐）

许可证信息，使用 HTML 格式：

```xml
<node TEXT="license">
    <html>
      <body>
        <p>许可证内容...</p>
      </body>
    </html>
</node>
```

### 4. translations 节点（推荐）

多语言翻译：

```xml
<node TEXT="translations">
    <node TEXT="en">
        <attribute NAME="plugins:org.freeplane.plugin.ai:ai_panel" VALUE="AI Chat"/>
    </node>
    <node TEXT="zh_CN">
        <attribute NAME="plugins:org.freeplane.plugin.ai:ai_panel" VALUE="AI 聊天"/>
    </node>
</node>
```

### 5. zips 节点（核心）

包含插件的二进制文件（JAR 包）：

```xml
<node TEXT="zips">
    <node TEXT="plugin.jar.zip">
        <object class="java.awt.datatransfer.DataFlavor" index="0"/>
        <!-- 这里嵌入 ZIP 文件的二进制数据 -->
    </node>
</node>
```

**重要：** 
- 需要将 JAR 文件打包成 ZIP
- ZIP 文件作为二进制数据嵌入到节点中
- Freeplane 使用 Java 序列化存储二进制数据

### 6. deinstall 节点（推荐）

卸载规则：

```xml
<node TEXT="deinstall">
    <attribute NAME="delete" VALUE="addons/org.freeplane.plugin.ai"/>
    <attribute NAME="delete_1" VALUE="plugins/org.freeplane.plugin.ai"/>
</node>
```

### 7. default.properties 节点（可选）

默认配置属性：

```xml
<node TEXT="default.properties">
    <attribute NAME="ai_selected_model" VALUE=""/>
    <attribute NAME="ai_chat_shows_tool_calls" VALUE="true"/>
</node>
```

## 🚀 安装方法

### 方法一：直接打开（推荐）

1. 双击 `.addon.mm` 文件
2. Freeplane 会识别为插件安装包
3. 弹出确认对话框："xxx seems to be an add-on package. Do you want to install it?"
4. 点击"Yes"开始安装
5. 查看插件详情并确认安装

### 方法二：通过插件管理器

1. 打开 Freeplane
2. 菜单：工具 → 管理附加组件（Manage Add-ons）
3. 点击"安装"标签页
4. 选择 `.addon.mm` 文件
5. 点击"安装"按钮

### 方法三：拖拽安装

1. 将 `.addon.mm` 文件拖拽到 Freeplane 窗口
2. 确认安装对话框

## 🗑️ 卸载方法

### 方法一：通过插件管理器

1. 工具 → 管理附加组件
2. 在列表中找到 "AI Plugin"
3. 点击"卸载"按钮

### 方法二：手动删除

删除以下目录：
- `{用户目录}/addons/org.freeplane.plugin.ai/`
- `{用户目录}/plugins/org.freeplane.plugin.ai/`

## ⚠️ 注意事项

1. **二进制数据嵌入**
   - `.addon.mm` 中的 ZIP 数据不是简单的 Base64 编码
   - Freeplane 使用 Java 对象序列化存储二进制数据
   - 建议使用 Gradle 任务或官方工具生成

2. **版本兼容性**
   - 确保 `freeplaneVersionFrom` 设置正确
   - AI 插件需要 Java 17+

3. **依赖管理**
   - LangChain4j 等依赖库需要一并打包
   - 可以放在 `lib/` 节点中

4. **签名验证**
   - 生产环境的插件应该进行数字签名
   - 当前开发阶段可以跳过

## 📝 开发调试技巧

### 快速测试

```bash
# 1. 构建并部署到 BIN 目录
gradle build

# 2. 启动 Freeplane
../BIN/freeplane.bat

# 3. 检查插件是否加载
# 查看右侧是否有 "AI Chat" 面板
```

### 查看日志

Freeplane 启动日志位置：
- Windows: `%APPDATA%\Freeplane\freeplane.log`
- Linux/macOS: `~/.freeplane/freeplane.log`

## 🔗 相关资源

- [Freeplane 插件开发文档](https://www.freeplane.org/wiki/index.php/Plug-ins)
- [附加组件站点](https://www.freeplane.org/addons)
- [installScriptAddOn.groovy 源码](../freeplane_plugin_script/scripts/installScriptAddOn.groovy)

## 📊 打包产物

成功打包后，你会得到：

```
org.freeplane.plugin.ai.addon.mm
├── 大小: 约 5-10 MB（包含所有依赖）
├── 格式: Freeplane 思维导图 XML
├── 可安装: 是（双击或在 Freeplane 中打开）
└── 平台: 跨平台（Windows/Linux/macOS）
```

---

**最后更新**: 2026-05-07
**版本**: 1.0.0
