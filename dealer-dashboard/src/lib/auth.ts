import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';

const SESSION_DURATION_HOURS = 8;

export type DealerRole = 'SUPER_ADMIN' | 'AGENCY_OWNER' | 'BRANCH_ADMIN' | 'AGENT';

export function hashPassword(password: string): string {
  return bcrypt.hashSync(password, 12);
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
    expiresIn: `${SESSION_DURATION_HOURS}h`,
    issuer: 'touch-base-dashboard',
    audience: 'touch-base-dealers'
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
    const payload = jwt.verify(token, jwtSecret, {
      issuer: 'touch-base-dashboard',
      audience: 'touch-base-dealers'
    });
    if (!payload || typeof payload === 'string') return null;

    const roles: DealerRole[] = ['SUPER_ADMIN', 'AGENCY_OWNER', 'BRANCH_ADMIN', 'AGENT'];
    if (
      typeof payload.sub !== 'string' || payload.sub.length === 0 ||
      typeof payload.name !== 'string' || payload.name.length === 0 ||
      typeof payload.role !== 'string' || !roles.includes(payload.role as DealerRole)
    ) {
      return null;
    }

    return {
      sub: payload.sub,
      name: payload.name,
      role: payload.role as DealerRole,
      agencyId: typeof payload.agencyId === 'string' ? payload.agencyId : null,
      branchId: typeof payload.branchId === 'string' ? payload.branchId : null
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

export interface SqlScope {
  where: string;
  params: string[];
}

/** Account visibility required by the client hierarchy. */
export function getAccountScopeFilter(
  dealer: AuthenticatedDealer,
  alias = 'a'
): SqlScope {
  switch (dealer.role) {
    case 'SUPER_ADMIN':
      return { where: '1=1', params: [] };
    case 'AGENCY_OWNER':
      return { where: `${alias}.agency_id = ?`, params: [dealer.agencyId || '__missing_agency__'] };
    case 'BRANCH_ADMIN':
      return { where: `${alias}.branch_id = ?`, params: [dealer.branchId || '__missing_branch__'] };
    case 'AGENT':
      return { where: `${alias}.enrolled_by = ?`, params: [dealer.id] };
    default:
      return { where: '1=0', params: [] };
  }
}

/** Dealer/agent visibility, used for inventory and agent-owned records. */
export function getDealerScopeFilter(
  dealer: AuthenticatedDealer,
  alias = 'owner'
): SqlScope {
  switch (dealer.role) {
    case 'SUPER_ADMIN':
      return { where: '1=1', params: [] };
    case 'AGENCY_OWNER':
      return { where: `${alias}.agency_id = ?`, params: [dealer.agencyId || '__missing_agency__'] };
    case 'BRANCH_ADMIN':
      return { where: `${alias}.branch_id = ?`, params: [dealer.branchId || '__missing_branch__'] };
    case 'AGENT':
      return { where: `${alias}.id = ?`, params: [dealer.id] };
    default:
      return { where: '1=0', params: [] };
  }
}

/** Backward-compatible alias used by existing account-list code. */
export function getScopeFilter(dealer: AuthenticatedDealer): SqlScope {
  return getAccountScopeFilter(dealer, 'a');
}

export function canAdministerAgents(role: DealerRole): boolean {
  return role === 'SUPER_ADMIN' || role === 'AGENCY_OWNER' || role === 'BRANCH_ADMIN';
}

export function canManageAccountFinancials(role: DealerRole): boolean {
  return role === 'SUPER_ADMIN' || role === 'AGENCY_OWNER' || role === 'BRANCH_ADMIN' || role === 'AGENT';
}

export function canReleaseOrDeleteAccount(role: DealerRole): boolean {
  return role === 'SUPER_ADMIN' || role === 'AGENCY_OWNER' || role === 'BRANCH_ADMIN';
}
