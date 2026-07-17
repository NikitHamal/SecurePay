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

<svelte:head><title>Sign in · Touch Base</title></svelte:head>

<div class="flex min-h-screen flex-col items-center justify-center px-4 py-10" style="background-color: var(--bg-base);">
  <div class="w-full max-w-[400px]">
    <!-- Brand -->
    <div class="mb-8 flex flex-col items-center text-center">
      <img
        src="/branding/touchbase-mark-128.png"
        alt="Touch Base"
        class="mb-4 h-16 w-16 rounded-2xl object-contain"
      />
      <h1 class="text-2xl font-bold tracking-tight text-ink-primary">Touch Base</h1>
      <p class="mt-1 text-sm text-ink-muted">Connect · Finance · Grow</p>
    </div>

    <form on:submit={handleSubmit} class="card space-y-4 p-7">
      <div class="space-y-1">
        <h2 class="text-lg font-semibold text-ink-primary">Agent sign in</h2>
        <p class="text-sm text-ink-muted">Welcome back. Sign in to manage financed devices and collections.</p>
      </div>

      {#if $authError}
        <div class="rounded-lg border border-crimson/20 bg-crimson/10 px-3 py-2 text-sm text-crimson">
          {$authError}
        </div>
      {/if}

      <div class="space-y-1.5">
        <label for="email" class="text-sm font-medium text-ink-primary">Email</label>
        <input
          id="email"
          type="email"
          bind:value={email}
          placeholder="you@touchbase.io"
          autocomplete="email"
          required
          class="input w-full"
        />
      </div>

      <div class="space-y-1.5">
        <label for="password" class="text-sm font-medium text-ink-primary">Password</label>
        <input
          id="password"
          type="password"
          bind:value={password}
          placeholder="••••••••"
          autocomplete="current-password"
          required
          class="input w-full"
        />
      </div>

      <button type="submit" disabled={submitting} class="btn-primary mt-2 w-full py-2.5 text-[15px] font-semibold">
        {#if submitting}
          <span class="h-4 w-4 animate-spin rounded-full border-2 border-[color:var(--avatar-text)] border-t-transparent"></span>
          Signing in…
        {:else}Sign in{/if}
      </button>

      <div class="flex items-center justify-between pt-2 text-sm">
        <a href="/register" style="color: var(--brand);" class="font-medium hover:underline">Become an agent? Register</a>
        <span class="text-ink-muted">v1.0</span>
      </div>
    </form>

    <p class="mt-6 text-center text-xs text-ink-dim">
      Secure dealer access. Device-financing operations console.
    </p>
  </div>
</div>
