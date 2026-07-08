import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';

const SESSION_DURATION_HOURS = 8;

export type DealerRole = 'SUPER_ADMIN' | 'AGENCY_OWNER' | 'BRANCH_ADMIN' | 'AGENT';

export function hashPassword(password: string): string {
  return bcrypt.hashSync(password, 10);
}

export function verifyPassword(password: string, hash: string): boolean {
  return bcrypt.compareSync(password, hash);
}

export function createToken(dealer: {
  id: string;
  name: string;
  role: DealerRole;
  agencyId?: string | null;
  branchId?: string | null;
}, jwtSecret: string): string {
  return jwt.sign({
    sub: dealer.id,
    name: dealer.name,
    role: dealer.role,
    agencyId: dealer.agencyId,
    branchId: dealer.branchId
  }, jwtSecret, {
    expiresIn: `${SESSION_DURATION_HOURS}h`
  });
}

export function verifyToken(token: string, jwtSecret: string): {
  sub: string;
  name: string;
  role: DealerRole;
  agencyId?: string | null;
  branchId?: string | null;
} | null {
  try {
    const payload = jwt.verify(token, jwtSecret);
    return payload as {
      sub: string;
      name: string;
      role: DealerRole;
      agencyId?: string | null;
      branchId?: string | null;
    };
  } catch {
    return null;
  }
}

export interface AuthenticatedDealer {
  id: string;
  name: string;
  role: DealerRole;
  agencyId?: string | null;
  branchId?: string | null;
}

export function getScopeFilter(dealer: AuthenticatedDealer): { where: string; params: string[] } {
  switch (dealer.role) {
    case 'SUPER_ADMIN':
      return { where: '1=1', params: [] };
    case 'AGENCY_OWNER':
      return { where: 'a.agency_id = ?', params: [dealer.agencyId || ''] };
    case 'BRANCH_ADMIN':
      return { where: 'a.branch_id = ?', params: [dealer.branchId || ''] };
    case 'AGENT':
      return { where: 'a.enrolled_by = ?', params: [dealer.id] };
    default:
      return { where: '1=0', params: [] };
  }
}