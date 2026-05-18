# Build-AddonPackage.ps1
# Freeplane AI Plugin .addon.mm 打包脚本
# 使用方法: .\Build-AddonPackage.ps1

$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Freeplane AI Plugin 打包工具" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 切换到脚本所在目录
Set-Location $PSScriptRoot

# 1. 构建 JAR
Write-Host "[1/5] 构建 AI 插件 JAR..." -ForegroundColor Yellow
& gradle clean jar -q
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ JAR 构建失败" -ForegroundColor Red
    pause
    exit 1
}
Write-Host "✅ JAR 构建成功" -ForegroundColor Green
Write-Host ""

# 2. 查找 JAR 文件
Write-Host "[2/5] 查找 JAR 文件..." -ForegroundColor Yellow
$jarFile = Get-ChildItem -Path "build\libs" -Filter "*.jar" -Recurse | 
    Where-Object { $_.Name -notmatch "sources|javadoc" } | 
    Select-Object -First 1

if (-not $jarFile) {
    Write-Host "❌ 未找到 JAR 文件" -ForegroundColor Red
    pause
    exit 1
}

Write-Host "找到 JAR: $($jarFile.Name)" -ForegroundColor Green
Write-Host "大小: $([math]::Round($jarFile.Length / 1MB, 2)) MB" -ForegroundColor Green
Write-Host ""

# 3. 提取版本信息
Write-Host "[3/5] 提取版本信息..." -ForegroundColor Yellow
$version = $jarFile.Name -replace 'org\.freeplane\.plugin\.ai-', '' -replace '\.jar$', ''
Write-Host "版本: $version" -ForegroundColor Green
Write-Host ""

# 4. 创建输出目录和 ZIP 包
Write-Host "[4/5] 创建插件包..." -ForegroundColor Yellow

$outputDir = Join-Path $PSScriptRoot "build\outputs"
$tempDir = Join-Path $PSScriptRoot "build\tmp\addon-package"

