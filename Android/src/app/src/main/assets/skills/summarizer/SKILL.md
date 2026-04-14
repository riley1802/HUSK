---
name: summarizer
description: Summarize long text, articles, or documents into concise bullet points or paragraphs.
---

# Summarizer

This skill takes long text and produces a concise summary.

## Examples

* "Summarize this article: [paste text]"
* "Give me the key points from this: [paste text]"
* "TLDR this: [long text]"

## Instructions

This skill does NOT use `run_js`. Instead, directly summarize the user's text yourself using these rules:

1. Read the full text provided by the user
2. Identify the key points, main arguments, and conclusions
3. Produce a summary in this format:
   - **One-sentence overview** at the top
   - **3-7 bullet points** covering the key information
   - **Notable details** if any are particularly important
4. Keep the summary under 200 words
5. After summarizing, use `save_memory` to store the summary in L2 memory with category "summary" and relevant tags, so it can be retrieved later
