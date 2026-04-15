---
name: calculator
description: Evaluate math expressions, unit conversions, and calculations. Supports arithmetic, trigonometry, logarithms, and more.
---

# Calculator

This skill evaluates mathematical expressions and returns the result.

## Examples

* "What is 15% of 230?"
* "Calculate the square root of 144"
* "Convert 72 fahrenheit to celsius"
* "What is sin(45 degrees)?"
* "2^10 + 3 * 7"

## Instructions

Call the `run_js` tool with the following exact parameters:

- script name: `index.html`
- data: A JSON string with the following field
  - expression: the math expression to evaluate (e.g., "sqrt(144)", "15/100 * 230", "(72-32) * 5/9")

Supported operations:
- Basic: +, -, *, /, %, ** (power)
- Functions: sqrt, abs, ceil, floor, round, min, max
- Trig: sin, cos, tan, asin, acos, atan (input in radians)
- Logarithms: log (natural), log10, log2
- Constants: PI, E
- Conversion helpers: pass the full expression like "(72-32)*5/9" for Fahrenheit to Celsius
