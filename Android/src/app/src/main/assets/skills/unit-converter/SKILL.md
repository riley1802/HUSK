---
name: unit-converter
description: Convert between units of measurement — temperature, weight, distance, volume, speed, data, and time.
---

# Unit Converter

This skill converts values between different units of measurement.

## Examples

* "Convert 72°F to Celsius"
* "How many kilometers is 26.2 miles?"
* "Convert 5 GB to MB"
* "180 lbs in kg"

## Instructions

Call the `run_js` tool with the following exact parameters:

- script name: `index.html`
- data: A JSON string with the following fields:
  - value: the numeric value to convert
  - from: the source unit (e.g., "fahrenheit", "miles", "kg", "GB")
  - to: the target unit (e.g., "celsius", "kilometers", "lbs", "MB")
