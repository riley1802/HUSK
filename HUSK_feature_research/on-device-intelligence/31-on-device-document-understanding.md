## 31. On-Device Document Understanding

### Gemma 4 Vision (Primary Path)

Gemma 4 E2B/E4B handles OCR, chart comprehension, and document parsing natively — images go directly to the model. No separate OCR pipeline needed for most use cases.

### ML Kit Document Scanner (Production-Ready)

`play-services-mlkit-document-scanner:16.0.0` (GA) provides:
- Automatic edge detection and perspective correction
- Shadow removal
- PDF output
- No camera permissions required (uses system scanner)

### ML Kit Text Recognition v2

Covers Latin, CJK, and Devanagari scripts fully on-device:
- 50+ languages
- Block/line/word-level detection
- Bounding box coordinates for each element

### Complete Document Pipeline

1. **Capture** — ML Kit Document Scanner (edge detection, deskew, shadow removal)
2. **OCR** — ML Kit Text Recognition v2 (structured text extraction)
3. **Entity Extraction** — Gemma 4 identifies addresses, dates, amounts, names
4. **Structured Output** — Constrained decoding forces JSON schema compliance
5. **Auto-Fill** — Android Autofill framework integration
6. **Index** — Feed extracted text into RAG pipeline for future retrieval

### Sources
- ML Kit Text Recognition v2: https://developers.google.com/ml-kit/vision/text-recognition/v2
- ML Kit Document Scanner: https://developers.google.com/ml-kit/vision/doc-scanner

---

