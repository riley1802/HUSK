# HUSK — 3-Layer Memory System, MCP Platform & Agent Skills Expansion

**Date:** 2026-04-13
**Author:** Riley Thomason + Claude
**Status:** Design approved, pending implementation plan
**Target Device:** Samsung Galaxy Z Fold 7 (Snapdragon 8 Elite, 12 GB RAM)
**Research Source:** HUSK_Feature_Research_MEGA.md (Sections 2, 3, 8, 9, 10, 15, 24, 25)

---

## Context

HUSK is a fork of Google AI Edge Gallery — a Kotlin/Jetpack Compose Android app running on-device LLMs via LiteRT-LM. It currently supports chat, agent skills, multimodal input, and benchmarking. The app has been rebranded with a monochrome dark theme and warm sand accent.

**Problem:** HUSK is a capable chat app but has no persistence, no external connectivity, and a flat skill system. Every conversation starts from zero. The model can't act on the outside world. Skills can't leverage context.

**Goal:** Transform HUSK into a private, on-device AI assistant that knows its user, grows smarter over time, and can reach out to external tools when needed. The architecture must be extensible — a platform for future capabilities, not a fixed feature set.

**Approach:** Platform First — build the infrastructure (memory, MCP, skill framework), then layer skills on top in waves.

---

## Architecture Overview

```
+------------------------------------------------------------------+
|                        HUSK Application                           |
|                                                                   |
|  +-------------------+  +-------------------+  +---------------+  |
|  | L1 Hot Memory     |  | L2 Warm Memory    |  | L3 External   |  |
|  | (Proto DataStore) |  | (Room + FTS4)     |  | (MCP Client)  |  |
|  | ~500 tokens       |  | Unbounded         |  | Live data     |  |
|  | Always injected   |  | Tool-accessed     |  | Tool-accessed |  |
|  +--------+----------+  +--------+----------+  +-------+-------+  |
|           |                      |                      |          |
|  +--------+----------------------+----------------------+-------+  |
|  |                    MemoryToolSet                              |  |
|  |  promote/demote/update L1 | search/save/update/delete L2     |  |
|  +------------------------------+-------------------------------+  |
|                                 |                                  |
|  +------------------------------+-------------------------------+  |
|  |                  LiteRT-LM Function Calling                  |  |
|  |  MemoryToolSet + McpToolBridge + EnhancedAgentTools          |  |
|  +------------------------------+-------------------------------+  |
|                                 |                                  |
|  +------------------------------+-------------------------------+  |
|  |                   Skill Framework (3-level)                  |  |
|  |  L1 Metadata (all skills) → L2 Instructions → L3 Resources  |  |
|  +--------------------------------------------------------------+  |
+------------------------------------------------------------------+
```

---

## L1 — Hot Memory (Always-Present Context)

### Purpose
A small, curated block injected into the system prompt of every new conversation. Contains only what's immediately relevant: current projects, recent events, key facts the model needs right now.

### Storage
Proto DataStore (matches existing HUSK pattern). New proto message in `settings.proto`:

```proto
message HotMemory {
  repeated HotMemoryEntry entries = 1;
  int64 last_updated_ms = 2;
}

message HotMemoryEntry {
  string key = 1;        // "current_project"
  string content = 2;    // "Building HUSK - on-device AI assistant"
  string category = 3;   // "project" | "event" | "fact" | "preference"
  int64 created_ms = 4;
  int64 expires_ms = 5;  // optional TTL for time-sensitive entries
  int32 priority = 6;    // model-assigned importance (1-10)
}
```

### Constraints
- Hard cap: ~500 tokens total for L1 content (measured as `totalChars / 4`, a standard approximation for English text — refinable later with actual tokenizer if needed)
- Enforced in the `promoteToL1` tool — rejects if adding would exceed budget
- When full, the model must demote something to L2 before promoting new content

### Injection Point
Prepended to the system prompt in `LlmModelHelper` before every conversation:

```
[HUSK Memory — What you know about the user right now]
{serialized L1 entries}

[Available tools]
You have memory tools to access deeper context and MCP tools for external data.
Use these proactively — don't ask the user for information you can look up.
```

### Model Tools
- `promoteToL1(key, content, category, priority)` — add to hot context
- `demoteFromL1(key)` — push entry down to L2
- `updateL1(key, content)` — modify an existing entry

### Key Files to Modify
- `Android/src/app/src/main/proto/settings.proto` — add HotMemory message
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/DataStoreRepository.kt` — add L1 read/write methods
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/runtime/LlmModelHelper.kt` — inject L1 into system prompt

---

## L2 — Warm Memory (On-Demand Knowledge Store)

