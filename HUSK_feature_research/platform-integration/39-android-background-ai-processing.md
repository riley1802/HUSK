## 39. Android Background AI Processing

### WorkManager Integration

**94.3% task completion** even under restrictive battery conditions. Best practices for AI workloads:

```kotlin
val embeddingWork = OneTimeWorkRequestBuilder<EmbeddingWorker>()
    .setConstraints(Constraints.Builder()
        .setRequiresCharging(true)
        .setRequiresDeviceIdle(true)
        .build())
    .build()
WorkManager.getInstance(context).enqueue(embeddingWork)
```

### Task Categories

| Task Type | WorkManager Config | When |
|-----------|-------------------|------|
| Embedding generation | `requiresCharging + requiresIdle` | Overnight |
| RAG index building | `requiresCharging + requiresIdle` | Overnight |
| Model pre-warming | `ExpeditedWork` | App startup |
| Notification processing | `PeriodicWork(15min)` | Background |
| Document OCR batch | `requiresCharging` | Plugged in |

### Critical Design Patterns

- Decompose work into **<30-second chunks** (35–40% better completion rates)
- Android 15 limits `mediaProcessing` foreground services to **6 hours per 24-hour period**
- For user-facing inference, `ExpeditedWork` resists Doze mode
- Use `ForegroundService` with `FOREGROUND_SERVICE_TYPE_DATA_SYNC` for active model loading

### Sources
- WorkManager: https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
- Background Task Management: https://eajournals.org/bjms/wp-content/uploads/sites/21/2025/07/System-Aware-Background.pdf

---