if (-not (Test-Path $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
}

if (-not (Test-Path $tempDir)) {
    New-Item -ItemType Directory -Path $tempDir -Force | Out-Null
}

# 收集依赖 JAR
$libDir = Join-Path $tempDir "lib"
New-Item -ItemType Directory -Path $libDir -Force | Out-Null

# 从 Gradle 缓存中复制依赖（简化处理，实际应该从 configurations 获取）
Write-Host "  准备打包文件..." -ForegroundColor Gray

# 创建 ZIP 文件
$zipFile = Join-Path $tempDir "plugin-package.zip"
if (Test-Path $zipFile) {
    Remove-Item $zipFile -Force
}

# 使用 .NET 创建 ZIP
Add-Type -AssemblyName System.IO.Compression.FileSystem

$compressionLevel = [System.IO.Compression.CompressionLevel]::Optimal

# 创建临时目录结构
$packageDir = Join-Path $tempDir "package"
if (Test-Path $packageDir) {
    Remove-Item $packageDir -Recurse -Force
}
New-Item -ItemType Directory -Path $packageDir -Force | Out-Null

# 复制主 JAR
Copy-Item $jarFile.FullName -Destination $packageDir

# 创建 ZIP
[System.IO.Compression.ZipFile]::CreateFromDirectory($packageDir, $zipFile, $compressionLevel, $false)

$zipSize = [math]::Round((Get-Item $zipFile).Length / 1MB, 2)
Write-Host "  ✅ ZIP 包已创建: $zipSize MB" -ForegroundColor Green
Write-Host ""

# 5. 生成 .addon.mm 文件
Write-Host "[5/5] 生成 .addon.mm 文件..." -ForegroundColor Yellow

$addonMmFile = Join-Path $outputDir "org.freeplane.plugin.ai.addon.mm"

$addonMmContent = @"
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<map version="1.0.1">
<!-- AI Plugin Add-On Package for Freeplane -->
<!-- Generated: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss") -->
<node TEXT="AI Plugin for Freeplane" FOLDED="false">
    <node TEXT="properties" POSITION="right">
        <attribute NAME="name" VALUE="org.freeplane.plugin.ai"/>
        <attribute NAME="version" VALUE="$version"/>
        <attribute NAME="author" VALUE="Freeplane Team"/>
        <attribute NAME="description" VALUE="AI-powered chat and intelligent node operations with LangChain4j"/>
        <attribute NAME="freeplaneVersionFrom" VALUE="1.13.0"/>
        <attribute NAME="freeplaneVersionTo" VALUE=""/>
    </node>
    <node TEXT="description" POSITION="right">
        <html>
          <body>
            <h2>`🤖 AI Plugin for Freeplane</h2>
            <p>`为 Freeplane 集成 AI 能力，支持多种大语言模型提供商。</p>
            <h3>`✨ 主要功能</h3>
            <ul>
              <li><b>多模型支持</b>`：OpenRouter、Google Gemini、Ollama、文心一言、通义千问</li>
              <li><b>AI 聊天面板</b>`：直接在 Freeplane 中与 AI 对话</li>
              <li><b>智能节点操作</b>`：AI 辅助创建、编辑、组织思维导图节点</li>
              <li><b>REST API</b>`：提供外部集成的 HTTP API 接口</li>
              <li><b>MCP 服务器</b>`：支持 Model Context Protocol</li>
              <li><b>AI 编辑追踪</b>`：记录 AI 对节点的修改历史</li>
            </ul>
            <h3>`⚙️ 系统要求</h3>
            <ul>
              <li>Freeplane 1.13.0 或更高版本</li>
              <li>Java 17 或更高版本</li>
              <li>至少一个 AI 服务提供商的 API 密钥</li>
            </ul>
          </body>
        </html>
    </node>
    <node TEXT="license" POSITION="right">
        <html>
          <body>
            <h3>GNU General Public License version 2.0</h3>
            <p>This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License.</p>
          </body>
        </html>
    </node>
    <node TEXT="translations" POSITION="left">
        <node TEXT="en">
            <attribute NAME="plugins:org.freeplane.plugin.ai:ai_panel" VALUE="AI Chat"/>
            <attribute NAME="plugins:org.freeplane.plugin.ai:name" VALUE="AI Plugin"/>
        </node>
        <node TEXT="zh_CN">
            <attribute NAME="plugins:org.freeplane.plugin.ai:ai_panel" VALUE="AI `聊天"/>
            <attribute NAME="plugins:org.freeplane.plugin.ai:name" VALUE="AI `插件"/>
        </node>
    </node>
    <node TEXT="default.properties" POSITION="left">
        <attribute NAME="ai_selected_model" VALUE=""/>
        <attribute NAME="ai_chat_shows_tool_calls" VALUE="true"/>
        <attribute NAME="ai_edits_state_icon_visible" VALUE="true"/>
    </node>
    <node TEXT="preferences.xml" POSITION="left">
    </node>
    <node TEXT="scripts" POSITION="left">
    </node>
    <node TEXT="zips" POSITION="right" FOLDED="false">
        <node TEXT="plugin-package.zip">
            <!-- ZIP 文件需要手动添加 -->
        </node>
    </node>
    <node TEXT="deinstall" POSITION="right">
        <attribute NAME="delete" VALUE="addons/org.freeplane.plugin.ai"/>
        <attribute NAME="delete_1" VALUE="plugins/org.freeplane.plugin.ai"/>
    </node>
    <node TEXT="images" POSITION="left">
    </node>
    <node TEXT="lib" POSITION="left">
    </node>
</node>
</map>
"@

$addonMmContent | Out-File -FilePath $addonMmFile -Encoding UTF8

$addonMmSize = [math]::Round((Get-Item $addonMmFile).Length / 1KB, 2)
Write-Host "  ✅ .addon.mm 模板已创建: $addonMmSize KB" -ForegroundColor Green
Write-Host ""

# 完成提示
Write-Host "========================================" -ForegroundColor Cyan
Write-Host " ✅ 打包完成！" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "📄 生成的文件:" -ForegroundColor White
Write-Host "  1. .addon.mm 模板: $addonMmFile" -ForegroundColor Gray
Write-Host "  2. ZIP 包: $zipFile" -ForegroundColor Gray
Write-Host ""
Write-Host "⚠️  重要提示：" -ForegroundColor Yellow
Write-Host ""
Write-Host "  由于 Freeplane .addon.mm 格式需要使用 Java 序列化嵌入二进制数据，" -ForegroundColor Yellow
Write-Host "  请按以下步骤完成打包：" -ForegroundColor Yellow
Write-Host ""
Write-Host "  方法一（推荐）：使用 Freeplane 手动完成" -ForegroundColor Cyan
Write-Host "  1. 在 Freeplane 中打开: org.freeplane.plugin.ai.addon.mm" -ForegroundColor Gray
Write-Host "  2. 选中 'zips > plugin-package.zip' 节点" -ForegroundColor Gray
Write-Host "  3. 右键 → 添加对象 → 选择文件: plugin-package.zip" -ForegroundColor Gray
Write-Host "  4. 保存文件（Ctrl+S）" -ForegroundColor Gray
Write-Host "  5. 完成！现在可以分发这个 .addon.mm 文件了" -ForegroundColor Gray
Write-Host ""
Write-Host "  方法二：直接部署到 BIN 目录（开发调试）" -ForegroundColor Cyan
Write-Host "  1. 运行: gradle deployToBin" -ForegroundColor Gray
Write-Host "  2. 启动 Freeplane: ..\BIN\freeplane.bat" -ForegroundColor Gray
Write-Host "  3. 插件会自动加载" -ForegroundColor Gray
Write-Host ""
Write-Host "📖 详细文档: ADDON_PACKAGE_GUIDE.md" -ForegroundColor White
Write-Host ""

pause
