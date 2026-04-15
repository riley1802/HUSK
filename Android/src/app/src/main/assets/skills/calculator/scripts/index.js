/*
 * Copyright 2026 Riley Thomason
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Safe math evaluator — no eval(), uses Function constructor with whitelisted math ops.
function evaluateExpression(expr) {
	// Replace common math function names with Math.* equivalents.
	let sanitized = expr
		.replace(/\bsqrt\b/g, 'Math.sqrt')
		.replace(/\babs\b/g, 'Math.abs')
		.replace(/\bceil\b/g, 'Math.ceil')
		.replace(/\bfloor\b/g, 'Math.floor')
		.replace(/\bround\b/g, 'Math.round')
		.replace(/\bmin\b/g, 'Math.min')
		.replace(/\bmax\b/g, 'Math.max')
		.replace(/\bsin\b/g, 'Math.sin')
		.replace(/\bcos\b/g, 'Math.cos')
		.replace(/\btan\b/g, 'Math.tan')
		.replace(/\basin\b/g, 'Math.asin')
		.replace(/\bacos\b/g, 'Math.acos')
		.replace(/\batan\b/g, 'Math.atan')
		.replace(/\blog\b/g, 'Math.log')
		.replace(/\blog10\b/g, 'Math.log10')
		.replace(/\blog2\b/g, 'Math.log2')
		.replace(/\bpow\b/g, 'Math.pow')
		.replace(/\bPI\b/gi, 'Math.PI')
		.replace(/\bE\b/g, 'Math.E')
		.replace(/\^/g, '**');

	// Validate: only allow digits, operators, parens, dots, commas, Math.*, whitespace.
	const allowedPattern = /^[0-9+\-*/%().,\s]|Math\.\w+/;
	const stripped = sanitized.replace(/Math\.\w+/g, '').replace(/[0-9+\-*/%().,\s]/g, '');
	if (stripped.length > 0) {
		throw new Error('Invalid characters in expression: ' + stripped);
	}

	const result = new Function('return (' + sanitized + ')')();
	if (typeof result !== 'number' || !isFinite(result)) {
		throw new Error('Expression did not evaluate to a finite number');
	}
	return result;
}

window['ai_edge_gallery_get_result'] = async (data) => {
	try {
		const jsonData = JSON.parse(data);
		const expression = jsonData['expression'];
		if (!expression) {
			return JSON.stringify({error: 'No expression provided'});
		}
		const result = evaluateExpression(expression);
		// Format: avoid unnecessary decimals for whole numbers.
		const formatted = Number.isInteger(result) ? result.toString() : result.toPrecision(10).replace(/\.?0+$/, '');
		return JSON.stringify({
			result: `${expression} = ${formatted}`,
			value: formatted,
		});
	} catch (e) {
		console.error(e);
		return JSON.stringify({error: `Calculation failed: ${e.message}`});
	}
};
