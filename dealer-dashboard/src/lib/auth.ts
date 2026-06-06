import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import { JWT_SECRET } from '$env/static/private';

const SESSION_DURATION_HOURS = 24;

export function hashPassword(password: string): string {
  return bcrypt.hashSync(password, 10);
}

export function verifyPassword(password: string, hash: string): boolean {
  return bcrypt.compareSync(password, hash);
}

export function createToken(dealerId: string, dealerName: string): string {
  return jwt.sign({ sub: dealerId, name: dealerName }, JWT_SECRET, {
    expiresIn: `${SESSION_DURATION_HOURS}h`
  });
}

export function verifyToken(token: string): { sub: string; name: string } | null {
  try {
    const payload = jwt.verify(token, JWT_SECRET);
    return payload as { sub: string; name: string };
  } catch {
    return null;
  }
}

export interface AuthenticatedDealer {
  id: string;
  name: string;
}