<script lang="ts">
  export let hover: boolean = true;
</script>

<div class="card {hover ? 'card-hover' : ''} {$$props.class ?? ''}">
  <slot />
</div>