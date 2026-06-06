import { writable, derived, type Readable } from 'svelte/store';
import type { CustomerNode } from '$lib/types';
import {
  getNodes,
  extendTimer as apiExtendTimer,
  forceRemoteLock as apiForceRemoteLock,
  getKpis,
  type KpiSummary
} from '$lib/data/mockApi';

export interface NodesState {
  nodes: CustomerNode[];
  loading: boolean;
  error: string | null;
}

const initialState: NodesState = {
  nodes: [],
  loading: false,
  error: null
};

const store = writable<NodesState>(initialState);

function upsert(node: CustomerNode): void {
  store.update((state) => ({
    ...state,
    nodes: state.nodes.map((n) => (n.customerId === node.customerId ? node : n))
  }));
}

/**
 * Load (or refresh) the full node list from the mock API.
 */
export async function load(): Promise<void> {
  store.update((state) => ({ ...state, loading: true, error: null }));
  try {
    const nodes = await getNodes();
    store.update((state) => ({ ...state, nodes, loading: false }));
  } catch (err) {
    const message = err instanceof Error ? err.message : 'Failed to load nodes';
    store.update((state) => ({ ...state, loading: false, error: message }));
  }
}

/** Alias for `load` to make refresh intent explicit at call sites. */
export const refresh = load;

/**
 * Extend a device's payment timer by `hours` and update the store in place.
 */
export async function extendTimer(customerId: string, hours: number): Promise<void> {
  try {
    const updated = await apiExtendTimer(customerId, hours);
    upsert(updated);
  } catch (err) {
    const message = err instanceof Error ? err.message : 'Failed to extend timer';
    store.update((state) => ({ ...state, error: message }));
  }
}

/**
 * Force a remote lock on a device and update the store in place.
 */
export async function forceRemoteLock(customerId: string): Promise<void> {
  try {
    const updated = await apiForceRemoteLock(customerId);
    upsert(updated);
  } catch (err) {
    const message = err instanceof Error ? err.message : 'Failed to lock device';
    store.update((state) => ({ ...state, error: message }));
  }
}

/** The raw node-state store (nodes + loading + error). */
export const nodesState: Readable<NodesState> = { subscribe: store.subscribe };

/** Convenience derived store of just the node array. */
export const nodes: Readable<CustomerNode[]> = derived(store, ($s) => $s.nodes);

/** Derived KPI summary that recomputes whenever the nodes change. */
export const kpis: Readable<KpiSummary> = derived(store, ($s) => getKpis($s.nodes));
