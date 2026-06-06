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

<div class="flex min-h-screen items-center justify-center bg-charcoal px-4">
  <div class="w-full max-w-md">
    <div class="mb-8 text-center">
      <div class="mb-4 inline-flex h-16 w-16 items-center justify-center rounded-2xl bg-emerald-500/20">
        <svg class="h-8 w-8 text-emerald-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
        </svg>
      </div>
      <h1 class="text-2xl font-bold text-white">SecurePay</h1>
      <p class="mt-1 text-sm text-ink-secondary">Dealer Dashboard</p>
    </div>

    <form on:submit={handleSubmit} class="card space-y-4 p-6">
      {#if $authError}
        <div class="rounded-lg border border-crimson-200/30 bg-crimson-200/10 px-3 py-2 text-sm text-crimson">
          {$authError}
        </div>
      {/if}

      <div>
        <label for="email" class="mb-1 block text-sm font-medium text-ink-secondary">Email</label>
        <input
          id="email"
          type="email"
          bind:value={email}
          placeholder="dealer@securepay.io"
          required
          class="input w-full"
        />
      </div>

      <div>
        <label for="password" class="mb-1 block text-sm font-medium text-ink-secondary">Password</label>
        <input
          id="password"
          type="password"
          bind:value={password}
          placeholder="••••••••"
          required
          class="input w-full"
        />
      </div>

      <button
        type="submit"
        disabled={submitting}
        class="btn-primary w-full"
      >
        {submitting ? 'Signing in...' : 'Sign in'}
      </button>
    </form>

    <p class="mt-4 text-center text-xs text-ink-tertiary">
      Demo: dealer@securepay.io / dealer123
    </p>
  </div>
</div>