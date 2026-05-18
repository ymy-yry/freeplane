// create-complete-addon.groovy
// 创建完整的 .addon.mm 文件，包含嵌入的 ZIP 数据（无需 Freeplane）

import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

def jarFile = args[0] as File
def outputFile = args[1] as File
def version = args[2]

println "=== Freeplane AI Plugin 完整打包工具 ==="
println "JAR 文件: ${jarFile.name}"
println "版本: ${version}"
println "输出: ${outputFile.name}"
println ""

// 1. 创建 ZIP 文件
println "[1/3] 创建 ZIP 包..."
def tempZipFile = File.createTempFile("plugin", ".jar.zip")
tempZipFile.deleteOnExit()

def zipOut = new ZipOutputStream(new FileOutputStream(tempZipFile))
def entry = new ZipEntry(jarFile.name)
zipOut.putNextEntry(entry)
zipOut.write(jarFile.bytes)
zipOut.closeEntry()
zipOut.close()

def zipBytes = tempZipFile.bytes
println "  ZIP 大小: ${(zipBytes.length / 1024 / 1024).toFixed(2)} MB"

// 2. 序列化二进制数据（使用 Java 序列化）
println "[2/3] 序列化二进制数据..."

// 创建 DataFlavor 对象（与 Freeplane 兼容）
def dataFlavor = DataFlavor.stringFlavor

// 使用 ByteArrayOutputStream 和 ObjectOutputStream 序列化
def baos = new ByteArrayOutputStream()
def oos = new ObjectOutputStream(baos)

// 写入字节数组（这是 Freeplane 存储二进制数据的方式）
oos.writeObject(zipBytes)
oos.flush()
oos.close()

def serializedBytes = baos.toByteArray()
println "  序列化后大小: ${(serializedBytes.length / 1024 / 1024).toFixed(2)} MB"

// 3. 转换为 Base64 并构建 XML
println "[3/3] 生成 .addon.mm 文件..."

def base64Data = serializedBytes.encodeBase64().toString()

// 构建完整的 .addon.mm XML
def addonMmContent = """<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<map version="1.0.1">
<!-- AI Plugin Add-On Package for Freeplane -->
<!-- Generated: ${new Date().format('yyyy-MM-dd HH:mm:ss')} -->
<!-- This is a complete addon package with embedded binary data -->
<node TEXT="AI Plugin for Freeplane" FOLDED="false">
    <node TEXT="properties" POSITION="right">
        <attribute NAME="name" VALUE="org.freeplane.plugin.ai"/>
        <attribute NAME="version" VALUE="${version}"/>
        <attribute NAME="author" VALUE="Freeplane Team"/>
        <attribute NAME="description" VALUE="AI-powered chat and intelligent node operations with LangChain4j"/>
        <attribute NAME="freeplaneVersionFrom" VALUE="1.13.0"/>
        <attribute NAME="freeplaneVersionTo" VALUE=""/>
    </node>
    <node TEXT="description" POSITION="right">
        <html>
          <body>
            <h2>🤖 AI Plugin for Freeplane</h2>
            <p>为 Freeplane 集成 AI 能力，支持多种大语言模型提供商。</p>
            <h3>✨ 主要功能</h3>
            <ul>
              <li><b>多模型支持</b>：OpenRouter、Google Gemini、Ollama、文心一言、通义千问</li>
              <li><b>AI 聊天面板</b>：直接在 Freeplane 中与 AI 对话</li>
              <li><b>智能节点操作</b>：AI 辅助创建、编辑、组织思维导图节点</li>
              <li><b>REST API</b>：提供外部集成的 HTTP API 接口</li>
              <li><b>MCP 服务器</b>：支持 Model Context Protocol</li>
              <li><b>AI 编辑追踪</b>：记录 AI 对节点的修改历史</li>
            </ul>
            <h3>⚙️ 系统要求</h3>
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
            <attribute NAME="plugins:org.freeplane.plugin.ai:ai_panel" VALUE="AI 聊天"/>
            <attribute NAME="plugins:org.freeplane.plugin.ai:name" VALUE="AI 插件"/>
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
        <node TEXT="plugin.jar.zip">
            <object class="java.awt.datatransfer.DataFlavor" index="0"/>
            <object class="[B" index="0">${base64Data}</object>
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
</map>"""

// 写入文件
outputFile.text = addonMmContent

println ""
println "========================================"
println "✅ 完整打包成功！"
println "========================================"
println ""
println "📄 生成文件: ${outputFile.absolutePath}"
println "📦 文件大小: ${(outputFile.length() / 1024 / 1024).toFixed(2)} MB"
println ""
println "✨ 这是一个完整的 .addon.mm 文件，可以直接安装！"
println "   无需在 Freeplane 中手动添加 ZIP 文件。"
println ""
println "安装方法:"
println "  1. 双击此文件"
println "  2. 或在 Freeplane 中: 工具 → 管理附加组件 → 安装"
println ""
