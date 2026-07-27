<script lang="ts">
  export let hover: boolean = true;
</script>

<div class="card w-full p-5 {hover ? 'card-hover' : ''} {$$props.class ?? ''}">
  <slot />
</div>