import { apiClient } from '$lib/api/client';
import type { Ad } from '$lib/types';

export async function listAds(): Promise<Ad[]> {
  const res = await apiClient('/api/ads');
  if (!res.ok) throw new Error('Failed to fetch ads');
  const data = await res.json();
  return data.ads || [];
}

export async function getAd(id: string): Promise<Ad> {
  const res = await apiClient(`/api/ads/${encodeURIComponent(id)}`);
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: 'Not found' }));
    throw new Error(body.error || 'Ad not found');
  }
  const data = await res.json();
  return data.ad;
}

export async function createAd(data: {
  title: string;
  description?: string;
  imageUrl?: string | null;
  linkUrl?: string | null;
  isActive?: boolean;
  order?: number;
}): Promise<Ad> {
  const res = await apiClient('/api/ads', {
    method: 'POST',
    body: JSON.stringify(data)
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: 'Failed to create ad' }));
    throw new Error(body.error || 'Failed to create ad');
  }
  const result = await res.json();
  return result.ad;
}

export async function updateAd(id: string, data: {
  title: string;
  description?: string;
  imageUrl?: string | null;
  linkUrl?: string | null;
  isActive?: boolean;
  order?: number;
}): Promise<Ad> {
  const res = await apiClient(`/api/ads/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(data)
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: 'Failed to update ad' }));
    throw new Error(body.error || 'Failed to update ad');
  }
  const result = await res.json();
  return result.ad;
}

export async function deleteAd(id: string): Promise<void> {
  const res = await apiClient(`/api/ads/${encodeURIComponent(id)}`, {
    method: 'DELETE'
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: 'Failed to delete ad' }));
    throw new Error(body.error || 'Failed to delete ad');
  }
}
