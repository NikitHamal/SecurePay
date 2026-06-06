<script lang="ts">
  export let title: string;
  export let subtitle = '';
</script>

<header class="mb-6">
  <h1 class="text-xl font-semibold text-text-primary md:text-2xl">{title}</h1>
  {#if subtitle}
    <p class="mt-1 text-sm text-text-secondary">{subtitle}</p>
  {/if}
</header>
