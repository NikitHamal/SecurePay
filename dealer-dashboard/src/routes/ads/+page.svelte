<script lang="ts">
  import { onMount } from 'svelte';
  import { dealer } from '$lib/stores/auth';
  import { listAds, createAd, updateAd, deleteAd } from '$lib/api/ads';
  import type { Ad } from '$lib/types';
  import PageHeader from '$lib/components/ui/PageHeader.svelte';
  import TopBar from '$lib/components/layout/TopBar.svelte';
  import Card from '$lib/components/ui/Card.svelte';
  import Modal from '$lib/components/ui/Modal.svelte';

  let ads: Ad[] = [];
  let loading = true;
  let error = '';

  let showModal = false;
  let editing: Ad | null = null;
  let saving = false;
  let formTitle = '';
  let formDescription = '';
  let formUrlInput = '';
  let formImageData: string | null = null;
  let existingR2Key: string | null = null;
  let imagePreview: string | null = null;
  let formLinkUrl = '';
  let formIsActive = true;
  let formOrder = 0;
  let formError = '';

  let deleting = false;

  onMount(() => { loadAds(); });

  async function loadAds() {
    loading = true;
    error = '';
    try {
      ads = await listAds();
    } catch (e) {
      error = e instanceof Error ? e.message : 'Failed to load ads';
    } finally {
      loading = false;
    }
  }

  function openCreate() {
    editing = null;
    formTitle = '';
    formDescription = '';
    formUrlInput = '';
    formImageData = null;
    existingR2Key = null;
    formLinkUrl = '';
    formIsActive = true;
    formOrder = ads.length;
    formError = '';
    showModal = true;
  }

  function openEdit(ad: Ad) {
    editing = ad;
    formTitle = ad.title;
    formDescription = ad.description;
    if (ad.imageUrl && /^https?:\/\//i.test(ad.imageUrl)) {
      formUrlInput = ad.imageUrl;
      existingR2Key = null;
    } else {
      formUrlInput = '';
      existingR2Key = ad.imageUrl || null;
    }
    formImageData = null;
    formLinkUrl = ad.linkUrl || '';
    formIsActive = ad.isActive;
    formOrder = ad.order;
    formError = '';
    showModal = true;
  }

  function closeModal() {
    showModal = false;
    editing = null;
  }

  /** Resolved <img> source for the modal preview, or null when there is no image. */
  $: imagePreview = formImageData
    || formUrlInput.trim()
    || (existingR2Key && editing ? `/api/ads/${editing.id}/image` : '')
    || null;

  function onImageFileSelected(event: Event) {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    if (!/^image\/(jpeg|png|webp)$/i.test(file.type)) {
      formError = 'Image must be a JPEG, PNG or WebP file';
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      formError = 'Image must be 5MB or smaller';
      return;
    }
    formError = '';
    const reader = new FileReader();
    reader.onload = () => {
      const result = typeof reader.result === 'string' ? reader.result : '';
      formImageData = result || null;
      formUrlInput = '';
      existingR2Key = null;
    };
    reader.readAsDataURL(file);
    // Allow re-selecting the very same file afterwards.
    (event.target as HTMLInputElement).value = '';
  }

  function clearImage() {
    formUrlInput = '';
    formImageData = null;
    existingR2Key = null;
  }

  async function save() {
    if (!formTitle.trim()) {
      formError = 'Title is required';
      return;
    }
    saving = true;
    formError = '';
    try {
      const uploaded = !!formImageData;
      const payload = {
        title: formTitle.trim(),
        description: formDescription.trim(),
        imageUrl: uploaded ? null : (formUrlInput.trim() || existingR2Key || null),
        imageData: formImageData,
        linkUrl: formLinkUrl.trim() || null,
        isActive: formIsActive,
        order: formOrder
      };
      if (editing) {
        await updateAd(editing.id, payload);
      } else {
        await createAd(payload);
      }
      closeModal();
      await loadAds();
    } catch (e) {
      formError = e instanceof Error ? e.message : 'Failed to save ad';
    } finally {
      saving = false;
    }
  }

  async function remove(ad: Ad) {
    if (!confirm(`Delete "${ad.title}"? This cannot be undone.`)) return;
    deleting = true;
    error = '';
    try {
      await deleteAd(ad.id);
      ads = ads.filter(a => a.id !== ad.id);
    } catch (e) {
      error = e instanceof Error ? e.message : 'Failed to delete ad';
    } finally {
      deleting = false;
    }
  }

  async function toggleActive(ad: Ad) {
    try {
      await updateAd(ad.id, {
        title: ad.title,
        description: ad.description,
        imageUrl: ad.imageUrl,
        linkUrl: ad.linkUrl,
        isActive: !ad.isActive,
        order: ad.order
      });
      ad.isActive = !ad.isActive;
    } catch (e) {
      error = e instanceof Error ? e.message : 'Failed to toggle ad status';
    }
  }

  $: userRole = $dealer?.role;
