# 🤖 Freeplane AI Plugin

**Powerful AI for Freeplane mind maps** — supports OpenRouter, Gemini, Ollama, ERNIE, DashScope and more. Let AI read, edit, and build mind map nodes directly.

![AI Chat Panel](docs/screenshots/ai-chat-panel.png)
*AI Chat Panel with intelligent mind map interaction*

---

## ✨ Features

### 💬 AI Chat Panel
- **Right-tab integration**: seamlessly embedded in Freeplane UI
- **Multi-turn conversation**: context memory up to 65 536 tokens
- **Tool Call visualization**: clearly shows AI tool invocations
- **Font scaling**: adjustable chat font size (50%–200%)
- **Token counter**: real-time token usage display

### 🧠 AI Smart Tool Calls
AI can directly manipulate mind maps with **14 tools**:

| Category | Method | Description |
|----------|--------|-------------|
| **Read nodes** | `readNodesWithDescendants` | Read a node and all its descendants |
| **Fetch for editing** | `fetchNodesForEditing` | Get editable content fields (text, details, notes, attributes, etc.) |
| **Create nodes** | `createNodes` | Batch-create nodes with hierarchical structure |
| **Edit nodes** | `edit` | Unified edit API — text/details/notes/attributes/tags/icons/styles/hyperlinks/connectors |
| **Move nodes** | `moveNodes` | Move nodes to a new parent |
| **Delete nodes** | `deleteNodes` | Delete specified nodes |
| **Search nodes** | `searchNodes` | Search nodes by content, with regex support |
| **Select node** | `selectSingleNode` | Select and scroll to a specific node |
| **Get selection** | `getSelectedMapAndNodeIdentifiers` | Get currently selected map and node identifiers |
| **Create summary** | `createSummary` | Create a summary node aggregating child content |
| **Move into summary** | `moveNodesIntoSummary` | Move nodes into a summary node |
| **List icons** | `listAvailableIcons` | List application-level available icons |
| **List styles** | `listMapStyles` | List styles defined in the target map |
| **Edit connectors** | `editConnectors` | Edit connectors between nodes (source/target/labels) |

### 🌐 Multi-Provider AI Support

| Provider | Example Models | Config Key | Notes |
|----------|---------------|------------|-------|
| **OpenRouter** | GPT-5, Claude Sonnet 4.6, Gemini 2.5 Pro | `ai_openrouter_key` | Multi-model aggregator |
| **Google Gemini** | gemini-3-pro, gemini-2.5-flash | `ai_gemini_key` | Google's latest AI models |
| **Ollama** | llama3, mistral, qwen | `ai_ollama_service_address` | Local deployment, privacy-first |
| **ERNIE** | ernie-4.5-turbo, deepseek-v3 | `ai_ernie_key` | Baidu — Chinese-optimized |
| **DashScope** | qwen-max, qwen-plus | `ai_dashscope_key` | Alibaba Cloud AI service |

### 🔌 MCP Server (Model Context Protocol)
- **Port**: 6298 (configurable)
- **Token auth**: secure access control
- **External integration**: callable from Claude Desktop, Cursor, etc.
- **Remote control**: operate Freeplane mind maps via API

### 🎨 Three Interaction Modes

#### 1️⃣ **Chat Mode**
- **Use case**: discuss mind map content, get suggestions
- **Features**:
  - Natural-language dialogue
  - AI answers do not modify the map directly
  - Ideal for brainstorming and planning
- **How to enable**: select "Chat" in the AI panel top-right

#### 2️⃣ **Build Mode**
- **Use case**: let AI create and edit mind maps automatically
- **Features**:
  - AI can directly manipulate nodes
  - Auto-generate mind map structures
  - Batch create, edit, and organize nodes
- **How to enable**: select "Build" in the AI panel top-right
- **Example prompt**:
  ```
  "Create a mind map about Machine Learning with Supervised, Unsupervised, and Reinforcement Learning branches"
  ```

#### 3️⃣ **Auto Mode**
- **Use case**: AI intelligently decides when to chat and when to act
- **Features**:
  - AI picks the best interaction style
  - Switches mode based on user intent
  - Smartest and most efficient
- **How to enable**: select "Auto" in the AI panel top-right

### 🌟 Web Frontend (In Development)

Modern web UI built with Vue 3 + TypeScript (`freeplane_plugin_ai/frontend` and `freeplane_web`):

#### Core Features
- **Responsive design**: desktop, tablet, and phone
- **Node operations**: Tab to smart-create, Enter to quick-edit
- **Fold/unfold**: toggle node folding with persisted state
- **Delete confirmation**: elegant confirmation modal
- **Canvas sync**: AI operation results reflect on canvas in real time

