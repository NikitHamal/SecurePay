#!/usr/bin/env node
import bcrypt from 'bcryptjs';
import { randomBytes } from 'node:crypto';

function parseArgs(argv) {
  const out = {};
  for (let i = 0; i < argv.length; i += 1) {
    if (!argv[i].startsWith('--')) continue;
    const key = argv[i].slice(2);
    const value = argv[i + 1];
    if (!value || value.startsWith('--')) out[key] = 'true';
    else { out[key] = value; i += 1; }
  }
  return out;
}

const args = parseArgs(process.argv.slice(2));
const role = String(args.role ?? '').trim().toUpperCase();
const name = String(args.name ?? '').trim();
const email = String(args.email ?? '').trim().toLowerCase();
const phone = String(args.phone ?? '').trim();
const password = String(args.password ?? '');
const agencyId = String(args.agency ?? '').trim();
const branchId = String(args.branch ?? '').trim();
const approvedBy = String(args['approved-by'] ?? '').trim();
const allowed = new Set(['AGENCY_OWNER', 'BRANCH_ADMIN', 'AGENT']);

if (!allowed.has(role) || !name || !email || !password || !approvedBy) {
  console.error('Usage: node scripts/create-staff.mjs --role AGENCY_OWNER|BRANCH_ADMIN|AGENT --name NAME --email EMAIL --password PASSWORD --approved-by SUPER_ADMIN_ID [--agency AGY-ID] [--branch BR-ID] [--phone PHONE]');
  process.exit(1);
}
if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
  console.error('Email is invalid.');
  process.exit(1);
}
if (password.length < 14 || password.length > 128) {
  console.error('Password must be 14-128 characters.');
  process.exit(1);
}
if (role === 'AGENCY_OWNER' && !agencyId) {
  console.error('AGENCY_OWNER requires --agency.');
  process.exit(1);
}
if ((role === 'BRANCH_ADMIN' || role === 'AGENT') && (!agencyId || !branchId)) {
  console.error(`${role} requires both --agency and --branch.`);
  process.exit(1);
}

const q = (value) => `'${String(value).replaceAll("'", "''")}'`;
const id = `DLR-${randomBytes(6).toString('hex').toUpperCase()}`;
const hash = bcrypt.hashSync(password, 12);
const now = Math.floor(Date.now() / 1000);
const agencySql = agencyId ? q(agencyId) : 'NULL';
const branchSql = branchId ? q(branchId) : 'NULL';

let sql = `-- Review IDs and execute in one D1 session after the agency/branch exists.\nINSERT INTO dealers (id, name, email, phone, password, role, agency_id, branch_id, is_approved, approved_by, approved_at, created_at)\nVALUES (${q(id)}, ${q(name)}, ${q(email)}, ${phone ? q(phone) : 'NULL'}, ${q(hash)}, ${q(role)}, ${agencySql}, ${branchSql}, 1, ${q(approvedBy)}, ${now}, ${now});\n`;
if (role === 'AGENCY_OWNER') {
  sql += `UPDATE agencies SET owner_id = ${q(id)} WHERE id = ${q(agencyId)};\n`;
}
if (role === 'BRANCH_ADMIN') {
  sql += `UPDATE branches SET admin_id = ${q(id)} WHERE id = ${q(branchId)} AND agency_id = ${q(agencyId)};\n`;
}
process.stdout.write(sql);
