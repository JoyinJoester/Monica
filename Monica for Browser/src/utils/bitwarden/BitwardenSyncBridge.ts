import type { PasswordEntry, SecureItem } from '../../types/models';
import { ItemType } from '../../types/models';
import { bitwardenAuthClient, type BitwardenSession } from './BitwardenAuthClient';

export type BitwardenLocalOperation = 'upsert' | 'delete' | 'restore' | 'purge';

export interface BitwardenLocalMutationPayload {
  operation: BitwardenLocalOperation;
  item: SecureItem;
}

export interface BitwardenSyncStatus {
  inProgress: boolean;
  pending: boolean;
  lastSyncAt?: number;
  lastError?: string;
  lastTrigger?: 'manual' | 'local_mutation' | 'app_resume';
  lastCipherCount?: number;
  pendingQueueCount?: number;
  failedQueueCount?: number;
  conflictCount?: number;
  emptyVaultProtectionBlocked?: boolean;
  emptyVaultLocalCount?: number;
  emptyVaultServerCount?: number;
}

interface BitwardenPendingOperation {
  id: string;
  operation: BitwardenLocalOperation;
  itemId: number;
  itemType: number;
  bitwardenVaultId?: number;
  bitwardenCipherId?: string;
  snapshot: SecureItem;
  status: 'pending' | 'failed';
  retryCount: number;
  createdAt: number;
  updatedAt: number;
  lastAttemptAt?: number;
  lastError?: string;
}

interface BitwardenConflictBackup {
  id: string;
  itemId: number;
  bitwardenCipherId?: string;
  reason: 'remote_newer_than_local_pending';
  localSnapshot: SecureItem;
  serverSnapshot: {
    id: string;
    revisionDate?: string;
    deletedDate?: string;
  };
  createdAt: number;
}

interface BitwardenRemoteCipher {
  id: string;
  revisionDate?: string;
  deletedDate?: string;
}

interface BitwardenCipherApiResponse {
  Id?: unknown;
  id?: unknown;
  RevisionDate?: unknown;
  revisionDate?: unknown;
}

interface BitwardenCipherUpsertResponse {
  cipherId?: string;
  revisionDate?: string;
}

interface BitwardenQueueOperationResult {
  success: boolean;
  error?: string;
}

interface BitwardenSymmetricKey {
  encKey: Uint8Array;
  macKey: Uint8Array;
}

const STORAGE_KEY_SYNC_STATUS = 'bitwarden_sync_status_v2';
const STORAGE_KEY_PENDING_OPS = 'bitwarden_pending_ops_v1';
const STORAGE_KEY_CONFLICTS = 'bitwarden_conflict_backups_v1';
const STORAGE_KEY_MONICA_VAULT = 'monica_vault';

const MAX_CONFLICT_BACKUPS = 200;
const textEncoder = new TextEncoder();

const readStorage = async <T>(key: string): Promise<T | null> => {
  if (typeof chrome !== 'undefined' && chrome.storage?.local) {
    const result = await chrome.storage.local.get(key);
    const raw = (result as Record<string, unknown>)[key];
    return (raw as T) ?? null;
  }
  const raw = localStorage.getItem(key);
  return raw ? (JSON.parse(raw) as T) : null;
};

const writeStorage = async (key: string, value: unknown): Promise<void> => {
  if (typeof chrome !== 'undefined' && chrome.storage?.local) {
    await chrome.storage.local.set({ [key]: value });
    return;
  }
  localStorage.setItem(key, JSON.stringify(value));
};

const ensureTrailingSlash = (value: string): string => (value.endsWith('/') ? value : `${value}/`);

const normalizeCipherId = (value: unknown): string | undefined => {
  if (typeof value !== 'string') return undefined;
  const trimmed = value.trim();
  return trimmed || undefined;
};

const normalizeIso = (value: unknown): string | undefined => {
  if (typeof value !== 'string') return undefined;
  const trimmed = value.trim();
  if (!trimmed) return undefined;
  const time = new Date(trimmed).getTime();
  return Number.isNaN(time) ? undefined : new Date(time).toISOString();
};

const toMillis = (iso: string | undefined): number => {
  if (!iso) return 0;
  const time = new Date(iso).getTime();
  return Number.isNaN(time) ? 0 : time;
};

const cloneItem = (item: SecureItem): SecureItem => JSON.parse(JSON.stringify(item)) as SecureItem;

