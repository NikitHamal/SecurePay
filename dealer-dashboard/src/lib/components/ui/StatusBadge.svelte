<script lang="ts">
  import type { Status } from '$lib/types';

  export let status: Status;
  export let size: 'sm' | 'md' = 'md';

  const variantMap: Record<Status, 'active' | 'warning' | 'locked'> = {
    ACTIVE: 'active',
    WARNING: 'warning',
    LOCKED: 'locked',
    STOLEN: 'locked',
  };

  const dotMap: Record<Status, string> = {
    ACTIVE: 'bg-emerald',
    WARNING: 'bg-amber',
    LOCKED: 'bg-crimson',
    STOLEN: 'bg-crimson',
  };

  const labelMap: Record<Status, string> = {
    ACTIVE: 'Active',
    WARNING: 'Warning',
    LOCKED: 'Locked',
    STOLEN: 'Stolen',
  };
</script>

<span
  class="inline-flex items-center gap-1.5 rounded-full border ring-0 font-medium
         {status === 'ACTIVE' ? 'border-emerald-300/30 bg-emerald-300/10 text-emerald' : status === 'WARNING' ? 'border-amber-200/30 bg-amber-200/10 text-amber' : 'border-crimson-200/30 bg-crimson-200/10 text-crimson'}
         {size === 'sm' ? 'px-2 py-0.5 text-2xs' : 'px-2.5 py-1 text-xs'}"
>
  <span class="relative flex h-1.5 w-1.5">
    <span class="absolute inline-flex h-full w-full animate-ping rounded-full opacity-60 {dotMap[status]}"></span>
    <span class="relative inline-flex h-1.5 w-1.5 rounded-full {dotMap[status]}"></span>
  </span>
  {labelMap[status]}
</span>