### Purpose
Large on-device store of everything HUSK has learned about the user. Facts, preferences, conversation summaries, behavioral patterns. Never auto-injected — the model queries it via function calling when it needs more context.

### Storage
Room database with FTS4 full-text search. New database separate from existing DataStore system.

```kotlin
@Entity(tableName = "memories")
data class Memory(
    @PrimaryKey val id: String,          // UUID
    val content: String,                  // "User prefers Kotlin over Java"
    val category: String,                 // "fact" | "preference" | "summary" | "behavior"
    val tags: String,                     // comma-separated: "kotlin,programming,languages"
    val source: String,                   // "conversation:2026-04-13" | "extracted"
    val confidence: Float,                // model's confidence (0.0-1.0)
    val accessCount: Int,                 // retrieval frequency
    val createdMs: Long,
    val updatedMs: Long,
    val lastAccessedMs: Long
)
```

### Query Layer
Room DAO with FTS4. Requires a companion `@Fts4` virtual table entity linked to `Memory`:

```kotlin
@Fts4(contentEntity = Memory::class)
@Entity(tableName = "memories_fts")
data class MemoryFts(
    val content: String,
    val category: String,
    val tags: String
)

@Dao
interface MemoryDao {
    @Query("SELECT memories.* FROM memories JOIN memories_fts ON memories.rowid = memories_fts.rowid WHERE memories_fts MATCH :query ORDER BY memories.confidence DESC LIMIT :limit")
    fun search(query: String, limit: Int = 10): List<Memory>

    @Query("SELECT * FROM memories WHERE category = :category ORDER BY updatedMs DESC")
    fun getByCategory(category: String): List<Memory>

    @Query("SELECT * FROM memories ORDER BY accessCount DESC LIMIT :limit")
    fun getMostAccessed(limit: Int = 20): List<Memory>
}
```

### Model Tools
- `searchMemory(query, category?, limit?)` — keyword search across all memories
- `saveMemory(content, category, tags, confidence)` — store a new memory
- `updateMemory(id, content?, confidence?)` — revise existing memory
- `deleteMemory(id)` — forget something
- `listMemories(category?, sortBy?)` — browse stored memories

### Scale
No hard cap. `accessCount` and `lastAccessedMs` enable future pruning of stale memories.

### Key Files to Create
- `data/memory/MemoryDatabase.kt` — Room database definition
- `data/memory/Memory.kt` — entity
- `data/memory/MemoryDao.kt` — DAO with FTS4
- `data/memory/MemoryRepository.kt` — repository wrapping DAO

---

## L3 — MCP Platform (External Context)

### Purpose
The model reaches outside the device to fetch live data via MCP server connections. Designed as an extensible platform — easy to add new servers, transports, and tools over time.

### MCP Client Core

```kotlin
@Singleton
class McpManager @Inject constructor(
    private val dataStore: DataStoreRepository
) {
    private val connections = mutableMapOf<String, Client>()

    suspend fun connect(serverConfig: McpServerConfig): Client
    suspend fun disconnect(serverId: String)
    fun getAvailableTools(): List<McpTool>
    suspend fun callTool(serverId: String, toolName: String, args: JsonObject): JsonObject
}
```

**Dependency:** `io.modelcontextprotocol:kotlin-sdk-client:0.5.0`

### Server Configuration
Proto DataStore (small structured config):

```proto
message McpServerConfig {
  string id = 1;
  string name = 2;
  string url = 3;
  string transport = 4;      // "http" | "stdio" | "websocket"
  string auth_token = 5;     // encrypted via AndroidX Security Crypto
  bool auto_connect = 6;
  repeated string enabled_tools = 7;
}

message McpServerRegistry {
  repeated McpServerConfig servers = 1;
}
```

### Transport Abstraction
Pluggable transport layer via Hilt `@IntoSet`:

```kotlin
interface McpTransport {
    suspend fun connect(config: McpServerConfig): McpConnection
    suspend fun disconnect()
    fun isSupported(config: McpServerConfig): Boolean
}

// Shipped implementations
class HttpSseTransport : McpTransport { ... }
class StdioTransport : McpTransport { ... }
class WebSocketTransport : McpTransport { ... }
// Future: BluetoothTransport, NearbyConnectionsTransport
```

### Server Discovery
Multiple discovery methods:
- Manual URL configuration (baseline)
- `.well-known/mcp` auto-discovery
- MCP Registry browsing
- QR code / deep link import
- Local network server detection

### Tool Bridge
Normalizes MCP tools into LiteRT-LM function calling format:

```kotlin
class McpToolBridge @Inject constructor(
    private val mcpManager: McpManager
) {
    fun bridgeTools(serverId: String): List<BridgedTool>
    suspend fun executeBridgedTool(tool: BridgedTool, args: JsonObject): ToolResult
}
```

