#!/usr/bin/env node
import bcrypt from 'bcryptjs';
import { randomBytes } from 'node:crypto';

function argsToObject(argv) {
  const out = {};
  for (let i = 0; i < argv.length; i += 1) {
    const key = argv[i];
    if (!key.startsWith('--')) continue;
    const value = argv[i + 1];
    if (!value || value.startsWith('--')) {
      out[key.slice(2)] = 'true';
    } else {
      out[key.slice(2)] = value;
      i += 1;
    }
  }
  return out;
}

const args = argsToObject(process.argv.slice(2));
const name = String(args.name ?? process.env.TB_ADMIN_NAME ?? '').trim();
const email = String(args.email ?? process.env.TB_ADMIN_EMAIL ?? '').trim().toLowerCase();
const phone = String(args.phone ?? process.env.TB_ADMIN_PHONE ?? '').trim();
const password = String(args.password ?? process.env.TB_ADMIN_PASSWORD ?? '');

if (!name || !email || !password) {
  console.error('Usage: node scripts/create-super-admin.mjs --name NAME --email EMAIL --password PASSWORD [--phone PHONE]');
  console.error('Environment alternatives: TB_ADMIN_NAME, TB_ADMIN_EMAIL, TB_ADMIN_PASSWORD, TB_ADMIN_PHONE.');
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

const q = (value) => `'${String(value).replaceAll("'", "''")}'`;
const id = `DLR-${randomBytes(6).toString('hex').toUpperCase()}`;
const hash = bcrypt.hashSync(password, 12);
const now = Math.floor(Date.now() / 1000);

process.stdout.write(`-- Review before executing against D1.\nINSERT INTO dealers (id, name, email, phone, password, role, agency_id, branch_id, is_approved, approved_at, created_at)\nVALUES (${q(id)}, ${q(name)}, ${q(email)}, ${phone ? q(phone) : 'NULL'}, ${q(hash)}, 'SUPER_ADMIN', NULL, NULL, 1, ${now}, ${now});\n`);