</script>

<svelte:head><title>Ad Management · Touch Base</title></svelte:head>

<div class="page">
  <TopBar showSearch={false} />

  <PageHeader
    title="Ad Management"
    subtitle="Create and manage advertisements shown on customer devices."
  >
    <svelte:fragment slot="actions">
      {#if userRole === 'SUPER_ADMIN'}
        <button class="btn-primary" on:click={openCreate}>
          <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4">
            <path d="M12 5v14M5 12h14" stroke-linecap="round"/>
          </svg>
          Add Ad
        </button>
      {/if}
    </svelte:fragment>
  </PageHeader>

  {#if userRole !== 'SUPER_ADMIN' && userRole !== undefined}
    <Card>
      <div class="rounded-lg border border-crimson-200/30 bg-crimson-200/10 px-4 py-3 text-sm text-crimson">
        Only Super Administrators can manage advertisements.
      </div>
    </Card>
  {:else if error}
    <div class="mb-4 rounded-lg border border-crimson/20 bg-crimson/10 px-4 py-3 text-sm text-crimson">{error}</div>
  {/if}

  {#if userRole === 'SUPER_ADMIN'}
    <div class="card overflow-hidden">
      <div class="flex items-center justify-between border-b border-edge px-5 py-3">
        <div>
          <p class="text-sm font-semibold text-ink-primary">Advertisements</p>
          <p class="text-xs text-ink-muted">{ads.length} ad{ads.length !== 1 ? 's' : ''} · {ads.filter(a => a.isActive).length} active</p>
        </div>
        <button class="btn-outline !py-1.5 text-xs" on:click={loadAds} disabled={loading}>
          {loading ? 'Refreshing…' : 'Refresh'}
        </button>
      </div>

      <div class="overflow-x-auto">
        <table class="data-table min-w-[700px]">
          <thead>
            <tr>
              <th>Order</th>
              <th>Title</th>
              <th>Description</th>
              <th>Status</th>
              <th>Created</th>
              <th class="text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            {#if loading && ads.length === 0}
              <tr><td colspan="6" class="py-10 text-center text-ink-muted">Loading…</td></tr>
            {:else if ads.length === 0}
              <tr><td colspan="6" class="py-12 text-center">
                <p class="text-sm text-ink-primary">No ads yet</p>
                <p class="mt-1 text-xs text-ink-muted">Create your first advertisement to display on customer devices.</p>
                <button class="btn-primary mt-3" on:click={openCreate}>+ Add Ad</button>
              </td></tr>
            {:else}
              {#each ads as ad (ad.id)}
                <tr class="hover:bg-hover transition-colors">
                  <td class="text-xs text-ink-muted">{ad.order}</td>
                  <td class="text-sm font-medium text-ink-primary">{ad.title}</td>
                  <td class="max-w-xs truncate text-sm text-ink-secondary">{ad.description || '—'}</td>
                  <td>
                    <button
                      type="button"
                      on:click={() => toggleActive(ad)}
                      class="inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-medium transition-colors
                             {ad.isActive
                        ? 'border-emerald-300/30 bg-emerald-300/10 text-emerald hover:bg-emerald-300/20'
                        : 'border-edge bg-surface-100/60 text-ink-muted hover:bg-surface-100'}"
                    >
                      <span class="h-1.5 w-1.5 rounded-full {ad.isActive ? 'bg-emerald' : 'bg-ink-muted'}"></span>
                      {ad.isActive ? 'Active' : 'Inactive'}
                    </button>
                  </td>
                  <td class="text-xs text-ink-muted">
                    {ad.createdAt ? new Date(ad.createdAt).toLocaleDateString() : '—'}
                  </td>
                  <td class="text-right">
                    <div class="flex justify-end gap-2">
                      <button class="btn-outline !py-1 !px-2.5 text-xs" on:click={() => openEdit(ad)}>
                        Edit
                      </button>
                      <button
                        class="btn-outline !py-1 !px-2.5 text-xs text-crimson hover:bg-crimson/10"
                        disabled={deleting}
                        on:click={() => remove(ad)}
                      >
                        Delete
                      </button>
                    </div>
                  </td>
                </tr>
              {/each}
            {/if}
          </tbody>
        </table>
      </div>
    </div>
  {/if}
</div>

<Modal
  open={showModal}
  title={editing ? 'Edit Ad' : 'Create Ad'}
  size="lg"
  on:close={closeModal}
>
  <div class="space-y-4">
    {#if formError}
      <div class="rounded-lg border border-crimson/20 bg-crimson/10 px-4 py-3 text-sm text-crimson">{formError}</div>
    {/if}

    <div>
      <label class="mb-1 block text-xs font-medium text-ink-secondary" for="ad-title">Title *</label>
      <input id="ad-title" type="text" class="input" placeholder="e.g. Special Offer" bind:value={formTitle} />
    </div>

    <div>
      <label class="mb-1 block text-xs font-medium text-ink-secondary" for="ad-description">Description</label>
      <textarea id="ad-description" class="input min-h-[80px] resize-y" placeholder="Ad body text" bind:value={formDescription}></textarea>
    </div>

    <div>
      <span class="mb-1 block text-xs font-medium text-ink-secondary">Ad image</span>
      {#if imagePreview}
        <div class="mb-2 overflow-hidden rounded-lg border border-edge bg-surface-100/40">
          <img
            src={imagePreview}
            alt="Ad preview"
            class="max-h-40 w-full object-contain"
            on:error={() => { existingR2Key = null; }}
          />
        </div>
      {/if}
      <div class="flex flex-wrap items-center gap-2">
        <label class="btn-outline !py-1.5 text-xs cursor-pointer" for="ad-image-file">
          {formImageData ? 'Change file' : 'Upload image'}
        </label>
        <input
          id="ad-image-file"
          type="file"
          accept="image/jpeg,image/png,image/webp"
          class="hidden"
          on:change={onImageFileSelected}
        />
        <button type="button" class="btn-ghost !py-1.5 text-xs" on:click={clearImage} disabled={!imagePreview}>
          Remove
        </button>
      </div>
      <input
        id="ad-image-url"
        type="url"
        class="input mt-2"
        placeholder="…or paste an image URL (https://…)"
        bind:value={formUrlInput}
        on:input={() => { formImageData = null; existingR2Key = null; }}
      />
      <p class="mt-1 text-2xs text-ink-muted">Optional. Upload a file or paste a URL. Leave both empty for a text-only ad.</p>
    </div>

    <div>
      <label class="mb-1 block text-xs font-medium text-ink-secondary" for="ad-link-url">Link URL</label>
      <input id="ad-link-url" type="url" class="input" placeholder="https://example.com/offer" bind:value={formLinkUrl} />
      <p class="mt-1 text-2xs text-ink-muted">Optional. Opens when user taps the ad.</p>
    </div>

    <div class="grid grid-cols-2 gap-4">
      <div>
        <label class="mb-1 block text-xs font-medium text-ink-secondary" for="ad-sort-order">Sort Order</label>
        <input id="ad-sort-order" type="number" class="input" min="0" bind:value={formOrder} />
      </div>
      <div>
        <span class="mb-1 block text-xs font-medium text-ink-secondary">Status</span>
        <div class="flex h-input items-center gap-3">
          <label class="flex items-center gap-2 cursor-pointer">
            <input type="checkbox" bind:checked={formIsActive} class="h-4 w-4 rounded border-edge text-emerald focus:ring-emerald" />
            <span class="text-sm text-ink-primary">Active</span>
          </label>
        </div>
      </div>
    </div>
  </div>

  <svelte:fragment slot="footer">
    <button class="btn-ghost" on:click={closeModal}>Cancel</button>
    <button class="btn-primary" disabled={saving} on:click={save}>
      {saving ? 'Saving…' : editing ? 'Update Ad' : 'Create Ad'}
    </button>
  </svelte:fragment>
</Modal>