### Capability Manifests
Lightweight server metadata for progressive disclosure:

```kotlin
data class ServerCapability(
    val serverId: String,
    val name: String,
    val description: String,
    val categories: List<String>,
    val toolCount: Int
)
```

The model sees all capability manifests (~100 tokens each) and loads full tool schemas only when it chooses to use a specific server.

### Permission & Safety
- Per-server read-only vs read-write toggle
- Per-tool allow/deny lists
- Confirmation prompts for destructive actions
- Rate limiting per server
- Audit log of all external calls

### Connection Lifecycle
- Auto-reconnect on network recovery
- Health monitoring
- Graceful degradation (model gets told "server unavailable" not raw errors)
- Background keepalive for auto-connect servers
- Lifecycle-aware (connects/disconnects with app lifecycle)

### Settings UI
New section in SettingsDialog for managing MCP servers: add, remove, configure, view connection status, toggle tools.

### Key Files to Create
- `data/mcp/McpManager.kt` — singleton connection manager
- `data/mcp/McpToolBridge.kt` — tool normalization
- `data/mcp/McpTransport.kt` — transport interface
- `data/mcp/transports/HttpSseTransport.kt`
- `data/mcp/transports/StdioTransport.kt`
- `data/mcp/transports/WebSocketTransport.kt`
- `data/mcp/McpServerConfig.kt` — config model
- `ui/settings/McpSettingsScreen.kt` — server management UI

### Key Files to Modify
- `Android/src/app/src/main/proto/settings.proto` — add McpServerConfig/Registry
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/home/SettingsDialog.kt` — add MCP section
- `Android/src/app/build.gradle.kts` — add MCP SDK dependency

---

## Memory Self-Management

### Purpose
The model autonomously manages its own memory across L1 and L2. It decides what to remember, promote, demote, update, and forget without user curation.

### MemoryToolSet
Single tool set registered in LiteRT-LM function calling, available in every conversation:

```kotlin
class MemoryToolSet(
    private val hotMemoryStore: HotMemoryStore,
    private val memoryRepository: MemoryRepository
) : ToolSet {
    // L1 tools
    @Tool("Promote important context to always-visible memory")
    fun promoteToL1(key, content, category, priority): String

    @Tool("Demote context from always-visible to searchable memory")
    fun demoteFromL1(key): String

    @Tool("Update an existing hot memory entry")
    fun updateL1(key, content): String

    // L2 tools
    @Tool("Search stored memories for relevant context")
    fun searchMemory(query, category?, limit?): String

    @Tool("Save a new memory about the user or conversation")
    fun saveMemory(content, category, tags, confidence): String

    @Tool("Update an existing memory")
    fun updateMemory(id, content?, confidence?): String

    @Tool("Remove a memory that is no longer accurate")
    fun deleteMemory(id): String

    @Tool("List memories by category")
    fun listMemories(category?, sortBy?): String
}
```

### Three Management Triggers

**1. During conversation** — model calls memory tools naturally mid-conversation when it learns new information about the user.

**2. End-of-conversation extraction** — when a conversation ends or the app backgrounds, a background inference pass runs:

```
Review this conversation. You have access to memory tools.
1. Extract new facts, preferences, or context → save_memory
2. Update outdated memories → update_memory
3. Promote critical info to L1 → promote_to_l1
4. Demote stale L1 entries → demote_from_l1
Be selective. Do not save trivial information.
```

Uses LiteRT-LM automatic tool calling recursion (up to 5 calls).

**3. L1 housekeeping** — periodic background check (daily or on app launch) where the model reviews L1 entries, prunes expired ones, and rebalances priorities.

### Guardrails
- L1 token budget enforced in `promoteToL1` (rejects if exceeding ~500 tokens)
- Memory writes during conversation are fire-and-forget (non-blocking)
- Extraction pass capped at 5 tool call iterations
- All operations logged for audit trail

### Key Files to Create
- `data/memory/MemoryToolSet.kt` — LiteRT-LM ToolSet implementation
- `data/memory/HotMemoryStore.kt` — L1 Proto DataStore wrapper
- `data/memory/MemoryExtractionWorker.kt` — WorkManager task for end-of-conversation extraction
- `data/memory/MemoryHousekeepingWorker.kt` — WorkManager periodic L1 cleanup

---

## Agent Skills Framework Upgrade

### Purpose
Upgrade the flat skill system to support progressive disclosure, memory/MCP integration, and extensibility (including model-generated skills).

### Current State
Skills are JS + HTML bundles in `assets/skills/` with SKILL.md docs. Loaded via `AgentTools.loadSkill()`, executed via `AgentTools.runJs()`. No memory access, no MCP access, flat loading.

### New 3-Level Skill Loading

```
L1 Metadata (~100 tokens/skill)
  Name + one-line description. ALL skills loaded at startup.
  Model sees full catalog, picks what's relevant.

