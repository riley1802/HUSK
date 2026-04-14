---
name: regex-helper
description: Test regular expressions against text, show matches, and explain regex patterns.
---

# Regex Helper

This skill tests regex patterns against input text and returns all matches.

## Examples

* "Test the regex \d+ against 'I have 42 cats and 7 dogs'"
* "Find all email addresses in this text: ..."
* "Does 'hello-world_123' match ^[a-z\-_0-9]+$ ?"

## Instructions

Call the `run_js` tool with the following exact parameters:

- script name: `index.html`
- data: A JSON string with the following fields:
  - pattern: the regex pattern (without delimiters, e.g., "\\d+")
  - text: the text to test against
  - flags: (optional) regex flags like "gi" for global case-insensitive. Default is "g".
