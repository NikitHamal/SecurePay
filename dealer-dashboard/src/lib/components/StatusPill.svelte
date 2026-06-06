<script lang="ts">
  import type { DeviceStatus, PaymentStatus } from '$lib/types';

  export let status: DeviceStatus | PaymentStatus | 'available' | 'assigned' | 'settled' | 'pending';

  $: label = status.replace('_', ' ');
  $: tone =
    status === 'active' || status === 'current' || status === 'settled' || status === 'available'
      ? 'bg-secure-emerald/15 text-secure-emerald ring-secure-emerald/30'
      : status === 'warning' || status === 'due_soon' || status === 'pending'
        ? 'bg-secure-amber/15 text-secure-amber ring-secure-amber/30'
        : 'bg-secure-crimson/15 text-secure-crimson ring-secure-crimson/30';
</script>

<span class={`inline-flex items-center rounded-m3 px-2.5 py-1 text-xs font-semibold capitalize ring-1 ${tone}`}>
  {label}
</span>
