<script lang="ts">
  import { login, authError } from '$lib/stores/auth';

  let email = '';
  let password = '';
  let submitting = false;

  async function handleSubmit(e: Event) {
    e.preventDefault();
    submitting = true;
    await login(email, password);
    submitting = false;
  }
</script>

<svelte:head><title>Sign in · SecurePay</title></svelte:head>

<div class="flex min-h-screen items-center justify-center px-4 py-10" style="background-color: var(--bg-base);">
  <div class="w-full max-w-sm">
    <div class="mb-6 flex flex-col items-center">
      <div class="mb-3 flex h-12 w-12 items-center justify-center rounded-xl" style="background-color: var(--brand);">
        <svg class="h-6 w-6 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round" d="M12 2l8 4v6c0 5-3.5 8-8 10-4.5-2-8-5-8-10V6l8-4z"/>
          <path stroke-linecap="round" stroke-linejoin="round" d="M9 12l2 2 4-4"/>
        </svg>
      </div>
      <h1 class="text-xl font-semibold text-ink-primary">SecurePay</h1>
      <p class="mt-0.5 text-sm text-ink-muted">Dealer Console</p>
    </div>

    <form on:submit={handleSubmit} class="card space-y-4 p-6">
      {#if $authError}
        <div class="rounded-lg border border-crimson/20 bg-crimson/10 px-3 py-2 text-sm text-crimson">
          {$authError}
        </div>
      {/if}

      <div>
        <label for="email" class="label">Email</label>
        <input id="email" type="email" bind:value={email} placeholder="you@securepay.io" required class="input w-full" />
      </div>

      <div>
        <label for="password" class="label">Password</label>
        <input id="password" type="password" bind:value={password} placeholder="••••••••" required class="input w-full" />
      </div>

      <button type="submit" disabled={submitting} class="btn-primary w-full">
        {#if submitting}
          <span class="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent"></span>
          Signing in…
        {:else}Sign in{/if}
      </button>

      <div class="text-center">
        <a href="/register" class="text-sm text-emerald hover:underline">Become an agent? Register here</a>
      </div>
    </form>
  </div>
</div>
