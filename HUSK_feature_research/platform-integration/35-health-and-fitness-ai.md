## 35. Health & Fitness AI

### Health Connect (Android 14+)

**50+ data types** including:
- Heart rate, HRV, SpO2, blood pressure
- Sleep stages (awake, light, deep, REM)
- Steps, distance, calories, exercise sessions
- Nutrition (macro/micronutrients)
- FHIR medical records

### ML Kit Pose Detection

Tracks **33 body landmarks** in real-time for:
- Exercise counting (reps)
- Yoga pose classification
- Form correction feedback
- Physical therapy tracking

### Activity Recognition

- **Transition API** — Detects walking, running, cycling, vehicle states
- Battery-efficient callbacks (no continuous sensor polling)
- **Sleep API** — Confidence-scored sleep segments reported every ~10 minutes

### Features to Build

1. **Health Dashboard** — Aggregate Health Connect data → Gemma 4 generates weekly health summary
2. **Exercise Coach** — Pose detection + function calling → real-time form feedback
3. **Sleep Analysis** — Sleep API data → LLM interpretation → actionable recommendations
4. **Meal Logging** — Photograph meals → Gemma 4 vision → nutritional estimation
5. **Medication Reminders** — NL-configured medication schedules with adaptive timing
6. **Wellness Journal** — Daily AI-generated health insights based on sensor data trends

### Privacy Note

All health data stays on-device. Health Connect requires explicit per-data-type permissions. HUSK should never transmit health data to any cloud service.

### Sources
- Activity Recognition: https://medium.com/@hariharan.b/activity-recognition-client-transition-sampling-and-sleep-api-c140e5289de4
- Sleep API: https://9to5google.com/2021/02/25/google-android-sleep-api/
- ML Kit Pose Detection: https://github.com/ibrahimcanerdogan/PoseDetectionApp-MLKit

---

