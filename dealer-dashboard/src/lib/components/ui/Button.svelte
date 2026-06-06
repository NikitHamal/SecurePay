<script lang="ts">
  export let variant: 'primary' | 'emerald' | 'crimson' | 'ghost' | 'outline' = 'outline';
  export let size: 'sm' | 'md' | 'lg' = 'md';
  export let disabled: boolean = false;
  export let full: boolean = false;

  const baseClass = 'inline-flex items-center justify-center gap-1.5 rounded-lg font-medium transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-offset-surface-200 disabled:cursor-not-allowed disabled:opacity-40';

  const sizeClasses: Record<string, string> = {
    sm: 'px-2.5 py-1 text-xs',
    md: 'px-3 py-1.5 text-sm',
    lg: 'px-4 py-2.5 text-sm',
  };

  const variantClasses: Record<string, string> = {
    primary: 'bg-emerald text-white hover:bg-emerald-50 focus-visible:ring-emerald',
    emerald: 'bg-emerald-100/15 text-emerald hover:bg-emerald-100/25 focus-visible:ring-emerald',
    crimson: 'bg-crimson-100/15 text-crimson hover:bg-crimson-100/25 focus-visible:ring-crimson',
    ghost: 'text-ink-secondary hover:bg-hover hover:text-ink-primary',
    outline: 'border border-edge text-ink-primary hover:bg-hover',
  };
</script>

<button
  type="button"
  class="{baseClass} {sizeClasses[size]} {variantClasses[variant]} {full ? 'w-full' : ''}"
  {disabled}
  on:click
>
  <slot />
</button>