const normalizeString = (value: unknown): string | undefined => {
  if (typeof value !== 'string') return undefined;
  const trimmed = value.trim();
  return trimmed || undefined;
};

const bytesToBase64 = (bytes: Uint8Array): string => {
  let binary = '';
  for (let i = 0; i < bytes.length; i += 1) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
};

const base64ToBytes = (raw: string): Uint8Array => {
  const normalized = raw.replace(/-/g, '+').replace(/_/g, '/');
  const padding = (4 - (normalized.length % 4)) % 4;
  const padded = `${normalized}${'='.repeat(padding)}`;
  const binary = atob(padded);
  const out = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) {
    out[i] = binary.charCodeAt(i);
  }
  return out;
};

const concatBytes = (...parts: Uint8Array[]): Uint8Array => {
  const total = parts.reduce((sum, current) => sum + current.length, 0);
  const out = new Uint8Array(total);
  let offset = 0;
  for (const part of parts) {
    out.set(part, offset);
    offset += part.length;
  }
  return out;
};

const parseJsonObject = (raw: string): Record<string, unknown> | null => {
  const trimmed = raw.trim();
  if (!trimmed) return null;
  try {
    const parsed = JSON.parse(trimmed) as unknown;
    return parsed && typeof parsed === 'object' ? (parsed as Record<string, unknown>) : null;
  } catch {
    return null;
  }
};

class BitwardenSyncBridge {
  private inFlight = false;

  private timer: ReturnType<typeof setTimeout> | null = null;

  private async loadVaultItems(): Promise<SecureItem[]> {
    const items = await readStorage<SecureItem[]>(STORAGE_KEY_MONICA_VAULT);
    return Array.isArray(items) ? items : [];
  }

  private async saveVaultItems(items: SecureItem[]): Promise<void> {
    await writeStorage(STORAGE_KEY_MONICA_VAULT, items);
  }

  private async loadPendingOperations(): Promise<BitwardenPendingOperation[]> {
    const raw = await readStorage<BitwardenPendingOperation[]>(STORAGE_KEY_PENDING_OPS);
    return Array.isArray(raw) ? raw : [];
  }

  private async savePendingOperations(ops: BitwardenPendingOperation[]): Promise<void> {
    await writeStorage(STORAGE_KEY_PENDING_OPS, ops);
  }

  private async loadConflicts(): Promise<BitwardenConflictBackup[]> {
    const raw = await readStorage<BitwardenConflictBackup[]>(STORAGE_KEY_CONFLICTS);
    return Array.isArray(raw) ? raw : [];
  }

  private async saveConflicts(conflicts: BitwardenConflictBackup[]): Promise<void> {
    const normalized = conflicts
      .sort((a, b) => b.createdAt - a.createdAt)
      .slice(0, MAX_CONFLICT_BACKUPS);
    await writeStorage(STORAGE_KEY_CONFLICTS, normalized);
  }

  async loadConflictBackups(): Promise<BitwardenConflictBackup[]> {
    return this.loadConflicts();
  }

  private getPasswordData(item: SecureItem): PasswordEntry | null {
    if (item.itemType !== ItemType.Password) return null;
    return item.itemData as PasswordEntry;
  }

  private getItemBinding(item: SecureItem): { bitwardenVaultId?: number; bitwardenCipherId?: string } {
    const data = this.getPasswordData(item);
    if (!data) return {};
    return {
      bitwardenVaultId: typeof data.bitwardenVaultId === 'number' ? data.bitwardenVaultId : undefined,
      bitwardenCipherId: normalizeCipherId(data.bitwardenCipherId),
    };
  }

  private coalesceOperation(
    existing: BitwardenPendingOperation | undefined,
    incoming: BitwardenLocalOperation,
    hasCipherBinding: boolean
  ): BitwardenLocalOperation | null {
    if (!existing) {
      if ((incoming === 'delete' || incoming === 'purge') && !hasCipherBinding) {
        return null;
      }
      if (incoming === 'restore' && !hasCipherBinding) {
        return 'upsert';
      }
      return incoming;
    }

    if (incoming === 'purge') {
      return hasCipherBinding ? 'purge' : null;
    }

    if (incoming === 'delete') {
      if (existing.operation === 'upsert' && !hasCipherBinding) {
        return null;
      }
      return 'delete';
    }

    if (incoming === 'restore') {
      if (!hasCipherBinding) return 'upsert';
      return existing.operation === 'delete' || existing.operation === 'purge' ? 'restore' : 'upsert';
    }

    if (existing.operation === 'delete' || existing.operation === 'purge') {
      return hasCipherBinding ? 'restore' : 'upsert';
    }

    return 'upsert';
  }

