## 2. MCP (Model Context Protocol) Support

### What MCP Is

MCP is an open standard (created by Anthropic, November 2024) that provides a universal interface for LLMs to communicate with external data sources and tools. It uses a client-server architecture where an MCP Host (your app) contains an MCP Client that connects to MCP Servers exposing tools, resources, and prompts.

### Why It Matters for HUSK

MCP would transform HUSK from a self-contained chat app into a **universal tool-using agent**. Instead of hardcoding every tool (Wikipedia, maps, etc.) as a custom Agent Skill, MCP lets users connect to any MCP server — thousands already exist — and the model dynamically discovers and uses available tools.

### Implementation Path (Kotlin-Native)

**Official Kotlin SDK:** `io.modelcontextprotocol:kotlin-sdk:0.5.0`

The MCP Kotlin SDK is Kotlin Multiplatform (JVM, Native, JS, Wasm) maintained by Anthropic in collaboration with JetBrains. Key modules:

```kotlin
// Dependencies
implementation("io.modelcontextprotocol:kotlin-sdk:0.5.0")
// Or client-only
implementation("io.modelcontextprotocol:kotlin-sdk-client:0.5.0")
```

**Client implementation pattern:**

```kotlin
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation

val client = Client(
    clientInfo = Implementation(name = "husk-client", version = "1.0.0")
)
val transport = StreamableHttpClientTransport(
    client = httpClient, url = "http://server:3000/mcp"
)
client.connect(transport)
val tools = client.listTools().tools
```

**Transport options available:**
- `StdioClientTransport` — for local process-based servers
- `StreamableHttpClientTransport` — for HTTP/SSE-based remote servers
- `WebSocketClientTransport` — for WebSocket connections

### Android-Specific MCP SDK

There's also `dev.jasonpearson:mcp-android-sdk:1.0.0` which wraps the Kotlin SDK specifically for Android with:
- AndroidX Startup automatic initialization
- Thread-safe singleton management
- Lifecycle management (auto-start/stop with app lifecycle)
- Built-in Android-specific tools (device info, app data, file operations)
- WebSocket and HTTP/SSE transport layers
- ADB port forwarding support for development

### Features to Implement

1. **MCP Client Manager** — A settings screen where users can add/remove/configure MCP server connections (URL, auth, transport type)
2. **Tool Discovery UI** — When connected to an MCP server, auto-list available tools with descriptions so the model can use them
3. **MCP Tool Bridge for Agent Skills** — Bridge MCP tools into the existing Agent Skills architecture so Gemma 4 can call them via its native function calling
4. **Resource Browser** — Let users browse MCP Resources (files, databases, configs) from connected servers
5. **Prompt Templates** — Import and use MCP Prompt templates from servers
6. **Local MCP Server** — HUSK itself could expose an MCP server, letting external AI tools (Claude Code, Cursor, etc.) interact with HUSK's on-device model via ADB
7. **MCP Server Registry Integration** — Connect to the official MCP Registry for server discovery

### Key MCP Spec Features (Nov 2025 Release)

- Server-side agent loops (servers can run their own agentic reasoning)
- Parallel tool calls
- Elicitations (server-initiated user interactions)
- OAuth-based authorization
- Structured tool outputs
- `.well-known` URL discovery

### Sources
- MCP Specification: https://modelcontextprotocol.io/specification/2025-11-25
- MCP Kotlin SDK: https://github.com/modelcontextprotocol/kotlin-sdk
- Android MCP SDK: https://kaeawc.github.io/android-mcp-sdk/
- MCP Blog (1-year anniversary): https://blog.modelcontextprotocol.io/posts/2025-11-25-first-mcp-anniversary/
- MCP Wikipedia: https://en.wikipedia.org/wiki/Model_Context_Protocol
- Google Cloud MCP Guide: https://cloud.google.com/discover/what-is-model-context-protocol
- Data Science Dojo MCP Guide: https://datasciencedojo.com/blog/guide-to-model-context-protocol/
- Android Management API MCP: https://developers.google.com/android/management/use-android-management-mcp
- mobile-mcp (Mobile Automation MCP): https://github.com/mobile-next/mobile-mcp
- MCP Kotlin SDK Docs: https://modelcontextprotocol.github.io/kotlin-sdk/
- JetBrains MCP SDK: https://github.com/JetBrains/mcp-kotlin-sdk
- Kotlin MCP Server Guide: https://medium.com/@nishantpardamwar/building-an-mcp-server-in-kotlin-a-step-by-step-guide-7ec96c7d9e00
- Android MCP SDK API Reference: https://kaeawc.github.io/android-mcp-sdk/api-reference/
- Kotlin Android MCP Server (PulseMCP): https://www.pulsemcp.com/servers/kotlin-android

---

