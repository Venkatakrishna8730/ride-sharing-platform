const crypto = require('crypto');

/**
 * Validates HMAC SHA-256 JWT tokens natively without external dependencies.
 */
function base64UrlDecode(str) {
  let base64 = str.replace(/-/g, '+').replace(/_/g, '/');
  while (base64.length % 4) {
    base64 += '=';
  }
  return Buffer.from(base64, 'base64').toString('utf8');
}

function verifyJwt(token, secret) {
  if (!token || typeof token !== 'string') {
    throw new Error('Token is missing or not a string');
  }

  const parts = token.split('.');
  if (parts.length !== 3) {
    throw new Error('Malformed JWT: must contain 3 segments');
  }

  const [headerB64, payloadB64, signatureB64] = parts;

  // Parse header to determine algorithm
  let header;
  try {
    header = JSON.parse(base64UrlDecode(headerB64));
  } catch (e) {
    throw new Error('Invalid JWT header');
  }

  const algMap = {
    'HS256': 'sha256',
    'HS384': 'sha384',
    'HS512': 'sha512'
  };

  const hashAlg = algMap[header.alg];
  if (!hashAlg) {
    throw new Error(`Unsupported JWT algorithm: ${header.alg}`);
  }

  // Verify signature
  const hmac = crypto.createHmac(hashAlg, secret);
  hmac.update(`${headerB64}.${payloadB64}`);
  const expectedSignature = hmac.digest('base64url');

  if (signatureB64 !== expectedSignature) {
    throw new Error('Invalid JWT signature');
  }

  // Parse payload
  const payloadJson = base64UrlDecode(payloadB64);
  const payload = JSON.parse(payloadJson);

  // Check expiration
  const nowInSeconds = Math.floor(Date.now() / 1000);
  if (payload.exp && payload.exp < nowInSeconds) {
    throw new Error('JWT token has expired');
  }

  return payload;
}

module.exports = {
  verifyJwt
};
