## 15. Memory & Context Management

### Conversation Memory

1. **Summarization-Based Memory** — Periodically summarize old conversation turns to compress context
2. **Sliding Window with Summary** — Keep last N turns verbatim + summary of earlier turns
3. **Topic-Based Memory** — Segment conversations by topic, retrieve relevant topic memory for new queries
4. **Cross-Conversation Memory** — Maintain a persistent "user profile" updated from all conversations
5. **Memory Editing** — Let users view and edit what the model remembers about them
6. **Memory Categories** — Separate memories into facts, preferences, tasks, relationships

### Context Window Management

Given Gemma 4's 128K context window:

7. **Context Budget Display** — Show current context usage (tokens used / tokens available)
8. **Smart Truncation** — When approaching context limit, summarize oldest messages rather than dropping them
9. **Priority Context** — Mark certain messages as "always keep" to prevent truncation
10. **Context Compression** — Use the model itself to compress verbose context into essential information

---

