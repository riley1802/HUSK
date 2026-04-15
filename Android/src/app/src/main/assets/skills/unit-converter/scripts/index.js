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

// Conversion factors to a base unit per category.
const conversions = {
	// Temperature (special — uses functions, not factors).
	temperature: {
		celsius: { toBase: (v) => v, fromBase: (v) => v },
		fahrenheit: { toBase: (v) => (v - 32) * 5 / 9, fromBase: (v) => v * 9 / 5 + 32 },
		kelvin: { toBase: (v) => v - 273.15, fromBase: (v) => v + 273.15 },
	},
	// Length → meters.
	length: {
		meters: 1, m: 1, meter: 1,
		kilometers: 1000, km: 1000,
		centimeters: 0.01, cm: 0.01,
		millimeters: 0.001, mm: 0.001,
		miles: 1609.344, mile: 1609.344,
		yards: 0.9144, yard: 0.9144, yd: 0.9144,
		feet: 0.3048, foot: 0.3048, ft: 0.3048,
		inches: 0.0254, inch: 0.0254, in: 0.0254,
	},
	// Weight → grams.
	weight: {
		grams: 1, g: 1, gram: 1,
		kilograms: 1000, kg: 1000,
		milligrams: 0.001, mg: 0.001,
		pounds: 453.592, lbs: 453.592, lb: 453.592,
		ounces: 28.3495, oz: 28.3495,
		tons: 907185, ton: 907185,
		tonnes: 1000000, tonne: 1000000,
	},
	// Volume → liters.
	volume: {
		liters: 1, l: 1, liter: 1,
		milliliters: 0.001, ml: 0.001,
		gallons: 3.78541, gallon: 3.78541, gal: 3.78541,
		quarts: 0.946353, quart: 0.946353, qt: 0.946353,
		cups: 0.236588, cup: 0.236588,
		tablespoons: 0.0147868, tbsp: 0.0147868,
		teaspoons: 0.00492892, tsp: 0.00492892,
		floz: 0.0295735,
	},
	// Speed → m/s.
	speed: {
		'm/s': 1, mps: 1,
		'km/h': 0.277778, kmh: 0.277778, kph: 0.277778,
		mph: 0.44704,
		knots: 0.514444, knot: 0.514444,
	},
	// Data → bytes.
	data: {
		bytes: 1, b: 1, byte: 1,
		kb: 1024, kilobytes: 1024,
		mb: 1048576, megabytes: 1048576,
		gb: 1073741824, gigabytes: 1073741824,
		tb: 1099511627776, terabytes: 1099511627776,
	},
	// Time → seconds.
	time: {
		seconds: 1, s: 1, second: 1, sec: 1,
		minutes: 60, minute: 60, min: 60,
		hours: 3600, hour: 3600, hr: 3600,
		days: 86400, day: 86400,
		weeks: 604800, week: 604800,
		months: 2629746, month: 2629746,
		years: 31556952, year: 31556952,
	},
};

function normalize(unit) {
	return unit.toLowerCase().trim().replace(/[°\s]/g, '');
}

function findCategory(unit) {
	const n = normalize(unit);
	for (const [cat, units] of Object.entries(conversions)) {
		if (cat === 'temperature') {
			if (n in units) return cat;
		} else {
			if (n in units) return cat;
		}
	}
	return null;
}

function convert(value, from, to) {
	const nFrom = normalize(from);
	const nTo = normalize(to);
	const cat = findCategory(nFrom);
	if (!cat) throw new Error(`Unknown unit: ${from}`);
	if (findCategory(nTo) !== cat) throw new Error(`Cannot convert between ${from} and ${to} (different categories)`);

	if (cat === 'temperature') {
		const base = conversions.temperature[nFrom].toBase(value);
		return conversions.temperature[nTo].fromBase(base);
	}

	const baseValue = value * conversions[cat][nFrom];
	return baseValue / conversions[cat][nTo];
}

window['ai_edge_gallery_get_result'] = async (data) => {
	try {
		const jsonData = JSON.parse(data);
		const value = parseFloat(jsonData['value']);
		const from = jsonData['from'];
		const to = jsonData['to'];

		if (isNaN(value)) return JSON.stringify({error: 'Invalid numeric value'});
		if (!from || !to) return JSON.stringify({error: 'Both "from" and "to" units are required'});

		const result = convert(value, from, to);
		const formatted = Number.isInteger(result) ? result.toString() : result.toPrecision(8).replace(/\.?0+$/, '');

		return JSON.stringify({
			result: `${value} ${from} = ${formatted} ${to}`,
			value: formatted,
		});
	} catch (e) {
		console.error(e);
		return JSON.stringify({error: `Conversion failed: ${e.message}`});
	}
};
