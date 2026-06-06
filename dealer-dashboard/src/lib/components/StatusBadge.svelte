<script lang="ts">
  import type { DeviceStatus } from '$lib/types';

  export let status: DeviceStatus;

  const styles: Record<DeviceStatus, string> = {
    ACTIVE: 'bg-brand-emerald/15 text-brand-emerald ring-1 ring-inset ring-brand-emerald/30',
    WARNING: 'bg-brand-amber/15 text-brand-amber ring-1 ring-inset ring-brand-amber/30',
    LOCKED: 'bg-brand-crimson/15 text-brand-crimson ring-1 ring-inset ring-brand-crimson/30'
  };

  const dotStyles: Record<DeviceStatus, string> = {
    ACTIVE: 'bg-brand-emerald',
    WARNING: 'bg-brand-amber',
    LOCKED: 'bg-brand-crimson'
  };
</script>

<span
  class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold tracking-wide {styles[
    status
  ]}"
>
  <span class="h-1.5 w-1.5 rounded-full {dotStyles[status]}"></span>
  {status}
</span>
