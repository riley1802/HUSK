---
name: json-formatter
description: Pretty-print, validate, and minify JSON data. Also supports extracting values by path.
---

# JSON Formatter

This skill formats, validates, and queries JSON data.

## Examples

* "Format this JSON: {"name":"Riley","age":25}"
* "Is this valid JSON? {broken: data}"
* "Minify this JSON: [formatted JSON]"
* "Get the value at path 'users.0.name' from this JSON: ..."

## Instructions

Call the `run_js` tool with the following exact parameters:

- script name: `index.html`
- data: A JSON string with the following fields:
  - json: the JSON string to process
  - action: (optional) "format" (default), "validate", "minify", or "query"
  - path: (optional, for "query" action) dot-notation path like "users.0.name"
