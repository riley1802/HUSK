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

window['ai_edge_gallery_get_result'] = async (data) => {
	try {
		const jsonData = JSON.parse(data);
		const pattern = jsonData['pattern'];
		const text = jsonData['text'];
		const flags = jsonData['flags'] || 'g';

		if (!pattern) return JSON.stringify({error: 'No regex pattern provided'});
		if (!text) return JSON.stringify({error: 'No text provided'});

		const regex = new RegExp(pattern, flags);
		const matches = [];
		let match;

		if (flags.includes('g')) {
			while ((match = regex.exec(text)) !== null) {
				matches.push({
					match: match[0],
					index: match.index,
					groups: match.slice(1),
				});
				if (matches.length > 100) break;
			}
		} else {
			match = regex.exec(text);
			if (match) {
				matches.push({
					match: match[0],
					index: match.index,
					groups: match.slice(1),
				});
			}
		}

		const fullMatch = regex.test(text);

		return JSON.stringify({
			result: matches.length > 0
				? `Found ${matches.length} match(es): ${matches.map(m => '"' + m.match + '"').join(', ')}`
				: 'No matches found',
			matches: matches,
			match_count: matches.length,
			full_match: fullMatch,
			pattern: pattern,
			flags: flags,
		});
	} catch (e) {
		console.error(e);
		return JSON.stringify({error: `Regex error: ${e.message}`});
	}
};
