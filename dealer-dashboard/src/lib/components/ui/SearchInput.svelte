<script lang="ts">
  export let placeholder: string = 'Search…';
  export let value: string = '';
  export let classAttr: string = '';
</script>

<label class="relative flex items-center {classAttr}">
  <svg class="absolute left-3 h-3.5 w-3.5 text-ink-muted" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
    <circle cx="11" cy="11" r="7" />
    <path d="M20 20l-3.5-3.5" stroke-linecap="round" />
  </svg>
  <input
    type="search"
    class="input pl-8"
    {placeholder}
    bind:value
    on:input
  />
</label>