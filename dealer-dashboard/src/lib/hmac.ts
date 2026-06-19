const HMAC_MAX_AGE_MS = 5 * 60 * 1000;

function arrayBufferToHex(buffer: ArrayBuffer): string {
	return Array.from(new Uint8Array(buffer)).map((b) => b.toString(16).padStart(2, '0')).join('');
}

export async function hmacSha256(key: string, data: string): Promise<string> {
	const encoder = new TextEncoder();
	const cryptoKey = await crypto.subtle.importKey(
		'raw',
		encoder.encode(key),
		{ name: 'HMAC', hash: 'SHA-256' },
		false,
		['sign']
	);
	const signature = await crypto.subtle.sign('HMAC', cryptoKey, encoder.encode(data));
	return arrayBufferToHex(signature);
}

export function randomHex(byteLength = 32): string {
	const bytes = new Uint8Array(byteLength);
	crypto.getRandomValues(bytes);
	return Array.from(bytes).map((b) => b.toString(16).padStart(2, '0')).join('');
}

export async function verifyHmacSignature(params: {
	signature: string;
	timestamp: string;
	nonce: string;
	method: string;
	path: string;
	body: string;
	secret: string;
}): Promise<boolean> {
	const { signature, timestamp, nonce, method, path, body, secret } = params;

	const timestampNum = Number(timestamp);
	if (isNaN(timestampNum)) return false;

	const age = Date.now() - timestampNum;
	if (age > HMAC_MAX_AGE_MS || age < -HMAC_MAX_AGE_MS) return false;

	const bodyHash = body ? await hmacSha256(secret, body) : '';
	const stringToSign = `${method.toUpperCase()}\n${path}\n${timestamp}\n${nonce}\n${bodyHash}`;
	const expected = await hmacSha256(secret, stringToSign);

	if (signature.length !== expected.length) return false;
	let difference = 0;
	for (let i = 0; i < signature.length; i++) {
		difference |= signature.charCodeAt(i) ^ expected.charCodeAt(i);
	}
	return difference === 0;
}

export function getHmacSecret(event: { platform?: App.Platform | null }): string {
	if (!event.platform?.env?.HMAC_SECRET) {
		throw new Error('HMAC_SECRET not configured');
	}
	return event.platform.env.HMAC_SECRET;
}
