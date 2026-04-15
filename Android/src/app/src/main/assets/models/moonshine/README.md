# Moonshine TFLite assets

The Ambient Scribe transcription engine uses the Moonshine speech-to-text model in its
official TFLite port. **None of these files are bundled in this repository** — they exceed
the 10 MB asset budget and are distributed via git-lfs upstream. Side-load them into the
installed app's asset directory (or drop them here before rebuilding) to enable
transcription.

## Required files

All five must be present for `MoonshineTfliteEngine.isReady()` to become true. Drop them
into this directory (`Android/src/app/src/main/assets/models/moonshine/`) with the exact
filenames below:

| File                   | Role                                                  |
| ---------------------- | ----------------------------------------------------- |
| `preprocessor.tfl`     | 16 kHz PCM window -> log-mel features                 |
| `encoder.tfl`          | Features -> encoder hidden states                     |
| `decoder_initial.tfl`  | First decoder step (no KV cache input)                |
| `decoder.tfl`          | Subsequent decoder steps with KV-cache round-tripping |
| `tokenizer.json`       | HuggingFace Tokenizers vocabulary (NOT SentencePiece) |

## Source

Upstream repo: <https://github.com/moonshine-ai/moonshine-tflite>

The `.tfl` files are stored with git-lfs. Clone with `git lfs install && git lfs pull`
after cloning the repo, or download individual files via the GitHub web UI / raw URLs.

Pick either the `tiny` or `base` variant depending on the size/quality tradeoff you want;
both variants expose the same five-file layout. Copy the files from the variant directory
into this directory, renaming if the upstream filenames differ.

## SHA-256

Upstream does not publish stable SHA-256 digests per release at the time of writing. If
you want integrity checks, compute them locally after download and record them here.

## Notes

- Do not commit these files to this repository — the `.gitignore` does not block them but
  the 10 MB asset budget does.
- The decoder loop itself is not implemented in this build (see `MoonshineTfliteEngine.kt`
  for the pipeline shape); dropping the assets in alone will not enable transcription
  without completing the port.