L2 Instructions (<5,000 tokens)
  Full SKILL.md body. Loaded only when model selects a skill.

L3 Resources (unlimited)
  External data, API schemas, templates.
  Loaded only during execution.
```

50+ skills at ~100 tokens each = ~5,000 tokens for the full catalog. Well within context budget.

### Enhanced Agent Tools

```kotlin
class EnhancedAgentTools(
    private val memoryToolSet: MemoryToolSet,
    private val mcpManager: McpManager
) : ToolSet {
    @Tool("Load a skill for use")
    fun loadSkill(skillId: String): String

    @Tool("Execute JavaScript in sandbox")
    fun runJs(code: String): String

    @Tool("Search user context relevant to current skill")
    fun getSkillContext(query: String): String  // wraps searchMemory

    @Tool("Call an external tool via MCP")
    fun callExternal(server: String, tool: String, args: String): String
}
```

### Skill Sources
- **Bundled** — shipped in `assets/skills/` (existing)
- **User-created** — stored locally
- **Community** — imported from URLs (existing, formalized)
- **Generated** — model creates SKILL.md at runtime ("Skill Factory")

### Key Files to Modify
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/agentchat/` — upgrade AgentTools
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/common/chat/` — skill catalog UI

---

## Initial Skill Set

### Wave 1 — Ships with platform

| Skill | Type | Memory | MCP |
|-------|------|--------|-----|
| GitHub Integration | MCP server | L2 (repos, preferences) | L3 (issues, PRs) |
| Web Search | MCP server | L1 (current project) | L3 (search API) |
| File Browser | Native tool | L2 (file patterns) | No |
| Task Manager | JS skill | L1+L2 (persisted tasks) | No |
| Note Taker | JS skill | L2 (searchable notes) | No |
| Calculator | JS skill | No | No |
| Calendar Integration | Native tool | L1 (upcoming events) | No |

### Wave 2 — Builds on Wave 1

| Skill | Type | Depends on |
|-------|------|-----------|
| Email Draft | JS skill | Memory (tone/style) |
| Document Scanner + OCR | Native tool | File Browser, feeds L2 |
| Clipboard Manager | Native tool | Memory (history in L2) |
| Code Execution (JS sandbox) | Native tool | GitHub skill context |
| Summarizer | JS skill | Memory (reading history) |
| Smart Home (Google Home API) | Native tool | MCP bridge |
| System Settings Control | Native tool | Existing Mobile Actions |

### Wave 3 — Full expansion

| Skill | Type | Depends on |
|-------|------|-----------|
| Notification Intelligence | Native tool | Memory + calendar |
| Health Dashboard | Native tool | Health Connect + memory |
| Semantic Search (EmbeddingGemma) | Native tool | L2 memory + RAG |
| Translation | JS skill | Gemma 4 multilingual |
| Skill Factory | Meta-skill | Full framework maturity |
| Custom MCP server builder | Power-user tool | MCP platform maturity |

---

## Verification Plan

### L1 Memory
1. Start a conversation, tell HUSK something important ("I'm working on project X")
2. Verify the model calls `promoteToL1` autonomously
3. Start a NEW conversation — verify L1 context is present in the system prompt
4. Fill L1 to capacity — verify the model demotes lower-priority entries

### L2 Memory
1. Have a multi-turn conversation with personal information
2. End the conversation — verify extraction worker runs and saves memories
3. Start a new conversation, ask something that requires L2 — verify the model calls `searchMemory`
4. Verify FTS4 search returns relevant results across categories

### L3 MCP
1. Configure a GitHub MCP server in settings
2. Ask HUSK about a GitHub issue — verify it calls the MCP tool and returns live data
3. Disconnect the server — verify graceful degradation message
4. Add a second server — verify tool bridge aggregates tools from both

### Memory Self-Management
1. Over 5+ conversations, verify L1 evolves (stale entries demoted, new ones promoted)
2. Verify the end-of-conversation extraction pass produces meaningful memories
3. Verify the model updates existing memories when information changes
4. Verify L1 never exceeds ~500 token budget

### Skills Framework
1. Verify all skill L1 metadata loads at conversation start
2. Select a skill — verify L2 instructions load on demand
3. Use a skill that needs memory — verify `getSkillContext` returns relevant data
4. Use a skill that needs MCP — verify `callExternal` routes correctly

### Integration
1. Full flow: ask HUSK about a GitHub issue → it checks L2 for repo context → calls L3 MCP for live data → saves relevant findings to L2 → responds with grounded answer
2. Full flow: tell HUSK about a life event → it saves to L2 → promotes to L1 if important → next conversation reflects the knowledge
