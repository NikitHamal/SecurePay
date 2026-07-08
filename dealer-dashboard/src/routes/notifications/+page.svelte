<script lang="ts">
  import { onMount } from 'svelte';
  import { apiClient } from '$lib/api/client';
  import Card from '$lib/components/ui/Card.svelte';
  import PageHeader from '$lib/components/ui/PageHeader.svelte';

  interface Notification {
    id: string;
    type: string;
    title: string;
    message: string;
    isRead: boolean;
    relatedEntityType?: string;
    relatedEntityId?: string;
    createdAt: number;
  }

  let notifications: Notification[] = [];
  let loading = true;
  let error = '';
  let marking = false;

  onMount(async () => {
    await fetchNotifications();
  });

  async function fetchNotifications() {
    loading = true;
    error = '';
    try {
      const res = await apiClient('/api/notifications');
      if (!res.ok) throw new Error('Failed to fetch notifications');
      notifications = await res.json();
    } catch (e) {
      error = e instanceof Error ? e.message : 'Unknown error';
    } finally {
      loading = false;
    }
  }

  async function markAllRead() {
    const unreadIds = notifications.filter(n => !n.isRead).map(n => n.id);
    if (unreadIds.length === 0) return;
    
    marking = true;
    try {
      const res = await apiClient('/api/notifications', {
        method: 'POST',
        body: JSON.stringify({ ids: unreadIds })
      });
      if (!res.ok) throw new Error('Failed to mark as read');
      notifications = notifications.map(n => ({ ...n, isRead: true }));
    } catch (e) {
      alert(e instanceof Error ? e.message : 'Failed to mark notifications as read');
    } finally {
      marking = false;
    }
  }

  function formatDate(timestamp: number): string {
    const now = Date.now();
    const diff = now - timestamp;
    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);
    const days = Math.floor(diff / 86400000);
    
    if (minutes < 1) return 'Just now';
    if (minutes < 60) return `${minutes}m ago`;
    if (hours < 24) return `${hours}h ago`;
    if (days < 7) return `${days}d ago`;
    
    return new Date(timestamp).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric'
    });
  }

  function getIcon(type: string): string {
    switch (type) {
      case 'NEW_SALE': return 'M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z';
      case 'AGENT_APPROVED': return 'M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z';
      case 'KYC_VERIFIED': return 'M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z';
      case 'PAYMENT_RECEIVED': return 'M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z';
      default: return 'M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9';
    }
  }

  function getIconColor(type: string): string {
    switch (type) {
      case 'NEW_SALE': return 'text-emerald';
      case 'AGENT_APPROVED': return 'text-sky';
      case 'KYC_VERIFIED': return 'text-emerald';
      case 'PAYMENT_RECEIVED': return 'text-amber';
      default: return 'text-ink-secondary';
    }
  }

  $: unreadCount = notifications.filter(n => !n.isRead).length;
</script>

<PageHeader title="Notifications" subtitle="Stay updated on important events">
  {#if unreadCount > 0}
    <button type="button" class="btn-ghost" disabled={marking} on:click={markAllRead}>
      {marking ? 'Marking...' : `Mark all read (${unreadCount})`}
    </button>
  {/if}
</PageHeader>

{#if loading}
  <div class="flex items-center justify-center py-12">
    <div class="h-8 w-8 animate-spin rounded-full border-2 border-emerald border-t-transparent"></div>
  </div>
{:else if error}
  <Card>
    <div class="rounded-lg border border-crimson-200/30 bg-crimson-200/10 px-4 py-3 text-sm text-crimson">
      {error}
    </div>
  </Card>
{:else if notifications.length === 0}
  <Card>
    <div class="flex flex-col items-center justify-center py-12 text-center">
      <svg class="mb-3 h-12 w-12 text-ink-muted" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
        <path stroke-linecap="round" stroke-linejoin="round" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
      </svg>
      <p class="text-sm font-medium text-ink-primary">No notifications yet</p>
      <p class="mt-1 text-xs text-ink-muted">You'll be notified when important events occur</p>
    </div>
  </Card>
{:else}
  <div class="space-y-2">
    {#each notifications as notification (notification.id)}
      <Card hover={false}>
        <div class="flex items-start gap-3">
          <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-surface-100">
            <svg class="h-5 w-5 {getIconColor(notification.type)}" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
              <path stroke-linecap="round" stroke-linejoin="round" d={getIcon(notification.type)} />
            </svg>
          </div>
          <div class="flex-1 min-w-0">
            <div class="flex items-start justify-between gap-2">
              <div class="flex-1">
                <div class="flex items-center gap-2">
                  <h3 class="text-sm font-semibold text-ink-primary">{notification.title}</h3>
                  {#if !notification.isRead}
                    <span class="h-2 w-2 rounded-full bg-emerald"></span>
                  {/if}
                </div>
                <p class="mt-1 text-sm text-ink-secondary">{notification.message}</p>
                <p class="mt-1 text-xs text-ink-muted">{formatDate(notification.createdAt)}</p>
              </div>
            </div>
          </div>
        </div>
      </Card>
    {/each}
  </div>
{/if}
