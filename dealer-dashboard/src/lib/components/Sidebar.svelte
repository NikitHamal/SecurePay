<script lang="ts">
  import { BarChart3, Boxes, CreditCard, Users } from '@lucide/svelte';
  import type { Component } from 'svelte';
  import type { SectionKey } from '$lib/types';

  export let active: SectionKey;
  export let onNavigate: (section: SectionKey) => void;

  const items: Array<{ key: SectionKey; label: string; icon: Component<any> }> = [
    { key: 'overview', label: 'Overview', icon: BarChart3 },
    { key: 'inventory', label: 'Inventory', icon: Boxes },
    { key: 'customers', label: 'Customers', icon: Users },
    { key: 'ledger', label: 'Payment Ledger', icon: CreditCard }
  ];
</script>

<aside class="flex h-full w-full flex-col border-r border-white/10 bg-charcoal-900 px-4 py-5 lg:w-72">
  <div class="mb-8">
    <p class="text-xs font-semibold uppercase tracking-[0.18em] text-secure-emerald">SecurePay</p>
    <h1 class="mt-2 text-xl font-semibold text-slate-50">Dealer Console</h1>
  </div>

  <nav class="space-y-2">
    {#each items as item}
      <button
        type="button"
        class={`flex w-full items-center gap-3 rounded-m3 px-3 py-2.5 text-left text-sm font-medium transition ${
          active === item.key
            ? 'bg-secure-emerald text-charcoal-950'
            : 'text-slate-300 hover:bg-white/[0.08] hover:text-white'
        }`}
        on:click={() => onNavigate(item.key)}
      >
        <svelte:component this={item.icon} size={18} strokeWidth={2.2} />
        <span>{item.label}</span>
      </button>
    {/each}
  </nav>

  <div class="mt-auto rounded-m3 border border-white/10 bg-charcoal-850 p-3">
    <p class="text-sm font-semibold text-slate-100">Command Channel</p>
    <p class="mt-1 text-xs leading-5 text-slate-400">Mock remote actions are wired to local reactive state.</p>
  </div>
</aside>
