<script lang="ts">
  export let variant: 'default' | 'active' | 'warning' | 'locked' = 'default';
  export let size: 'sm' | 'md' = 'md';

  const variantStyles: Record<string, string> = {
    active: 'border-emerald-300/30 bg-emerald-300/10 text-emerald',
    warning: 'border-amber-200/30 bg-amber-200/10 text-amber',
    locked: 'border-crimson-200/30 bg-crimson-200/10 text-crimson',
    default: 'border-edge bg-surface-100/60 text-ink-secondary',
  };

  const dotStyles: Record<string, string> = {
    active: 'bg-emerald',
    warning: 'bg-amber',
    locked: 'bg-crimson',
    default: 'bg-ink-muted',
  };
</script>

<span
  class="inline-flex items-center gap-1.5 rounded-full border ring-0 font-medium
         {variantStyles[variant]}
         {size === 'sm' ? 'px-2 py-0.5 text-2xs' : 'px-2.5 py-1 text-xs'}"
>
  <slot />
</span>