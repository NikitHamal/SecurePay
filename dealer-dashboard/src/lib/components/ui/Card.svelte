<script lang="ts">
  export let hover: boolean = false;
</script>

<div class="card {hover ? 'card-hover' : ''} p-5 {$$props.class ?? ''}">
  <slot />
</div>