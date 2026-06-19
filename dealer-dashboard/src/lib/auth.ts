import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';

const SESSION_DURATION_HOURS = 24 * 30;

export function hashPassword(password: string): string {
  return bcrypt.hashSync(password, 10);
}

export function verifyPassword(password: string, hash: string): boolean {
  return bcrypt.compareSync(password, hash);
}

export function createToken(dealerId: string, dealerName: string, jwtSecret: string): string {
  return jwt.sign({ sub: dealerId, name: dealerName }, jwtSecret, {
    expiresIn: `${SESSION_DURATION_HOURS}h`
  });
}

export function verifyToken(token: string, jwtSecret: string): { sub: string; name: string } | null {
  try {
    const payload = jwt.verify(token, jwtSecret);
    return payload as { sub: string; name: string };
  } catch {
    return null;
  }
}

export interface AuthenticatedDealer {
  id: string;
  name: string;
}