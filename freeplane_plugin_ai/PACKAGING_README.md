# 📦 AI 插件打包指南

## 快速开始

### Windows 用户

**方法一：使用 PowerShell 脚本（推荐）**

```powershell
# 在 freeplane_plugin_ai 目录下执行
.\Build-AddonPackage.ps1
```

**方法二：使用批处理文件**

```cmd
build-addon.bat
```

**方法三：使用 Gradle**

```bash
gradle packageAddonMM
```

### 生成的文件

打包完成后，会在 `build/outputs/` 目录下生成：

```
org.freeplane.plugin.ai.addon.mm  ← Freeplane 插件安装包
```

## 📋 安装插件

### 方法一：双击安装（最简单）

1. 双击 `org.freeplane.plugin.ai.addon.mm` 文件
2. Freeplane 会弹出确认对话框
3. 点击"Yes"开始安装
4. 查看插件详情并确认

### 方法二：通过插件管理器

1. 打开 Freeplane
2. 菜单：**工具 → 管理附加组件**
3. 点击"安装"标签页
4. 选择 `.addon.mm` 文件
5. 点击"安装"

### 方法三：开发调试（无需打包）

```bash
# 直接部署到 BIN 目录
gradle deployToBin

# 启动 Freeplane
..\BIN\freeplane.bat
```

## ⚠️ 重要说明

### 关于 .addon.mm 格式

Freeplane 的 `.addon.mm` 格式是一种特殊的思维导图文件，它使用 **Java 对象序列化** 来嵌入二进制数据（ZIP 包）。这意味着：

1. **不能直接用文本编辑器编辑二进制数据**
2. **需要通过 Freeplane 界面添加 ZIP 文件**
3. **Gradle 脚本只能生成模板**

### 完整打包流程

由于上述限制，完整的打包流程是：

```
1. Gradle 构建 JAR
   ↓
2. 创建 ZIP 包（包含 JAR 和依赖）
   ↓
3. 生成 .addon.mm 模板（XML 结构）
   ↓
4. 在 Freeplane 中打开模板
   ↓
5. 手动添加 ZIP 文件到 'zips' 节点
   ↓
6. 保存为最终的 .addon.mm 文件
```

### 简化方案（推荐用于开发）

对于开发和测试，建议直接使用 `deployToBin`：

```bash
# 一键部署
gradle deployToBin

# 启动 Freeplane（插件自动加载）
..\BIN\freeplane.bat
```

这样无需打包成 `.addon.mm`，可以直接测试插件功能。

## 🔧 故障排除

### 问题：PowerShell 脚本无法执行

**原因**：PowerShell 执行策略限制

**解决方法**：

```powershell
# 以管理员身份运行 PowerShell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

### 问题：Gradle 构建失败

**原因**：依赖下载失败或 Java 版本不匹配

**解决方法**：

```bash
# 清理并重新构建
gradle clean build

# 检查 Java 版本（需要 Java 17）
java -version
```

### 问题：插件未加载

**检查清单**：

1. ✅ Java 版本 >= 17
2. ✅ JAR 文件已部署到 `BIN/plugins/org.freeplane.plugin.ai/`
3. ✅ `META-INF/MANIFEST.MF` 存在
4. ✅ 查看日志：`%APPDATA%\Freeplane\freeplane.log`

## 📖 更多信息

- [ADDON_PACKAGE_GUIDE.md](ADDON_PACKAGE_GUIDE.md) - 详细的 .addon.mm 格式说明
- [Freeplane 插件文档](https://www.freeplane.org/wiki/index.php/Plug-ins)
- [installScriptAddOn.groovy](../freeplane_plugin_script/scripts/installScriptAddOn.groovy) - 安装脚本源码

## 🎯 快速参考

### 常用命令

```bash
# 构建 JAR
gradle jar

# 部署到 BIN 目录
gradle deployToBin

# 生成 .addon.mm 模板
gradle packageAddonMM

# 完整构建（包含部署和验证）
gradle build

# 清理
gradle clean
```

### 文件位置

| 文件 | 位置 |
|------|------|
| 主 JAR | `build/libs/org.freeplane.plugin.ai-*.jar` |
| .addon.mm | `build/outputs/org.freeplane.plugin.ai.addon.mm` |
| ZIP 包 | `build/tmp/addon-package/plugin-package.zip` |
| 部署目录 | `../BIN/plugins/org.freeplane.plugin.ai/` |

### 版本号

版本号定义在 `../gradle.properties` 文件中，例如：

```properties
version=1.0.0
```

---

**最后更新**: 2026-05-07  
**维护者**: Freeplane Team