  private async updateStatusCounters(
    ops: BitwardenPendingOperation[],
    overrides?: Partial<BitwardenSyncStatus>
  ): Promise<void> {
    const conflicts = await this.loadConflicts();
    await this.saveStatus({
      pendingQueueCount: ops.filter((op) => op.status === 'pending').length,
      failedQueueCount: ops.filter((op) => op.status === 'failed').length,
      conflictCount: conflicts.length,
      ...overrides,
    });
  }

  async recordLocalMutation(mutation: BitwardenLocalMutationPayload): Promise<void> {
    if (mutation.item.itemType !== ItemType.Password) return;

    const ops = await this.loadPendingOperations();
    const index = ops.findIndex((op) => op.itemId === mutation.item.id);
    const existing = index >= 0 ? ops[index] : undefined;

    const binding = this.getItemBinding(mutation.item);
    const bitwardenCipherId = binding.bitwardenCipherId || existing?.bitwardenCipherId;
    const bitwardenVaultId = binding.bitwardenVaultId ?? existing?.bitwardenVaultId;
    const hasBinding = !!bitwardenCipherId || typeof bitwardenVaultId === 'number';

    if (!hasBinding && !existing) {
      return;
    }

    const coalesced = this.coalesceOperation(existing, mutation.operation, !!bitwardenCipherId);
    if (!coalesced) {
      if (index >= 0) {
        ops.splice(index, 1);
      }
      await this.savePendingOperations(ops);
      await this.updateStatusCounters(ops, { pending: true });
      this.requestLocalMutationSync();
      return;
    }

    const now = Date.now();
    const next: BitwardenPendingOperation = {
      id: existing?.id || `${mutation.item.id}-${now}`,
      operation: coalesced,
      itemId: mutation.item.id,
      itemType: mutation.item.itemType,
      bitwardenVaultId,
      bitwardenCipherId,
      snapshot: cloneItem(mutation.item),
      status: 'pending',
      retryCount: existing?.retryCount || 0,
      createdAt: existing?.createdAt || now,
      updatedAt: now,
      lastAttemptAt: existing?.lastAttemptAt,
      lastError: undefined,
    };

    if (index >= 0) {
      ops[index] = next;
    } else {
      ops.push(next);
    }

    await this.savePendingOperations(ops);
    await this.updateStatusCounters(ops, { pending: true });
    this.requestLocalMutationSync();
  }

  private async saveStatus(partial: Partial<BitwardenSyncStatus>): Promise<BitwardenSyncStatus> {
    const current = (await this.loadStatus()) ?? {
      inProgress: false,
      pending: false,
    };
    const merged: BitwardenSyncStatus = { ...current, ...partial };
    await writeStorage(STORAGE_KEY_SYNC_STATUS, merged);
    return merged;
  }

  async loadStatus(): Promise<BitwardenSyncStatus | null> {
    return readStorage<BitwardenSyncStatus>(STORAGE_KEY_SYNC_STATUS);
  }

  private canUseWifiOnlyMode(): boolean {
    const nav = navigator as Navigator & { connection?: { type?: string; effectiveType?: string } };
    const connection = nav.connection;
    if (!connection) return true;
    if (connection.type) return connection.type === 'wifi';
    if (connection.effectiveType) return connection.effectiveType === '4g';
    return true;
  }

  private getSessionSymmetricKey(session: BitwardenSession): BitwardenSymmetricKey | null {
    const encKeyRaw = normalizeString(session.symmetricEncKey);
    const macKeyRaw = normalizeString(session.symmetricMacKey);
    if (!encKeyRaw || !macKeyRaw) {
      return null;
    }
    try {
      return {
        encKey: base64ToBytes(encKeyRaw),
        macKey: base64ToBytes(macKeyRaw),
      };
    } catch {
      return null;
    }
  }

  private async hmacSha256(keyBytes: Uint8Array, data: Uint8Array): Promise<Uint8Array> {
    const key = await crypto.subtle.importKey(
      'raw',
      keyBytes.slice().buffer as unknown as BufferSource,
      { name: 'HMAC', hash: 'SHA-256' },
      false,
      ['sign']
    );
    const signature = await crypto.subtle.sign('HMAC', key, data.slice().buffer as unknown as BufferSource);
    return new Uint8Array(signature);
  }

