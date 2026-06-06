<script lang="ts">
  import { onMount } from 'svelte';
  import type { CustomerNode } from '$lib/types';
  import { formatKES, formatCountdown } from '$lib/types';
  import { extendTimer, forceRemoteLock } from '$lib/stores/nodes';
  import StatusBadge from '$lib/components/StatusBadge.svelte';

  export let rows: CustomerNode[] = [];
  /** Number of hours an "Extend Timer" action grants. */
  export let extendHours: number = 24;

  // Local clock that re-renders every second for the live countdown column.
  let now = Date.now();

  onMount(() => {
    const interval = setInterval(() => {
      now = Date.now();
    }, 1000);
    return () => clearInterval(interval);
  });

  // Track in-flight actions per row to disable buttons while pending.
  let pending: Record<string, boolean> = {};

  async function handleExtend(customerId: string): Promise<void> {
    pending = { ...pending, [customerId]: true };
    await extendTimer(customerId, extendHours);
    pending = { ...pending, [customerId]: false };
  }

  async function handleLock(customerId: string): Promise<void> {
    pending = { ...pending, [customerId]: true };
    await forceRemoteLock(customerId);
    pending = { ...pending, [customerId]: false };
  }

  function countdownClass(node: CustomerNode): string {
    const remaining = node.nextDueEpochMillis - now;
    if (remaining <= 0) return 'text-brand-crimson';
    if (remaining <= 86_400_000) return 'text-brand-amber';
    return 'text-brand-emerald';
  }
</script>

<div class="m3-card overflow-hidden">
  <div class="overflow-x-auto">
    <table class="w-full min-w-[820px] border-collapse text-left text-sm">
      <thead>
        <tr class="border-b border-brand-border text-xs uppercase tracking-wider text-brand-textMuted">
          <th class="px-5 py-3 font-medium">Customer</th>
          <th class="px-5 py-3 font-medium">Device</th>
          <th class="px-5 py-3 font-medium">IMEI</th>
          <th class="px-5 py-3 text-right font-medium">Remaining</th>
          <th class="px-5 py-3 font-medium">Next Due In</th>
          <th class="px-5 py-3 font-medium">Status</th>
          <th class="px-5 py-3 text-right font-medium">Actions</th>
        </tr>
      </thead>
      <tbody>
        {#if rows.length === 0}
          <tr>
            <td colspan="7" class="px-5 py-10 text-center text-brand-textMuted">
              No financed devices to display.
            </td>
          </tr>
        {:else}
          {#each rows as node (node.customerId)}
            <tr class="border-b border-brand-border/60 transition-colors hover:bg-brand-surfaceVariant/40">
              <td class="px-5 py-3.5">
                <div class="font-medium text-brand-text">{node.customerName}</div>
                <div class="text-xs text-brand-textMuted">{node.customerId}</div>
              </td>
              <td class="px-5 py-3.5 text-brand-text">{node.deviceModel}</td>
              <td class="px-5 py-3.5 font-mono text-xs text-brand-textMuted">{node.imei}</td>
              <td class="px-5 py-3.5 text-right tabular-nums text-brand-text">
                {formatKES(node.remainingBalance)}
              </td>
              <td class="px-5 py-3.5 font-mono tabular-nums {countdownClass(node)}">
                {formatCountdown(node.nextDueEpochMillis - now)}
              </td>
              <td class="px-5 py-3.5">
                <StatusBadge status={node.status} />
              </td>
              <td class="px-5 py-3.5">
                <div class="flex justify-end gap-2">
                  <button
                    type="button"
                    class="rounded-lg border border-brand-emerald/40 bg-brand-emerald/10 px-3 py-1.5 text-xs font-medium text-brand-emerald transition-colors hover:bg-brand-emerald/20 disabled:cursor-not-allowed disabled:opacity-50"
                    disabled={pending[node.customerId]}
                    on:click={() => handleExtend(node.customerId)}
                  >
                    Extend Timer
                  </button>
                  <button
                    type="button"
                    class="rounded-lg border border-brand-crimson/40 bg-brand-crimson/10 px-3 py-1.5 text-xs font-medium text-brand-crimson transition-colors hover:bg-brand-crimson/20 disabled:cursor-not-allowed disabled:opacity-50"
                    disabled={pending[node.customerId] || node.status === 'LOCKED'}
                    on:click={() => handleLock(node.customerId)}
                  >
                    Force Remote Lock
                  </button>
                </div>
              </td>
            </tr>
          {/each}
        {/if}
      </tbody>
    </table>
  </div>
</div>
