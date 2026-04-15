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

function getByPath(obj, path) {
	const parts = path.split('.');
	let current = obj;
	for (const part of parts) {
		if (current === null || current === undefined) return undefined;
		current = current[part];
	}
	return current;
}

window['ai_edge_gallery_get_result'] = async (data) => {
	try {
		const jsonData = JSON.parse(data);
		const jsonStr = jsonData['json'];
		const action = jsonData['action'] || 'format';
		const path = jsonData['path'] || '';

		if (!jsonStr) return JSON.stringify({error: 'No JSON string provided'});

		// Validate.
		let parsed;
		try {
			parsed = JSON.parse(jsonStr);
		} catch (e) {
			if (action === 'validate') {
				return JSON.stringify({
					result: `Invalid JSON: ${e.message}`,
					valid: false,
				});
			}
			return JSON.stringify({error: `Invalid JSON: ${e.message}`});
		}

		switch (action) {
			case 'validate':
				return JSON.stringify({
					result: 'Valid JSON',
					valid: true,
					type: Array.isArray(parsed) ? 'array' : typeof parsed,
					size: Array.isArray(parsed) ? parsed.length : Object.keys(parsed).length,
				});

			case 'minify':
				return JSON.stringify({
					result: JSON.stringify(parsed),
				});

			case 'query':
				if (!path) return JSON.stringify({error: 'No path provided for query action'});
				const value = getByPath(parsed, path);
				return JSON.stringify({
					result: value !== undefined ? JSON.stringify(value, null, 2) : `No value found at path "${path}"`,
					path: path,
					value: value,
				});

			case 'format':
			default:
				return JSON.stringify({
					result: JSON.stringify(parsed, null, 2),
				});
		}
	} catch (e) {
		console.error(e);
		return JSON.stringify({error: `JSON processing failed: ${e.message}`});
	}
};