  private async encryptString(plainText: string, key: BitwardenSymmetricKey): Promise<string> {
    const iv = crypto.getRandomValues(new Uint8Array(16));
    const aesKey = await crypto.subtle.importKey(
      'raw',
      key.encKey.slice().buffer as unknown as BufferSource,
      { name: 'AES-CBC' },
      false,
      ['encrypt']
    );
    const encrypted = await crypto.subtle.encrypt(
      { name: 'AES-CBC', iv: iv.slice().buffer as unknown as BufferSource },
      aesKey,
      textEncoder.encode(plainText).slice().buffer as unknown as BufferSource
    );
    const encryptedBytes = new Uint8Array(encrypted);
    const mac = await this.hmacSha256(key.macKey, concatBytes(iv, encryptedBytes));
    return `2.${bytesToBase64(iv)}|${bytesToBase64(encryptedBytes)}|${bytesToBase64(mac)}`;
  }

  private async buildPasswordCipherPayload(item: SecureItem, key: BitwardenSymmetricKey): Promise<Record<string, unknown>> {
    const entry = this.getPasswordData(item);
    if (!entry) {
      throw new Error('Only password items can be uploaded to Bitwarden ciphers');
    }

    const title = normalizeString(item.title) || 'Untitled';
    const notes = normalizeString(item.notes);
    const username = normalizeString(entry.username);
    const password = normalizeString(entry.password);
    const website = normalizeString(entry.website);
    const folderId = normalizeString(entry.bitwardenFolderId);

    const loginPayload: Record<string, unknown> = {};
    if (username) {
      loginPayload.Username = await this.encryptString(username, key);
    }
    if (password) {
      loginPayload.Password = await this.encryptString(password, key);
    }
    if (website) {
      loginPayload.Uris = [{ Uri: await this.encryptString(website, key) }];
    }

    const payload: Record<string, unknown> = {
      Type: 1,
      Name: await this.encryptString(title, key),
      Favorite: !!item.isFavorite,
      Reprompt: 0,
    };
    if (notes) {
      payload.Notes = await this.encryptString(notes, key);
    }
    if (folderId) {
      payload.FolderId = folderId;
    }
    if (Object.keys(loginPayload).length > 0) {
      payload.Login = loginPayload;
    }

    return payload;
  }

  private parseCipherUpsertResponse(rawBody: string): BitwardenCipherUpsertResponse {
    const parsed = parseJsonObject(rawBody) as BitwardenCipherApiResponse | null;
    if (!parsed) return {};
    return {
      cipherId: normalizeCipherId(parsed.Id ?? parsed.id),
      revisionDate: normalizeIso(parsed.RevisionDate ?? parsed.revisionDate),
    };
  }

  private async bindLocalItemToCipher(itemId: number, cipherId: string, revisionDate?: string): Promise<void> {
    const items = await this.loadVaultItems();
    const index = items.findIndex((item) => item.id === itemId && item.itemType === ItemType.Password);
    if (index < 0) return;

    const target = items[index];
    const entry = this.getPasswordData(target);
    if (!entry) return;

    const normalizedCipherId = normalizeCipherId(cipherId);
    if (!normalizedCipherId) return;
    if (normalizeCipherId(entry.bitwardenCipherId) === normalizedCipherId) {
      return;
    }

    items[index] = {
      ...target,
      updatedAt: revisionDate || target.updatedAt,
      itemData: {
        ...entry,
        bitwardenCipherId: normalizedCipherId,
      },
    };
    await this.saveVaultItems(items);
  }

