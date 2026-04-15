# AudioDecoder OOM on Large Audio Files

## What went wrong
Uploading a 120MB M4A file (approximately 1 hour of audio) to Audio Scribe
caused an out-of-memory crash. The AudioDecoder loaded all decoded PCM into
a single in-memory buffer. A 1-hour recording at 16kHz mono decodes to
approximately 635MB of raw PCM data, exceeding available heap.

## Why
The initial AudioDecoder implementation used a growable `ByteArrayOutputStream`
to accumulate all decoded PCM before converting to a `FloatArray`. This works
fine for short recordings but scales linearly with duration — there's no upper
bound on file length since the 30-second duration cap was removed.

## How it was fixed
1. Added a two-strategy approach in `AudioDecoder`:
   - **In-memory** (default): Used for files where decoded PCM < 50MB
   - **Temp file spool**: For larger files, decoded PCM is written to a temp file
     on disk, then read back in 30-second chunks during transcription
2. `AudioScribeViewModel` uses chunked Whisper transcription for audio > 5 minutes,
   processing 30-second segments sequentially rather than the entire buffer at once

## How to prevent
- Never accumulate unbounded decoded audio in memory
- Always use streaming/temp file strategies when the input size is unknown
- For any pipeline processing user-supplied files, assume worst case (hours of audio)
- Test with large files early (1hr+ recordings)
