-- Advertisements management for customer-app carousel
-- Controlled via dealer dashboard ads management page

CREATE TABLE IF NOT EXISTS ads (
  id            TEXT PRIMARY KEY,
  title         TEXT NOT NULL,
  description   TEXT NOT NULL DEFAULT '',
  image_url     TEXT,
  link_url      TEXT,
  is_active     INTEGER NOT NULL DEFAULT 1,
  sort_order    INTEGER NOT NULL DEFAULT 0,
  created_at    INTEGER NOT NULL DEFAULT (unixepoch()),
  updated_at    INTEGER NOT NULL DEFAULT (unixepoch())
);

CREATE INDEX IF NOT EXISTS idx_ads_active_sort ON ads(is_active, sort_order);