  private async upsertCipher(
    session: BitwardenSession,
    op: BitwardenPendingOperation,
    payload: Record<string, unknown>
  ): Promise<BitwardenQueueOperationResult> {
    const base = ensureTrailingSlash(session.serverUrls.api);
    const boundCipherId = normalizeCipherId(op.bitwardenCipherId) || normalizeCipherId(this.getPasswordData(op.snapshot)?.bitwardenCipherId);
    const updateUrl = boundCipherId ? `${base}ciphers/${encodeURIComponent(boundCipherId)}` : '';
    const createUrl = `${base}ciphers`;
    const headers = {
      Authorization: `Bearer ${session.accessToken}`,
      'Content-Type': 'application/json',
    };

    const parseFailure = (status: number, body: string, fallback: string): BitwardenQueueOperationResult => {
      const parsed = parseJsonObject(body);
      const message =
        normalizeString(parsed?.error_description) ||
        normalizeString(parsed?.error) ||
        normalizeString((parsed?.ErrorModel as Record<string, unknown> | undefined)?.Message) ||
        normalizeString((parsed?.errorModel as Record<string, unknown> | undefined)?.message);
      return {
        success: false,
        error: message || `${fallback} (${status})`,
      };
    };

    const doCreate = async (): Promise<BitwardenQueueOperationResult> => {
      const response = await fetch(createUrl, {
        method: 'POST',
        headers,
        body: JSON.stringify(payload),
      });
      const rawBody = await response.text();
      if (!response.ok) {
        return parseFailure(response.status, rawBody, 'Create cipher failed');
      }
      const parsed = this.parseCipherUpsertResponse(rawBody);
      const finalId = parsed.cipherId;
      if (!finalId) {
        return { success: false, error: 'Create cipher succeeded but response is missing cipher id' };
      }
      await this.bindLocalItemToCipher(op.itemId, finalId, parsed.revisionDate);
      return { success: true };
    };

    try {
      if (boundCipherId) {
        const updateResponse = await fetch(updateUrl, {
          method: 'PUT',
          headers,
          body: JSON.stringify(payload),
        });
        const updateRawBody = await updateResponse.text();
        if (updateResponse.ok) {
          const parsed = this.parseCipherUpsertResponse(updateRawBody);
          await this.bindLocalItemToCipher(op.itemId, parsed.cipherId || boundCipherId, parsed.revisionDate);
          return { success: true };
        }
        if (updateResponse.status !== 404) {
          return parseFailure(updateResponse.status, updateRawBody, 'Update cipher failed');
        }
      }

      return doCreate();
    } catch (e) {
      return { success: false, error: (e as Error).message || 'Upsert cipher failed' };
    }
  }

  private async runQueueOperation(
    session: BitwardenSession,
    op: BitwardenPendingOperation
  ): Promise<BitwardenQueueOperationResult> {
    if (op.operation === 'upsert') {
      const symmetricKey = this.getSessionSymmetricKey(session);
      if (!symmetricKey) {
        return { success: false, error: 'Missing Bitwarden symmetric key, please login again' };
      }
      try {
        const payload = await this.buildPasswordCipherPayload(op.snapshot, symmetricKey);
        return this.upsertCipher(session, op, payload);
      } catch (e) {
        return { success: false, error: (e as Error).message || 'Upsert cipher failed' };
      }
    }

    const cipherId = normalizeCipherId(op.bitwardenCipherId) || normalizeCipherId(this.getPasswordData(op.snapshot)?.bitwardenCipherId);
    if (!cipherId) {
      return { success: false, error: 'Missing bitwardenCipherId for remote operation' };
    }

    const base = ensureTrailingSlash(session.serverUrls.api);
    let url = `${base}ciphers/${encodeURIComponent(cipherId)}`;
    let method: 'DELETE' | 'PUT' = 'DELETE';

    if (op.operation === 'purge') {
      url = `${url}/delete`;
      method = 'DELETE';
    } else if (op.operation === 'restore') {
      url = `${url}/restore`;
      method = 'PUT';
    } else {
      method = 'DELETE';
    }

    try {
      const response = await fetch(url, {
        method,
        headers: {
          Authorization: `Bearer ${session.accessToken}`,
        },
      });

      const tolerates404 = op.operation === 'delete' || op.operation === 'purge';
      if (response.ok || (tolerates404 && response.status === 404)) {
        return { success: true };
      }
      return { success: false, error: `Queue operation failed (${response.status})` };
    } catch (e) {
      return { success: false, error: (e as Error).message || 'Queue operation failed' };
    }
  }

  private async processPendingOperations(
    session: BitwardenSession,
    queue: BitwardenPendingOperation[]
  ): Promise<{ queue: BitwardenPendingOperation[]; firstError?: string }> {
    const nextQueue: BitwardenPendingOperation[] = [];
    let firstError: string | undefined;

    const sorted = [...queue].sort((a, b) => a.updatedAt - b.updatedAt);
    for (const op of sorted) {
      const result = await this.runQueueOperation(session, op);
      if (result.success) {
        continue;
      }

      if (!firstError) firstError = result.error;
      nextQueue.push({
        ...op,
        status: 'failed',
        retryCount: op.retryCount + 1,
        lastAttemptAt: Date.now(),
        lastError: result.error,
      });
    }

    return { queue: nextQueue, firstError };
  }

