<script lang="ts">
  import type { Status } from '$lib/types';

  export let status: Status;

  const styles: Record<Status, string> = {
    ACTIVE: 'bg-emerald/15 text-emerald ring-emerald/30',
    WARNING: 'bg-amber/15 text-amber ring-amber/30',
    LOCKED: 'bg-crimson/15 text-crimson ring-crimson/30'
  };

  const dot: Record<Status, string> = {
    ACTIVE: 'bg-emerald',
    WARNING: 'bg-amber',
    LOCKED: 'bg-crimson'
  };
</script>

<span
  class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium ring-1 ring-inset {styles[
    status
  ]}"
>
  <span class="h-1.5 w-1.5 rounded-full {dot[status]}" aria-hidden="true"></span>
  {status}
</span>
