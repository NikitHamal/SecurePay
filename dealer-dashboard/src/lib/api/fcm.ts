function pemToBinary(pem: string): ArrayBuffer {
  const b64 = pem
    .replace(/-----BEGIN [\w\s]+-----/g, '')
    .replace(/-----END [\w\s]+-----/g, '')
    .replace(/\s/g, '');
  const binaryString = atob(b64);
  const bytes = new Uint8Array(binaryString.length);
  for (let i = 0; i < binaryString.length; i++) bytes[i] = binaryString.charCodeAt(i);
  return bytes.buffer as ArrayBuffer;
}

function base64UrlEncode(data: ArrayBuffer | Uint8Array): string {
  const bytes = data instanceof Uint8Array ? data : new Uint8Array(data);
  return btoa(String.fromCharCode(...bytes))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
}

async function signJwt(payload: string, privateKeyPem: string): Promise<string> {
  const header = { alg: 'RS256', typ: 'JWT' };
  const headerB64 = base64UrlEncode(new TextEncoder().encode(JSON.stringify(header)));
  const payloadB64 = base64UrlEncode(new TextEncoder().encode(payload));
  const signingInput = `${headerB64}.${payloadB64}`;

  const key = await crypto.subtle.importKey(
    'pkcs8',
    pemToBinary(privateKeyPem),
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['sign']
  );
  const signature = await crypto.subtle.sign(
    'RSASSA-PKCS1-v1_5',
    key,
    new TextEncoder().encode(signingInput)
  );
  return `${signingInput}.${base64UrlEncode(signature)}`;
}

async function getAccessToken(serviceAccountEmail: string, privateKeyPem: string): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const assertion = await signJwt(JSON.stringify({
    iss: serviceAccountEmail,
    scope: 'https://www.googleapis.com/auth/firebase.messaging',
    aud: 'https://oauth2.googleapis.com/token',
    exp: now + 3600,
    iat: now
  }), privateKeyPem);

  const resp = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion
    })
  });
  if (!resp.ok) throw new Error(`FCM OAuth2 failed: ${resp.status} ${(await resp.text()).slice(0, 500)}`);
  const data = await resp.json() as { access_token?: string };
  if (!data.access_token) throw new Error('FCM OAuth2 response did not include an access token');
  return data.access_token;
}

export interface FcmDataMessage {
  type: 'lock' | 'unlock' | 'sync' | 'stolen' | 'update' | 'notification';
  accountId?: string;
  isStolen?: string;
  versionCode?: string;
  versionName?: string;
  title?: string;
  body?: string;
}

interface FcmEnv {
  FCM_SERVICE_ACCOUNT_EMAIL?: string;
  FCM_SERVICE_ACCOUNT_PRIVATE_KEY?: string;
  FCM_PROJECT_ID?: string;
}

function configured(env: FcmEnv): env is Required<FcmEnv> {
  return Boolean(
    env.FCM_SERVICE_ACCOUNT_EMAIL &&
    env.FCM_SERVICE_ACCOUNT_PRIVATE_KEY &&
    env.FCM_PROJECT_ID
  );
}

async function sendMessage(target: { token: string } | { topic: string }, message: FcmDataMessage, env: FcmEnv): Promise<boolean> {
  if (!configured(env)) return false;
  const accessToken = await getAccessToken(env.FCM_SERVICE_ACCOUNT_EMAIL, env.FCM_SERVICE_ACCOUNT_PRIVATE_KEY);
  const data = Object.fromEntries(
    Object.entries(message).filter(([, value]) => typeof value === 'string' && value.length > 0)
  ) as Record<string, string>;

  const resp = await fetch(`https://fcm.googleapis.com/v1/projects/${env.FCM_PROJECT_ID}/messages:send`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ message: { ...target, data } })
  });
  if (!resp.ok) {
    console.error(`FCM send failed: ${resp.status} ${(await resp.text()).slice(0, 500)}`);
    return false;
  }
  return true;
}

export async function sendFcm(fcmToken: string, message: FcmDataMessage, env: FcmEnv): Promise<void> {
  await sendMessage({ token: fcmToken }, message, env);
}

export async function sendFcmTopic(topic: string, message: FcmDataMessage, env: FcmEnv): Promise<boolean> {
  if (!/^[a-zA-Z0-9-_.~%]{1,900}$/.test(topic)) throw new Error('Invalid FCM topic');
  return sendMessage({ topic }, message, env);
}