  private extractRemoteCiphers(syncPayload: Record<string, unknown>): BitwardenRemoteCipher[] {
    const raw = Array.isArray(syncPayload.Ciphers)
      ? syncPayload.Ciphers
      : Array.isArray(syncPayload.ciphers)
        ? syncPayload.ciphers
        : [];

    const ciphers: BitwardenRemoteCipher[] = [];
    for (const entry of raw) {
      if (!entry || typeof entry !== 'object') continue;
      const record = entry as Record<string, unknown>;
      const id = normalizeCipherId(record.Id ?? record.id);
      if (!id) continue;
      ciphers.push({
        id,
        revisionDate: normalizeIso(record.RevisionDate ?? record.revisionDate),
        deletedDate: normalizeIso(record.DeletedDate ?? record.deletedDate),
      });
    }

    return ciphers;
  }

  private async reconcileRemoteDeletionState(
    remoteCiphers: BitwardenRemoteCipher[],
    queue: BitwardenPendingOperation[]
  ): Promise<{ updatedCount: number; newConflicts: number }> {
    const items = await this.loadVaultItems();
    if (items.length === 0) return { updatedCount: 0, newConflicts: 0 };

    const pendingByItemId = new Map<number, BitwardenPendingOperation>();
    queue
      .filter((op) => op.status === 'pending' || op.status === 'failed')
      .forEach((op) => pendingByItemId.set(op.itemId, op));

    const remoteByCipher = new Map<string, BitwardenRemoteCipher>();
    remoteCiphers.forEach((cipher) => remoteByCipher.set(cipher.id, cipher));

    const conflicts = await this.loadConflicts();
    const conflictKeySet = new Set(
      conflicts.map((conflict) => `${conflict.itemId}|${conflict.serverSnapshot.id}|${conflict.serverSnapshot.revisionDate || ''}`)
    );

    let updatedCount = 0;
    let newConflicts = 0;

    for (let i = 0; i < items.length; i += 1) {
      const item = items[i];
      if (item.itemType !== ItemType.Password) continue;

      const data = this.getPasswordData(item);
      const cipherId = normalizeCipherId(data?.bitwardenCipherId);
      if (!cipherId) continue;

      const remote = remoteByCipher.get(cipherId);
      if (!remote) continue;

      const pending = pendingByItemId.get(item.id);
      if (pending) {
        const remoteRevision = toMillis(remote.revisionDate);
        const localRevision = toMillis(item.updatedAt);
        if (remoteRevision > 0 && localRevision > 0 && remoteRevision > localRevision) {
          const conflictKey = `${item.id}|${remote.id}|${remote.revisionDate || ''}`;
          if (!conflictKeySet.has(conflictKey)) {
            conflicts.push({
              id: `${item.id}-${remote.id}-${Date.now()}`,
              itemId: item.id,
              bitwardenCipherId: cipherId,
              reason: 'remote_newer_than_local_pending',
              localSnapshot: cloneItem(item),
              serverSnapshot: {
                id: remote.id,
                revisionDate: remote.revisionDate,
                deletedDate: remote.deletedDate,
              },
              createdAt: Date.now(),
            });
            conflictKeySet.add(conflictKey);
            newConflicts += 1;
          }
        }
        continue;
      }

      if (remote.deletedDate && !item.isDeleted) {
        items[i] = {
          ...item,
          isDeleted: true,
          deletedAt: remote.deletedDate,
          updatedAt: remote.revisionDate || item.updatedAt,
        };
        updatedCount += 1;
        continue;
      }

      if (!remote.deletedDate && item.isDeleted) {
        items[i] = {
          ...item,
          isDeleted: false,
          deletedAt: undefined,
          updatedAt: remote.revisionDate || item.updatedAt,
        };
        updatedCount += 1;
      }
    }

    if (updatedCount > 0) {
      await this.saveVaultItems(items);
    }
    if (newConflicts > 0) {
      await this.saveConflicts(conflicts);
    }

    return { updatedCount, newConflicts };
  }

  private countLinkedLocalCiphers(items: SecureItem[]): number {
    return items.filter((item) => {
      if (item.itemType !== ItemType.Password) return false;
      const data = item.itemData as PasswordEntry;
      return !!normalizeCipherId(data.bitwardenCipherId);
    }).length;
  }

