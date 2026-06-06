# SecurePay — Dealer Web Dashboard

The admin web console dealers use to monitor financed smartphones and customer
payment status within the SecurePay device-financing ecosystem (M-KOPA-style).

Built with **SvelteKit + TypeScript + TailwindCSS 3** using a dark,
Material Design 3-inspired enterprise layout.

## Features

- **Overview** — KPI summary cards (active nodes, defaults, financial totals) plus a live interactive table.
- **Inventory (IMEI Matrix)** — every financed device keyed by IMEI with live status.
- **Customers** — customer-focused roster with live countdown timers.
- **Payment Ledger** — amounts paid / outstanding / daily installments with a totals row.
- **Live actions** — Extend Timer and Force Remote Lock per device, wired to an in-memory mock API.
- **Per-second countdowns** to each device's next due time.

## Getting started

```bash
npm install
npm run dev        # start the dev server (http://localhost:5173)
```

## Other scripts

```bash
npm run build      # production build
npm run preview    # preview the production build
npm run check      # type-check the project with svelte-check
```

## Project structure

```
src/
  app.css                  Tailwind entry + base styles
  app.html                 SvelteKit shell (dark theme)
  lib/
    types.ts               Shared domain model + helpers (evaluateStatus, formatKES, formatCountdown)
    data/mockApi.ts        In-memory mock data layer + KPI derivation
    stores/nodes.ts        Writable store + action wrappers
    components/            Sidebar, StatusBadge, KpiCard, LiveTable
  routes/
    +layout.svelte         Responsive shell (Sidebar + slot)
    +page.svelte           Overview
    inventory/+page.svelte IMEI Matrix
    customers/+page.svelte Customers
    ledger/+page.svelte    Payment Ledger
```

## Domain model

The `CustomerNode` type and `evaluateStatus` rule mirror the SecurePay
customer-app and agent-app so all three clients share one definition of device
status (`ACTIVE` / `WARNING` / `LOCKED`).
