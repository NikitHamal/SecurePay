<script lang="ts">
  import { apiClient } from '$lib/api/client';
  import { goto } from '$app/navigation';

  let formData = {
    fullName: '',
    email: '',
    phone: '',
    password: '',
    confirmPassword: '',
    requestedBranchId: ''
  };

  let submitting = false;
  let error = '';
  let success = false;

  async function handleSubmit(e: Event) {
    e.preventDefault();
    error = '';

    if (formData.password !== formData.confirmPassword) {
      error = 'Passwords do not match';
      return;
    }

    if (formData.password.length < 8) {
      error = 'Password must be at least 8 characters';
      return;
    }

    submitting = true;
    try {
      const res = await apiClient('/api/auth/register-agent', {
        method: 'POST',
        body: JSON.stringify({
          fullName: formData.fullName,
          email: formData.email,
          phone: formData.phone,
          password: formData.password,
          requestedBranchId: formData.requestedBranchId || undefined
        })
      });

      if (!res.ok) {
        const data = await res.json();
        throw new Error(data.error || 'Registration failed');
      }

      success = true;
      const data = await res.json();
      
      // Redirect to login after 3 seconds
      setTimeout(() => {
        goto('/login');
      }, 3000);
    } catch (e) {
      error = e instanceof Error ? e.message : 'Registration failed';
    } finally {
      submitting = false;
    }
  }
</script>

<div class="flex min-h-screen items-center justify-center bg-charcoal px-4">
  <div class="w-full max-w-md">
    <div class="mb-8 text-center">
      <div class="mb-4 inline-flex h-16 w-16 items-center justify-center rounded-2xl bg-emerald-500/20">
        <svg class="h-8 w-8 text-emerald-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round" d="M18 9v3m0 0v3m0-3h3m-3 0h-3m-2-5a4 4 0 11-8 0 4 4 0 018 0zM3 20a6 6 0 0112 0v1H3v-1z" />
        </svg>
      </div>
      <h1 class="text-2xl font-bold text-white">Become an Agent</h1>
      <p class="mt-1 text-sm text-ink-secondary">Register to start selling with SecurePay</p>
    </div>

    {#if success}
      <div class="card space-y-4 p-6">
        <div class="rounded-lg border border-emerald-300/30 bg-emerald-300/10 px-3 py-2 text-sm text-emerald">
          <div class="flex items-center gap-2">
            <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            Registration successful!
          </div>
        </div>
        <p class="text-sm text-ink-secondary">
          Your registration has been submitted. An admin will review your request and approve your account shortly.
        </p>
        <p class="text-xs text-ink-muted">
          Redirecting to login page...
        </p>
      </div>
    {:else}
      <form on:submit={handleSubmit} class="card space-y-4 p-6">
        {#if error}
          <div class="rounded-lg border border-crimson-200/30 bg-crimson-200/10 px-3 py-2 text-sm text-crimson">
            {error}
          </div>
        {/if}

        <div>
          <label for="fullName" class="mb-1 block text-sm font-medium text-ink-secondary">Full Name</label>
          <input
            id="fullName"
            type="text"
            bind:value={formData.fullName}
            placeholder="John Doe"
            required
            class="input w-full"
          />
        </div>

        <div>
          <label for="email" class="mb-1 block text-sm font-medium text-ink-secondary">Email</label>
          <input
            id="email"
            type="email"
            bind:value={formData.email}
            placeholder="you@example.com"
            required
            class="input w-full"
          />
        </div>

        <div>
          <label for="phone" class="mb-1 block text-sm font-medium text-ink-secondary">Phone</label>
          <input
            id="phone"
            type="tel"
            bind:value={formData.phone}
            placeholder="+233 XX XXX XXXX"
            required
            class="input w-full"
          />
        </div>

        <div>
          <label for="requestedBranchId" class="mb-1 block text-sm font-medium text-ink-secondary">
            Branch ID <span class="text-ink-muted">(optional)</span>
          </label>
          <input
            id="requestedBranchId"
            type="text"
            bind:value={formData.requestedBranchId}
            placeholder="BR-XXXXXXXX"
            class="input w-full"
          />
          <p class="mt-1 text-xs text-ink-muted">
            Leave empty to be assigned by admin
          </p>
        </div>

        <div>
          <label for="password" class="mb-1 block text-sm font-medium text-ink-secondary">Password</label>
          <input
            id="password"
            type="password"
            bind:value={formData.password}
            placeholder="••••••••"
            required
            minlength="8"
            class="input w-full"
          />
          <p class="mt-1 text-xs text-ink-muted">
            Minimum 8 characters
          </p>
        </div>

        <div>
          <label for="confirmPassword" class="mb-1 block text-sm font-medium text-ink-secondary">Confirm Password</label>
          <input
            id="confirmPassword"
            type="password"
            bind:value={formData.confirmPassword}
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
          {submitting ? 'Submitting...' : 'Register as Agent'}
        </button>

        <div class="text-center">
          <a href="/login" class="text-sm text-emerald hover:underline">
            Already have an account? Sign in
          </a>
        </div>
      </form>
    {/if}
  </div>
</div>