  private async runSync(trigger: 'manual' | 'local_mutation' | 'app_resume'): Promise<void> {
    if (this.inFlight) {
      await this.saveStatus({ pending: true });
      return;
    }

    this.inFlight = true;
    await this.saveStatus({ inProgress: true, pending: false, lastTrigger: trigger });

    try {
      const settings = await bitwardenAuthClient.loadSyncSettings();
      if (!settings.autoSyncEnabled && trigger !== 'manual') {
        const existingQueue = await this.loadPendingOperations();
        await this.updateStatusCounters(existingQueue, { inProgress: false, pending: false });
        return;
      }
      if (settings.syncOnWifiOnly && !this.canUseWifiOnlyMode()) {
        const existingQueue = await this.loadPendingOperations();
        await this.updateStatusCounters(existingQueue, {
          inProgress: false,
          pending: false,
          lastError: 'Sync skipped: Wi-Fi required',
        });
        return;
      }

      let session = await bitwardenAuthClient.loadSession();
      if (!session) {
        const existingQueue = await this.loadPendingOperations();
        await this.updateStatusCounters(existingQueue, {
          inProgress: false,
          pending: false,
          lastError: 'Sync skipped: not logged in',
        });
        return;
      }

      if (session.expiresAt <= Date.now() + 60_000) {
        const refreshed = await bitwardenAuthClient.refreshSession(session);
        if (refreshed) {
          session = refreshed;
        }
      }

      const queue = await this.loadPendingOperations();
      const queueResult = await this.processPendingOperations(session, queue);
      await this.savePendingOperations(queueResult.queue);

      const syncResponse = await fetch(`${ensureTrailingSlash(session.serverUrls.api)}sync?excludeDomains=true`, {
        method: 'GET',
        headers: {
          Authorization: `Bearer ${session.accessToken}`,
        },
      });

      if (!syncResponse.ok) {
        await this.updateStatusCounters(queueResult.queue, {
          inProgress: false,
          pending: false,
          lastError: `Sync failed (${syncResponse.status})`,
          emptyVaultProtectionBlocked: false,
        });
        return;
      }

      const syncText = await syncResponse.text();
      let syncPayload: Record<string, unknown> = {};
      if (syncText.trim()) {
        try {
          syncPayload = JSON.parse(syncText) as Record<string, unknown>;
        } catch {
          syncPayload = {};
        }
      }

      const remoteCiphers = this.extractRemoteCiphers(syncPayload);
      const localItems = await this.loadVaultItems();
      const localLinkedCount = this.countLinkedLocalCiphers(localItems);
      const serverCipherCount = remoteCiphers.length;
      const emptyVaultProtectionBlocked = serverCipherCount === 0 && localLinkedCount > 0;

      let lastError = queueResult.firstError;
      if (emptyVaultProtectionBlocked) {
        lastError = `Empty vault protection blocked pull: local=${localLinkedCount}, server=0`;
      } else {
        await this.reconcileRemoteDeletionState(remoteCiphers, queueResult.queue);
      }

      await this.updateStatusCounters(queueResult.queue, {
        inProgress: false,
        pending: false,
        lastSyncAt: Date.now(),
        lastError,
        lastCipherCount: serverCipherCount,
        emptyVaultProtectionBlocked,
        emptyVaultLocalCount: localLinkedCount,
        emptyVaultServerCount: serverCipherCount,
      });
    } catch (e) {
      const queue = await this.loadPendingOperations();
      await this.updateStatusCounters(queue, {
        inProgress: false,
        pending: false,
        lastError: (e as Error).message || 'Sync failed',
        emptyVaultProtectionBlocked: false,
      });
    } finally {
      this.inFlight = false;
    }
  }

  requestLocalMutationSync(): void {
    if (this.timer) clearTimeout(this.timer);
    void this.saveStatus({ pending: true });
    this.timer = setTimeout(() => {
      void this.runSync('local_mutation');
      this.timer = null;
    }, 12_000);
  }

  requestAppResumeSync(): void {
    if (this.timer) clearTimeout(this.timer);
    void this.saveStatus({ pending: true });
    this.timer = setTimeout(() => {
      void this.runSync('app_resume');
      this.timer = null;
    }, 2_000);
  }

  async runManualSync(): Promise<void> {
    await this.runSync('manual');
  }
}

export const bitwardenSyncBridge = new BitwardenSyncBridge();
