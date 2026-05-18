# 🤖 Enhancing Freeplane with AI Capabilities - Proposal for Discussion

## 📋 Overview

I've been working on an AI plugin for Freeplane that integrates large language models directly into the mind mapping experience. This proposal outlines the current implementation and potential future directions for community discussion.

## ✨ What's Been Built

### Core Features Implemented

1. **AI Chat Panel** - Integrated into Freeplane's right-tab UI
   - Multi-turn conversation with context memory (up to 65,536 tokens)
   - Tool Call visualization showing AI interactions
   - Font scaling and token counter

2. **Smart Tool Calls** - AI can directly manipulate mind maps with 14 tools:
   - Read/Create/Edit/Move/Delete nodes
   - Search nodes with regex support
   - Create summaries and manage connectors
   - List available icons and styles

3. **Multi-Provider Support**:
   - OpenRouter (GPT-5, Claude Sonnet 4.6, Gemini 2.5 Pro)
   - Google Gemini (gemini-3-pro, gemini-2.5-flash)
   - Ollama (local deployment, privacy-first)
   - ERNIE (Baidu - Chinese-optimized)
   - DashScope (Alibaba Cloud - qwen-max, qwen-plus)

4. **MCP Server** (Model Context Protocol):
   - Port 6298 with token authentication
   - External integration with Claude Desktop, Cursor, etc.
   - Remote control of Freeplane mind maps via API

5. **Three Interaction Modes**:
   - **Chat Mode**: Discuss mind map content without modifying
   - **Build Mode**: Let AI create and edit mind maps automatically
   - **Auto Mode**: AI intelligently decides when to chat vs. act

## 🏗️ Technical Architecture

### Backend
- **Language**: Java 17 (with Java 8 bootstrap compatibility)
- **Framework**: LangChain4j 1.10.0
- **Plugin System**: OSGi (Knopflerfish 8.0.11)
- **Transport**: REST API (port 6299) + MCP (port 6298)

### Frontend (In Development)
- **Tech Stack**: Vue 3 + TypeScript + Pinia + Vue Flow
- **Features**: Responsive design, real-time canvas sync, node operations
- **AI Integration**: Three-mode switch, model selection, intelligent routing

### Key Components
```
freeplane_plugin_ai/
├── chat/           # AI chat subsystem
├── tools/          # 14 AI tool implementations
├── mcpserver/      # Model Context Protocol server
├── restapi/        # REST API server
├── edits/          # AI edit tracking & markers
└── buffer/         # Intelligent buffering layer
```

## 🚀 Potential Future Directions

### 1. Enhanced AI Capabilities
- **Intelligent Layout Suggestions**: AI recommends optimal mind map structures
- **Cross-Map Analysis**: AI analyzes multiple mind maps to find connections
- **Knowledge Graph Integration**: Transform mind maps into interconnected knowledge graphs

### 2. Performance Improvements
- **Streaming Responses**: Real-time AI output display
- **Concurrent Tool Execution**: Parallel processing of independent operations
- **Smart Caching**: Reuse identical AI responses to save tokens

### 3. User Experience
- **Voice Input/Output**: Natural voice interaction with AI
- **Collaborative AI**: Multiple users interacting with AI simultaneously
- **Personalized AI Profiles**: Custom AI behaviors based on user patterns

### 4. Integration Opportunities
- **External Knowledge Bases**: Connect AI to documentation, wikis, databases
- **Calendar/Task Sync**: AI-generated mind maps that sync with productivity tools
- **Version Control Integration**: AI-assisted mind map versioning and diffing

## 🤔 Discussion Points

1. **Community Interest**: Is there interest in integrating AI capabilities directly into Freeplane core vs. keeping it as a plugin?

2. **Privacy Concerns**: How should we handle data sent to external AI services? Should we prioritize local models (Ollama)?

3. **Feature Prioritization**: Which AI features would provide the most value to the community?

4. **Performance Impact**: How can we ensure AI features don't slow down the core Freeplane experience?

5. **Accessibility**: How do we make AI features accessible to users without API keys or technical knowledge?

## 🛠️ Current Status

- ✅ Core plugin functionality implemented
- ✅ Multiple AI provider support
- ✅ MCP server for external integration
- 🔄 Web frontend in development
- 📊 Performance optimization ongoing

## 🎥 Demo

### 三种交互模式演示

#### 1️⃣ 聊天模式（Chat Mode）
<!-- 在此插入聊天模式截图 -->
![聊天模式演示](待替换：聊天模式截图路径)
*图1：聊天模式 - 与AI讨论思维导图内容，不直接修改*

#### 2️⃣ 构建模式（Build Mode）
<!-- 在此插入构建模式截图 -->
![构建模式演示](待替换：构建模式截图路径)
*图2：构建模式 - AI自动创建和编辑思维导图节点*

#### 3️⃣ 自动模式（Auto Mode）
<!-- 在此插入自动模式截图 -->
![自动模式演示](待替换：自动模式截图路径)
*图3：自动模式 - AI智能决定何时对话、何时操作*

---

### Example 1: Create a Mind Map (Build Mode)

**User input**:
```
Create a mind map about "Python Programming" with:
- Basics (variables, data types, control flow)
- Functions & Modules
- Object-Oriented Programming
- Popular Libraries (NumPy, Pandas, Requests)
```

**AI auto-executes**:
1. Calls `createNodes` to create the root node "Python Programming"
2. Uses hierarchical parameters to create 4 first-level branches
3. Adds child nodes to each branch
4. Auto-expands all nodes

### Example 2: Edit Node Content (Build Mode)

**User input**:
```
Select the "Machine Learning" node and expand it with 3 child nodes
```

**AI operations**:
1. Calls `searchNodes` to locate the "Machine Learning" node
2. Calls `createNodes` to create 3 children:
   - Supervised Learning
   - Unsupervised Learning
   - Reinforcement Learning
3. Adds a short description to each child

### Example 3: Consultation (Chat Mode)

**User input**:
```
My mind map is about "Web Development" — what important topics am I missing?
```

**AI answer**:
```
Based on the current map, consider adding:

1. Frontend Frameworks (React, Vue, Angular)
2. Backend Tech (Node.js, Django, Spring)
3. Databases (MySQL, MongoDB, Redis)
4. DevOps (Docker, CI/CD, cloud deployment)
5. Security best practices

Want me to add these for you?
```

### Example 4: Remote Call via MCP

**Claude Desktop config**:
```json
{
  "mcpServers": {
    "freeplane": {
      "command": "curl",
      "args": ["http://localhost:6298/mcp"],
      "env": {
        "Authorization": "Bearer YOUR_TOKEN"
      }
    }
  }
}
```

**Claude conversation**:
```
Open the "Project Plan" mind map in Freeplane,
and add a "UI Design Review" child node under "Phase 2"
```

## 📦 Installation & Testing

The plugin is available as:
- Pre-packaged zip file ready for direct deployment
- JAR file for manual deployment
- Development build via Gradle

**Requirements**: Freeplane 1.13.0+, Java 17+, at least one AI provider API key (or Ollama for local)

## 💬 Next Steps

I'd love to hear the community's thoughts on:
- Which features are most valuable?
- How should we handle privacy and data security?
- Should this be integrated into core Freeplane or remain a plugin?
- What other AI capabilities would you like to see?

Looking forward to your feedback and suggestions!

---

**GitHub Repository**: https://github.com/ymy-yry/freeplane-ai-recreating  
**Documentation**: [AI_PLUGIN_README.md](https://github.com/ymy-yry/freeplane-ai-recreating/blob/main/freeplane_plugin_ai/AI_PLUGIN_README.md)