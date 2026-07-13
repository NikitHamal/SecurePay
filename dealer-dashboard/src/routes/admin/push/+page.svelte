<script lang="ts">
  import { dealer } from '$lib/stores/auth';
  import { apiClient } from '$lib/api/client';
  import Card from '$lib/components/ui/Card.svelte';
  import PageHeader from '$lib/components/ui/PageHeader.svelte';
  import TopBar from '$lib/components/layout/TopBar.svelte';
  import SearchInput from '$lib/components/ui/SearchInput.svelte';

  let accountId = '';
  let pushType = 'sync';
  let title = '';
  let message = '';
  let sending = false;
  let result = '';
  let resultOk = false;

  const PUSH_TYPES = [
    { value: 'sync', label: 'Sync Request', desc: 'Ask device to report its status' },
    { value: 'notification', label: 'Notification', desc: 'Show a notification message on device' },
    { value: 'stolen', label: 'Mark Stolen', desc: 'Flag device as stolen — locks screen, starts tracking' },
    { value: 'lock', label: 'Lock', desc: 'Lock the device immediately' },
    { value: 'unlock', label: 'Unlock', desc: 'Unlock the device, stop tracking' }
  ];

  async function send() {
    if (!accountId.trim()) return;
    sending = true;
    result = '';
    try {
      const res = await apiClient('/api/admin/push-device', {
        method: 'POST',
        body: JSON.stringify({
          accountId: accountId.trim(),
          type: pushType,
          title: title.trim() || undefined,
          message: message.trim() || undefined
        })
      });
      const data = await res.json();
      if (res.ok) {
        resultOk = true;
        result = `Push sent successfully to ${data.accountId}`;
      } else {
        resultOk = false;
        result = data.error || 'Failed to send push';
      }
    } catch (e) {
      resultOk = false;
      result = e instanceof Error ? e.message : 'Network error';
    } finally {
      sending = false;
    }
  }

  $: userRole = $dealer?.role;
</script>

<div class="page">
  <TopBar showSearch={false} />

  <PageHeader title="Push Notifications" subtitle="Send real-time messages to customer devices">
  </PageHeader>

  {#if userRole !== 'SUPER_ADMIN' && userRole !== undefined}
    <Card>
      <div class="rounded-lg border border-crimson-200/30 bg-crimson-200/10 px-4 py-3 text-sm text-crimson">
        Only Super Administrators can send push messages.
      </div>
    </Card>
  {:else}
    <Card>
      <div class="space-y-4">
        <div>
          <label class="mb-1 block text-xs font-medium text-ink-secondary">Account ID</label>
          <SearchInput placeholder="e.g. ACC-XXXXXX or numeric account ID" bind:value={accountId} />
        </div>

        <div>
          <label class="mb-1 block text-xs font-medium text-ink-secondary">Push Type</label>
          <div class="grid grid-cols-1 gap-2 sm:grid-cols-2">
            {#each PUSH_TYPES as pt}
              <button
                type="button"
                on:click={() => pushType = pt.value}
                class="rounded-lg border px-3 py-2 text-left text-sm transition-all
                       {pushType === pt.value
                  ? 'border-emerald bg-emerald/10 text-emerald'
                  : 'border-edge text-ink-secondary hover:border-ink-muted hover:text-ink-primary'}"
              >
                <span class="block font-medium">{pt.label}</span>
                <span class="mt-0.5 block text-2xs text-ink-muted">{pt.desc}</span>
              </button>
            {/each}
          </div>
        </div>

        {#if pushType === 'notification'}
          <div>
            <label class="mb-1 block text-xs font-medium text-ink-secondary">Title</label>
            <input type="text" class="input" placeholder="Notification heading" bind:value={title} />
          </div>
          <div>
            <label class="mb-1 block text-xs font-medium text-ink-secondary">Message</label>
            <textarea class="input min-h-[80px] resize-y" placeholder="Notification body text" bind:value={message}></textarea>
          </div>
        {/if}

        <button
          type="button"
          class="btn-emerald w-full"
          disabled={sending || !accountId.trim()}
          on:click={send}
        >
          {sending ? 'Sending...' : 'Send Push'}
        </button>

        {#if result}
          <div
            class="rounded-lg border px-4 py-3 text-sm
                   {resultOk ? 'border-emerald-200/30 bg-emerald-200/10 text-emerald' : 'border-crimson-200/30 bg-crimson-200/10 text-crimson'}"
          >
            {result}
          </div>
        {/if}
      </div>
    </Card>
  {/if}
</div>