#### AI Integration
- **AI assistant panel**: chat with AI in the web interface
- **Three-mode switch**: Chat / Build / Auto
- **Model selection**: switch between AI providers
- **Intelligent routing**: auto-select dialogue or tool call based on intent

#### Tech Stack
```
Frontend:  Vue 3 + TypeScript + Pinia + Vue Flow
Backend:   Java 8/17 + LangChain4j + OSGi + built-in HTTP server
Transport: REST API (port 6299) + MCP (port 6298)
```

### 🛠️ AI Edit Tracking
- **State icon**: marks AI-modified nodes
- **Persistence**: saves AI edit history
- **Clear markers**: bulk-clear AI markers
  - Map-wide: `Clear AI Markers in Map`
  - Selection: `Clear AI Markers in Selection`

---

## 📦 Installation

### Option 1: .addon.mm Package (Recommended)

1. Download the latest package: [org.freeplane.plugin.ai.addon.mm](https://github.com/ymy-yry/freeplane-ai-recreating/releases/latest)
2. Double-click the file, or in Freeplane: **Tools → Add-ons → Install**
3. Restart Freeplane

### Option 2: Manual JAR Install

1. Download the JAR: [freeplane_plugin_ai-1.13.3.jar](https://github.com/ymy-yry/freeplane-ai-recreating/releases/latest)
2. Copy the JAR into Freeplane's `plugins` directory:
   ```
   Windows: %APPDATA%\Freeplane\plugins\
   macOS:   ~/Library/Application Support/Freeplane/plugins/
   Linux:   ~/.freeplane/plugins/
   ```
3. Restart Freeplane → **Tools → Add-ons** to verify

### Option 3: Development Deployment

```bash
# Clone the repo
git clone https://github.com/ymy-yry/freeplane-ai-recreating.git
cd freeplane-ai-recreating

# Build the plugin JAR
cd freeplane_plugin_ai
gradle jar

# Generate the full .addon.mm package (recommended)
gradle packageAddonMM

# Output location:
# build/outputs/org.freeplane.plugin.ai.addon.mm

# Deploy to BIN directory (for dev testing)
gradle deployToBin

# Launch Freeplane
../BIN/freeplane.bat  # Windows
../BIN/freeplane.sh   # Linux/macOS
```

### 🎯 Build Commands

```bash
# 1. Compile the plugin
gradle :freeplane_plugin_ai:compileJava

# 2. Package the JAR
gradle :freeplane_plugin_ai:jar

# 3. Generate .addon.mm add-on package (with embedded binary data)
gradle :freeplane_plugin_ai:packageAddonMM

# 4. Deploy to Freeplane BIN directory
gradle :freeplane_plugin_ai:deployToBin

# 5. Run unit tests
gradle :freeplane_plugin_ai:test

# 6. Full build (compile + test + package)
gradle build
```

### 📦 Build Artifacts

| File | Size | Purpose |
|------|------|---------|
| `freeplane_plugin_ai-1.13.3.jar` | ~0.6 MB | JAR file — place manually in plugins directory |
| `org.freeplane.plugin.ai.addon.mm` | ~0.8 MB | **Recommended** — full add-on package, double-click to install |

> **💡 Tip**: the `.addon.mm` file contains all necessary binary data and can be installed directly in Freeplane with no extra steps.

---

## ⚙️ Configure API Keys

### Option A: Via UI

1. Open Freeplane
2. **Tools → Preferences → AI**
3. Fill in the API key for your chosen provider

### Option B: Via Config File

Edit `secrets.properties` in your Freeplane user directory:

```properties
# OpenRouter (recommended — multi-model)
ai_openrouter_key=sk-or-v1-xxxxx

# Google Gemini
ai_gemini_key=AIza-xxxxx

# ERNIE (Baidu)
ai_ernie_key=bce-v3/ALTAK-xxxxx

# DashScope (Alibaba Cloud)
ai_dashscope_key=sk-xxxxx

# Ollama (local deployment, optional)
ai_ollama_service_address=http://localhost:11434
```

### Get an API Key

| Provider | URL | Free Tier |
|----------|-----|-----------|
| OpenRouter | https://openrouter.ai/keys | $1 credit on signup |
| Google Gemini | https://aistudio.google.com/app/apikey | Free |
| ERNIE | https://qianfan.cloud.baidu.com/ | New-user credit |
| DashScope | https://dashscope.console.aliyun.com/ | Free tier |
| Ollama | https://ollama.ai/ | Completely free (local) |

---

## 🚀 Usage Examples

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

---

## 🔧 Advanced Configuration

### Sampling Parameters

> ⚠️ **Note**: some config keys below must be added manually to Freeplane's preferences or config file. Advanced Agent features are still in development.

```properties
# Temperature (creativity vs. determinism, 0.0–1.0)
temperature=0.2

# Top-P (sampling diversity, 0.0–1.0)
top_p=0.9

# Presence penalty (encourage new topics, -2.0 to 2.0)
presence_penalty=0.0

# Frequency penalty (reduce repetition, -2.0 to 2.0)
frequency_penalty=0.0
```

### Display Settings

```properties
# Show Tool Call history
ai_chat_shows_tool_calls=true

# Font scaling percentage
ai_chat_font_scaling=100

# Token counter display mode
ai_chat_token_counter_mode=visible

# Auto-unfold parents when creating nodes
ai_unfolds_parents_on_create=true

# AI edit state-icon visibility
ai_edits_state_icon_visible=true
```

---

## 📊 Performance

### Large Mind Maps
- **Virtual scrolling**: render only visible nodes
- **Incremental updates**: refresh only changed parts
- **Lazy loading**: load collapsed nodes on demand
- **Memory management**: auto-reclaim unused caches

### AI Response Acceleration
- **Streaming output**: display AI content in real time
- **Concurrent requests**: call multiple tools in parallel
- **Caching**: reuse identical AI responses
- **Token optimization**: smart context trimming to save tokens

---

## 🐛 Troubleshooting

### Issue 1: AI Panel Does Not Appear

**Cause**: Java version below 17

**Solution**:
```bash
# Check Java version
java -version

# Java 17 or later is required
# Download: https://adoptium.net/
```

### Issue 2: API Call Fails

**Checklist**:
- [ ] API Key is correctly configured
- [ ] Network connection is working
- [ ] Provider account has credit
- [ ] Firewall is not blocking requests

**Log location**:
```
Windows: %APPDATA%\Freeplane\freeplane.log
macOS:   ~/Library/Application Support/Freeplane/freeplane.log
Linux:   ~/.freeplane/freeplane.log
```

### Issue 3: Ollama Connection Fails

**Solution**:
```bash
# 1. Start Ollama
ollama serve

# 2. Pull a model
ollama pull llama3

# 3. Test connectivity
curl http://localhost:11434/api/tags

# 4. Configure Freeplane
ai_ollama_service_address=http://localhost:11434
```

### Issue 4: MCP Server Won't Start

**Cause**: port 6298 is in use

**Solution**:
```properties
# Change MCP port
ai_mcp_server_port=6299
```

---

## 📚 Developer Documentation

### Project Structure

```
freeplane_plugin_ai/
├── src/main/java/org/freeplane/plugin/ai/
│   ├── Activator.java                      # OSGi plugin entry point
│   ├── chat/                               # AI chat subsystem
│   │   ├── AIChatPanel.java               # Chat panel UI
│   │   ├── AIProviderConfiguration.java   # Provider config
│   │   └── AIModelSelection.java          # Model selection
│   ├── service/                            # AI service layer
│   │   ├── AIServiceFactory.java          # Service factory
│   │   ├── OpenRouterAIService.java       # OpenRouter impl
│   │   ├── GeminiAIService.java           # Gemini impl
│   │   └── OllamaAIService.java           # Ollama impl
│   ├── tools/                              # AI tool set (14 tools)
│   │   ├── AIToolSet.java                 # Tool set main class
│   │   ├── create/                        # Node creation tools
│   │   ├── edit/                          # Node editing tools
│   │   ├── move/                          # Node moving tools
│   │   ├── delete/                        # Node deletion tools
│   │   ├── search/                        # Node search tools
│   │   ├── read/                          # Node read tools
│   │   └── selection/                     # Node selection tools
│   ├── mcpserver/                          # MCP server
│   │   └── ModelContextProtocolServer.java
│   ├── restapi/                            # REST API
│   │   └── RestApiServer.java
│   └── edits/                              # AI edit tracking
│       ├── AIEdits.java
│       └── AiEditsStateIconProvider.java
├── frontend/                               # Web frontend (Vue 3)
│   ├── src/
│   │   ├── components/                    # Vue components
│   │   ├── views/                         # Page views
│   │   └── stores/                        # Pinia state management
│   └── package.json
└── build.gradle                            # Gradle build config
```

### Build Commands (see Build Commands section above)

> ⚠️ For the full list see the "Build Commands" section under Installation

### Adding a New AI Provider

1. Create a service class:
```java
public class NewProviderAIService implements IAIService {
    @Override
    public ChatResponse chat(ChatRequest request) {
        // implement AI call logic
    }
}
```

2. Register in the factory:
```java
// Add to AIServiceFactory.java
case "newprovider":
    return new NewProviderAIService(configuration);
```

3. Add config keys:
```properties
ai_newprovider_key=
ai_newprovider_service_address=
ai_newprovider_model_list=
```

---

## 🤝 Contributing

### Filing Issues
- Describe the problem
- Provide reproduction steps
- Attach log files
- Include environment info (OS, Java version, Freeplane version)

### Pull Requests
1. Fork this repo
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Commit: `git commit -m 'Add amazing feature'`
4. Push: `git push origin feature/amazing-feature`
5. Open a PR

### Code Style
- **Java**: follow Google Java Style
- **Frontend**: follow Vue Style Guide
- **Commit messages**: use imperative mood (e.g. "Fix bug" not "Fixed bug")
- **Tests**: new features must include unit tests

---

## 📄 License

This project is licensed under **GNU General Public License v2.0**

- Consistent with the Freeplane main project
- Free to use, modify, and distribute
- Modified code must remain open source

See the [LICENSE](LICENSE) file for details.

---

## 📋 Release Guide

### Tag Format

```
v1.13.3
```

### Release Title

```
v1.13.3 - AI Plugin for Freeplane
```

### Release Notes Template

```markdown
## 🤖 Freeplane AI Plugin v1.13.3

AI-powered mind mapping for Freeplane — supports OpenRouter, Gemini, Ollama, ERNIE, DashScope and more.

### ✨ Features
- 💬 AI Chat Panel: integrated right-tab, multi-turn conversation, Tool Call visualization
- 🧠 14 Smart Tools: create/edit/move/delete/search nodes, summaries, connector editing, etc.
- 🌐 Multi-Provider AI: OpenRouter / Google Gemini / Ollama / ERNIE (Baidu) / DashScope (Alibaba)
- 🔌 MCP Server: port 6298, callable from Claude Desktop, Cursor, etc.
- 🎨 Three Modes: Chat (dialogue) / Build (construction) / Auto (intelligent routing)
- 🛠️ AI Edit Tracking: state icons mark AI-modified nodes

### 📦 Downloads
| File | Description |
|------|-------------|
| `org.freeplane.plugin.ai.addon.mm` | **Recommended** — full add-on package, double-click or install via Freeplane |
| `freeplane_plugin_ai-1.13.3.jar` | JAR file, manually place in the `plugins` directory |

### 📥 Installation
1. Download `org.freeplane.plugin.ai.addon.mm`
2. Double-click the file, or in Freeplane: **Tools → Add-ons → Install**
3. Restart Freeplane
4. Configure API Key: **Tools → Preferences → AI**

### ⚙️ Requirements
- Freeplane 1.13.0 or later
- Java 17 or later
- At least one AI provider API key (Ollama local models require no key)

### 🔧 Full Changelog
See [AI_PLUGIN_README.md](https://github.com/ymy-yry/freeplane-ai-recreating/blob/main/freeplane_plugin_ai/AI_PLUGIN_README.md)
```

### Files to Upload

| File | Path | Required |
|------|------|----------|
| `org.freeplane.plugin.ai.addon.mm` | `freeplane_plugin_ai/build/outputs/` | **Yes** |
| `freeplane_plugin_ai-1.13.3.jar` | `freeplane_plugin_ai/build/libs/` | Optional |

### Generate Release Files

```bash
cd freeplane_plugin_ai

# One command to generate all release artifacts
gradle packageAddonMM

# Output:
# build/outputs/org.freeplane.plugin.ai.addon.mm  ← upload to Release
# build/libs/freeplane_plugin_ai-1.13.3.jar        ← upload to Release
```

---

## 🙏 Acknowledgements

- **Freeplane Team**: the powerful mind mapping platform
- **LangChain4j**: excellent Java AI integration framework
- **OpenRouter / Google / Baidu / Alibaba**: AI model services
- **Community contributors**: all Issue reporters and PR authors

---

## 📞 Contact

- **GitHub Issues**: https://github.com/ymy-yry/freeplane-ai-recreating/issues
- **Email**: [238966298g@gmail.com](mailto:238966298g@gmail.com)
- **Discussions**: https://github.com/ymy-yry/freeplane-ai-recreating/discussions

---

**Empower your mind maps with AI!** 🚀
