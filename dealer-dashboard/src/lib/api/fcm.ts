function pemToBinary(pem: string): ArrayBuffer {
  const b64 = pem
    .replace(/-----BEGIN [\w\s]+-----/g, '')
    .replace(/-----END [\w\s]+-----/g, '')
    .replace(/\s/g, '');
  const binaryString = atob(b64);
  const bytes = new Uint8Array(binaryString.length);
  for (let i = 0; i < binaryString.length; i++) {
    bytes[i] = binaryString.charCodeAt(i);
  }
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

  const signature = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, new TextEncoder().encode(signingInput));
  const sigB64 = base64UrlEncode(signature);

  return `${signingInput}.${sigB64}`;
}

async function getAccessToken(
  serviceAccountEmail: string,
  privateKeyPem: string
): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const jwtPayload = JSON.stringify({
    iss: serviceAccountEmail,
    scope: 'https://www.googleapis.com/auth/firebase.messaging',
    aud: 'https://oauth2.googleapis.com/token',
    exp: now + 3600,
    iat: now
  });

  const assertion = await signJwt(jwtPayload, privateKeyPem);

  const resp = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion
    })
  });

  if (!resp.ok) {
    const text = await resp.text();
    throw new Error(`FCM OAuth2 failed: ${resp.status} ${text}`);
  }

  const data = (await resp.json()) as { access_token: string };
  return data.access_token;
}

export interface FcmDataMessage {
  type: 'lock' | 'unlock' | 'sync' | 'stolen';
  accountId: string;
  isStolen?: string;
}

export async function sendFcm(
  fcmToken: string,
  message: FcmDataMessage,
  env: {
    FCM_SERVICE_ACCOUNT_EMAIL?: string;
    FCM_SERVICE_ACCOUNT_PRIVATE_KEY?: string;
    FCM_PROJECT_ID?: string;
  }
): Promise<void> {
  const { FCM_SERVICE_ACCOUNT_EMAIL, FCM_SERVICE_ACCOUNT_PRIVATE_KEY, FCM_PROJECT_ID } = env;

  if (!FCM_SERVICE_ACCOUNT_EMAIL || !FCM_SERVICE_ACCOUNT_PRIVATE_KEY || !FCM_PROJECT_ID) {
    return;
  }

  const accessToken = await getAccessToken(
    FCM_SERVICE_ACCOUNT_EMAIL,
    FCM_SERVICE_ACCOUNT_PRIVATE_KEY
  );

  const resp = await fetch(
    `https://fcm.googleapis.com/v1/projects/${FCM_PROJECT_ID}/messages:send`,
    {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        message: {
          token: fcmToken,
          data: {
            type: message.type,
            accountId: message.accountId
          }
        }
      })
    }
  );

  if (!resp.ok) {
    const text = await resp.text();
    console.error(`FCM send failed: ${resp.status} ${text}`);
  }
}
