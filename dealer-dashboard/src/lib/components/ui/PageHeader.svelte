<script lang="ts">
  export let title: string;
  export let subtitle: string = '';
  export let eyebrow: string = '';
</script>

<header class="mb-6 flex flex-wrap items-end justify-between gap-4">
  <div>
    {#if eyebrow}
      <p class="section-title mb-1.5">{eyebrow}</p>
    {/if}
    <h1 class="text-2xl font-semibold text-ink-primary md:text-3xl tracking-tight">{title}</h1>
    {#if subtitle}
      <p class="mt-1.5 max-w-2xl text-sm text-ink-secondary text-balance">{subtitle}</p>
    {/if}
  </div>
  <div class="flex items-center gap-2">
    <slot name="actions" />
  </div>
</header>