<script lang="ts">
  export let variant: 'default' | 'emerald' | 'crimson' | 'amber' | 'sky' | 'violet' = 'default';
  export let size: 'sm' | 'md' | 'lg' = 'md';

  const variantStyles: Record<string, string> = {
    default: 'border-edge bg-surface-100/60 text-ink-secondary',
    emerald: 'border-emerald-300/30 bg-emerald-300/10 text-emerald',
    crimson: 'border-crimson-200/30 bg-crimson-200/10 text-crimson',
    amber: 'border-amber-200/30 bg-amber-200/10 text-amber',
    sky: 'border-sky/30 bg-sky/10 text-sky',
    violet: 'border-violet/30 bg-violet/10 text-violet',
  };

  const sizeStyles: Record<string, string> = {
    sm: 'px-2 py-0.5 text-2xs',
    md: 'px-2.5 py-1 text-xs',
    lg: 'px-3 py-1.5 text-sm',
  };
</script>

<span
  class="inline-flex items-center gap-1.5 rounded-full border
         {variantStyles[variant]} {sizeStyles[size]}"
>
  <slot />
</span>