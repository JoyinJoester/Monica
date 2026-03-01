import { argon2id } from 'hash-wasm';

export type BitwardenServerPreset = 'us' | 'eu' | 'self_hosted';
export type BitwardenAuthHeaderProfile = 'monica_default' | 'keyguard_fallback';

interface ServerUrls {
  vault: string;
  identity: string;
  api: string;
}

interface PreLoginKdfConfig {
  kdf: number;
  kdfIterations: number;
  kdfMemory?: number;
  kdfParallelism?: number;
}

interface ParsedTokenResponse {
  payload: TokenLikeResponse | null;
  rawBody: string;
}

interface LoginRequestParams {
  serverUrls: ServerUrls;
  email: string;
  passwordHash: string;
  authHeaderProfile: BitwardenAuthHeaderProfile;
  twoFactorCode?: string;
  twoFactorProvider?: number;
  newDeviceOtp?: string;
  captchaResponse?: string;
}

export interface BitwardenSession {
  email: string;
  serverPreset: BitwardenServerPreset;
  serverUrl?: string;
  serverUrls: ServerUrls;
  accessToken: string;
  refreshToken?: string;
  symmetricEncKey?: string;
  symmetricMacKey?: string;
  expiresAt: number;
  createdAt: number;
}

export interface BitwardenSyncSettings {
  autoSyncEnabled: boolean;
  syncOnWifiOnly: boolean;
}

export interface TwoFactorPendingState {
  email: string;
  passwordHash: string;
  tempStretchedEncKey: string;
  tempStretchedMacKey: string;
  serverPreset: BitwardenServerPreset;
  serverUrl?: string;
  serverUrls: ServerUrls;
  providers: number[];
  authHeaderProfile: BitwardenAuthHeaderProfile;
}

export type LoginResult =
  | { status: 'success'; session: BitwardenSession }
  | { status: 'two_factor_required'; pending: TwoFactorPendingState }
  | { status: 'captcha_required'; message: string; siteKey?: string }
  | { status: 'error'; message: string };

interface TokenLikeResponse {
  access_token?: string;
  refresh_token?: string;
  expires_in?: number;
  key?: string;
  Key?: string;
  twoFactorProviders?: number[];
  TwoFactorProviders?: number[];
  twoFactorProviders2?: Record<string, unknown>;
  TwoFactorProviders2?: Record<string, unknown>;
  hCaptchaSiteKey?: string;
  HCaptchaSiteKey?: string;
  HCaptcha_SiteKey?: string;
  hCaptcha_SiteKey?: string;
  error?: string;
  error_description?: string;
  ErrorModel?: { Message?: string; message?: string };
  errorModel?: { Message?: string; message?: string };
}

const OFFICIAL_US = {
  vault: 'https://vault.bitwarden.com',
  identity: 'https://identity.bitwarden.com',
  api: 'https://api.bitwarden.com',
};

const OFFICIAL_EU = {
  vault: 'https://vault.bitwarden.eu',
  identity: 'https://identity.bitwarden.eu',
  api: 'https://api.bitwarden.eu',
};

const STORAGE_KEY_SESSION = 'bitwarden_session_v1';
const STORAGE_KEY_DEVICE_ID = 'bitwarden_device_id_v1';
const STORAGE_KEY_SYNC_SETTINGS = 'bitwarden_sync_settings_v1';

const BITWARDEN_CLIENT_NAME = 'desktop';
const BITWARDEN_CLIENT_VERSION = '2025.9.1';

const KDF_PBKDF2 = 0;
const KDF_ARGON2ID = 1;

export const TWO_FACTOR_EMAIL_NEW_DEVICE = -100;

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

const removeStorage = async (key: string): Promise<void> => {
  if (typeof chrome !== 'undefined' && chrome.storage?.local) {
    await chrome.storage.local.remove(key);
    return;
  }
  localStorage.removeItem(key);
};

const ensureTrailingSlash = (value: string): string => (value.endsWith('/') ? value : `${value}/`);

const isOfficialEuServer = (url: string): boolean => {
  const normalized = url.trim().toLowerCase().replace(/\/+$/, '');
  return normalized === OFFICIAL_EU.vault.toLowerCase() || normalized.includes('bitwarden.eu');
};

const isOfficialUsServer = (url: string): boolean => {
  const normalized = url.trim().toLowerCase().replace(/\/+$/, '');
  return normalized === OFFICIAL_US.vault.toLowerCase() || normalized.includes('bitwarden.com');
};

const inferServerUrls = (preset: BitwardenServerPreset, serverUrl?: string): ServerUrls => {
  if (preset === 'eu') return OFFICIAL_EU;
  if (preset === 'us') return OFFICIAL_US;
  const normalized = (serverUrl || '').trim().replace(/\/+$/, '');
  if (isOfficialEuServer(normalized)) return OFFICIAL_EU;
  if (isOfficialUsServer(normalized)) return OFFICIAL_US;
  return {
    vault: normalized,
    identity: `${normalized}/identity`,
    api: `${normalized}/api`,
  };
};

const bytesToBase64 = (bytes: Uint8Array): string => {
  let binary = '';
  for (let i = 0; i < bytes.length; i += 1) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
};

const toBase64UrlNoPadding = (value: string): string =>
  btoa(unescape(encodeURIComponent(value))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');

const pbkdf2Sha256 = async (
  seedBytes: Uint8Array,
  saltBytes: Uint8Array,
  iterations: number,
  lengthBytes: number
): Promise<Uint8Array> => {
  const seedBuffer = seedBytes.slice().buffer as unknown as BufferSource;
  const saltBuffer = saltBytes.slice().buffer as unknown as BufferSource;
  const baseKey = await crypto.subtle.importKey('raw', seedBuffer, { name: 'PBKDF2' }, false, ['deriveBits']);
  const bits = await crypto.subtle.deriveBits(
    { name: 'PBKDF2', hash: 'SHA-256', salt: saltBuffer, iterations },
    baseKey,
    lengthBytes * 8
  );
  return new Uint8Array(bits);
};

const sha256 = async (input: Uint8Array): Promise<Uint8Array> => {
  const digest = await crypto.subtle.digest('SHA-256', input.slice().buffer as unknown as BufferSource);
  return new Uint8Array(digest);
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

const timingSafeEqual = (a: Uint8Array, b: Uint8Array): boolean => {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i += 1) {
    diff |= a[i] ^ b[i];
  }
  return diff === 0;
};

const hmacSha256 = async (keyBytes: Uint8Array, data: Uint8Array): Promise<Uint8Array> => {
  const key = await crypto.subtle.importKey(
    'raw',
    keyBytes.slice().buffer as unknown as BufferSource,
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign']
  );
  const signature = await crypto.subtle.sign('HMAC', key, data.slice().buffer as unknown as BufferSource);
  return new Uint8Array(signature);
};

const hkdfExpandRaw = async (prk: Uint8Array, info: Uint8Array, length: number): Promise<Uint8Array> => {
  const chunks: Uint8Array<ArrayBufferLike>[] = [];
  let previous: Uint8Array<ArrayBufferLike> = new Uint8Array(0);
  let counter = 1;
  while (chunks.reduce((sum, part) => sum + part.length, 0) < length) {
    const input = concatBytes(previous, info, new Uint8Array([counter]));
    previous = await hmacSha256(prk, input);
    chunks.push(previous);
    counter += 1;
  }
  return concatBytes(...chunks).slice(0, length);
};

const stretchMasterKey = async (masterKey: Uint8Array): Promise<{ encKey: Uint8Array; macKey: Uint8Array }> => {
  const encKey = await hkdfExpandRaw(masterKey, textEncoder.encode('enc'), 32);
  const macKey = await hkdfExpandRaw(masterKey, textEncoder.encode('mac'), 32);
  return { encKey, macKey };
};

const decryptCipherString = async (
  cipherString: string,
  encKey: Uint8Array,
  macKey: Uint8Array
): Promise<Uint8Array> => {
  const dot = cipherString.indexOf('.');
  const type = dot >= 0 ? Number(cipherString.slice(0, dot)) : 0;
  const payload = dot >= 0 ? cipherString.slice(dot + 1) : cipherString;
  const parts = payload.split('|');
  if ((type === 2 && parts.length < 3) || (type !== 2 && parts.length < 2)) {
    throw new Error('Invalid cipher string payload');
  }
  const iv = base64ToBytes(parts[0]);
  const data = base64ToBytes(parts[1]);
  if (type === 2) {
    const mac = base64ToBytes(parts[2]);
    const computed = await hmacSha256(macKey, concatBytes(iv, data));
    if (!timingSafeEqual(computed, mac)) {
      throw new Error('Cipher MAC verification failed');
    }
  }

  const key = await crypto.subtle.importKey(
    'raw',
    encKey.slice().buffer as unknown as BufferSource,
    { name: 'AES-CBC' },
    false,
    ['decrypt']
  );
  const plain = await crypto.subtle.decrypt(
    { name: 'AES-CBC', iv: iv.slice().buffer as unknown as BufferSource },
    key,
    data.slice().buffer as unknown as BufferSource
  );
  return new Uint8Array(plain);
};

const parseTokenResponse = async (response: Response): Promise<ParsedTokenResponse> => {
  const rawBody = await response.text();
  if (!rawBody.trim()) {
    return { payload: null, rawBody };
  }
  try {
    return {
      payload: JSON.parse(rawBody) as TokenLikeResponse,
      rawBody,
    };
  } catch {
    return { payload: null, rawBody };
  }
};

const getErrorModelMessage = (payload: TokenLikeResponse | null): string | undefined =>
  payload?.errorModel?.message ||
  payload?.errorModel?.Message ||
  payload?.ErrorModel?.message ||
  payload?.ErrorModel?.Message;

const normalizeErrorMessage = (payload: TokenLikeResponse | null, fallback: string, rawBody?: string): string => {
  const fromPayload =
    payload?.error_description ||
    getErrorModelMessage(payload) ||
    payload?.error;
  if (fromPayload && fromPayload.trim()) return fromPayload.trim();
  if (rawBody && rawBody.trim()) {
    const plain = rawBody.trim();
    return plain.length > 220 ? `${plain.slice(0, 220)}...` : plain;
  }
  return fallback;
};

const coerceProviderList = (value: unknown): number[] => {
  if (!Array.isArray(value)) return [];
  const mapped = value
    .map((item) => Number(item))
    .filter((item) => Number.isFinite(item))
    .map((item) => Math.trunc(item));
  return Array.from(new Set(mapped));
};

const detectTwoFactorProviders = (payload: TokenLikeResponse | null): number[] => {
  if (!payload) return [];
  const providers = [
    ...coerceProviderList(payload.twoFactorProviders),
    ...coerceProviderList(payload.TwoFactorProviders),
  ];
  return Array.from(new Set(providers));
};

const getHcaptchaSiteKey = (payload: TokenLikeResponse | null): string | undefined =>
  payload?.hCaptchaSiteKey ||
  payload?.HCaptchaSiteKey ||
  payload?.HCaptcha_SiteKey ||
  payload?.hCaptcha_SiteKey ||
  undefined;

const getProtectedSymmetricKey = (payload: TokenLikeResponse | null): string | undefined =>
  payload?.key || payload?.Key || undefined;

const isNewDeviceVerificationRequired = (payload: TokenLikeResponse | null): boolean => {
  const message = getErrorModelMessage(payload);
  return message?.trim().toLowerCase() === 'new device verification required';
};

const isCaptchaRequired = (payload: TokenLikeResponse | null, rawBody: string): boolean => {
  if (getHcaptchaSiteKey(payload)) return true;
  const message = normalizeErrorMessage(payload, '', rawBody);
  return message.toLowerCase().includes('captcha');
};

const shouldRetryWithKeyguardFallback = (
  responseCode: number,
  payload: TokenLikeResponse | null,
  rawBody: string,
  captchaProvided: boolean
): boolean => {
  if (captchaProvided) return false;
  if (responseCode !== 400) return false;
  const error = payload?.error || '';
  const description = payload?.error_description || '';
  const isInvalidGrant = error.toLowerCase() === 'invalid_grant';
  const isInvalidCredDescription = description.toLowerCase() === 'invalid_username_or_password';
  const isInvalidCredBody = rawBody.toLowerCase().includes('invalid_username_or_password');
  return isInvalidGrant && (isInvalidCredDescription || isInvalidCredBody);
};

const normalizePreLogin = (payload: Record<string, unknown>): PreLoginKdfConfig => {
  const kdf = Number(payload.kdf ?? payload.Kdf ?? KDF_PBKDF2);
  const kdfIterations = Number(payload.kdfIterations ?? payload.KdfIterations ?? 600000);
  const kdfMemoryRaw = Number(payload.kdfMemory ?? payload.KdfMemory ?? Number.NaN);
  const kdfParallelismRaw = Number(payload.kdfParallelism ?? payload.KdfParallelism ?? Number.NaN);
  return {
    kdf: Number.isFinite(kdf) ? Math.trunc(kdf) : KDF_PBKDF2,
    kdfIterations: Number.isFinite(kdfIterations) ? Math.max(1, Math.trunc(kdfIterations)) : 600000,
    kdfMemory: Number.isFinite(kdfMemoryRaw) ? Math.max(1, Math.trunc(kdfMemoryRaw)) : undefined,
    kdfParallelism: Number.isFinite(kdfParallelismRaw) ? Math.max(1, Math.trunc(kdfParallelismRaw)) : undefined,
  };
};

class BitwardenAuthClient {
  async loadSession(): Promise<BitwardenSession | null> {
    return readStorage<BitwardenSession>(STORAGE_KEY_SESSION);
  }

  async saveSession(session: BitwardenSession): Promise<void> {
    await writeStorage(STORAGE_KEY_SESSION, session);
  }

  async clearSession(): Promise<void> {
    await removeStorage(STORAGE_KEY_SESSION);
  }

  async loadSyncSettings(): Promise<BitwardenSyncSettings> {
    const stored = await readStorage<Partial<BitwardenSyncSettings>>(STORAGE_KEY_SYNC_SETTINGS);
    return {
      autoSyncEnabled: stored?.autoSyncEnabled === true,
      syncOnWifiOnly: stored?.syncOnWifiOnly === true,
    };
  }

  async saveSyncSettings(settings: BitwardenSyncSettings): Promise<void> {
    await writeStorage(STORAGE_KEY_SYNC_SETTINGS, settings);
  }

  private async getDeviceIdentifier(): Promise<string> {
    const existing = await readStorage<string>(STORAGE_KEY_DEVICE_ID);
    if (existing && existing.length > 0) return existing;
    const newId = typeof crypto !== 'undefined' && crypto.randomUUID
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
    await writeStorage(STORAGE_KEY_DEVICE_ID, newId);
    return newId;
  }

  private async fetchPrelogin(identityUrl: string, email: string): Promise<PreLoginKdfConfig> {
    const response = await fetch(`${ensureTrailingSlash(identityUrl)}accounts/prelogin`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email }),
    });
    if (!response.ok) {
      throw new Error(`Prelogin failed (${response.status})`);
    }
    const payload = (await response.json()) as Record<string, unknown>;
    return normalizePreLogin(payload);
  }

  private async deriveMasterKeyArgon2(params: {
    password: string;
    emailLower: string;
    iterations: number;
    memoryMb: number;
    parallelism: number;
  }): Promise<Uint8Array> {
    const { password, emailLower, iterations, memoryMb, parallelism } = params;
    const saltHash = await sha256(textEncoder.encode(emailLower));
    const output = await argon2id({
      password: textEncoder.encode(password),
      salt: saltHash,
      iterations: Math.max(1, iterations),
      memorySize: Math.max(8, memoryMb) * 1024,
      parallelism: Math.max(1, parallelism),
      hashLength: 32,
      outputType: 'binary',
    });
    return output instanceof Uint8Array ? output : new Uint8Array(output);
  }

  private async buildPasswordHash(
    email: string,
    password: string,
    preLogin: PreLoginKdfConfig
  ): Promise<{ passwordHash: string; masterKey: Uint8Array }> {
    const emailLower = email.trim().toLowerCase();
    const masterKey = preLogin.kdf === KDF_ARGON2ID
      ? await this.deriveMasterKeyArgon2({
        password,
        emailLower,
        iterations: preLogin.kdfIterations,
        memoryMb: preLogin.kdfMemory ?? 64,
        parallelism: preLogin.kdfParallelism ?? 4,
      })
      : await pbkdf2Sha256(
        textEncoder.encode(password),
        textEncoder.encode(emailLower),
        preLogin.kdfIterations,
        32
      );
    const hash = await pbkdf2Sha256(masterKey, textEncoder.encode(password), 1, 32);
    return { passwordHash: bytesToBase64(hash), masterKey };
  }

  private async extractSessionSymmetricKey(
    payload: TokenLikeResponse | null,
    stretchedKey: { encKey: Uint8Array; macKey: Uint8Array }
  ): Promise<{ symmetricEncKey?: string; symmetricMacKey?: string }> {
    const protectedKey = getProtectedSymmetricKey(payload);
    if (!protectedKey) {
      return {};
    }
    const decrypted = await decryptCipherString(protectedKey, stretchedKey.encKey, stretchedKey.macKey);
    if (decrypted.length < 64) {
      throw new Error(`Invalid decrypted symmetric key length: ${decrypted.length}`);
    }
    const symEnc = decrypted.slice(0, 32);
    const symMac = decrypted.slice(32, 64);
    return {
      symmetricEncKey: bytesToBase64(symEnc),
      symmetricMacKey: bytesToBase64(symMac),
    };
  }

  private shouldApplyReferer(profile: BitwardenAuthHeaderProfile, vaultUrl: string): boolean {
    if (profile === 'monica_default') return true;
    return !(isOfficialUsServer(vaultUrl) || isOfficialEuServer(vaultUrl));
  }

  private buildCommonHeaders(email: string): Record<string, string> {
    const headers: Record<string, string> = {
      'Content-Type': 'application/x-www-form-urlencoded',
      'Auth-Email': toBase64UrlNoPadding(email.trim()),
      'device-type': '8',
      'cache-control': 'no-store',
      'Bitwarden-Client-Name': BITWARDEN_CLIENT_NAME,
      'Bitwarden-Client-Version': BITWARDEN_CLIENT_VERSION,
      'Keyguard-Client': '1',
    };
    if (typeof navigator !== 'undefined' && navigator.language) {
      headers['Accept-Language'] = navigator.language;
    }
    return headers;
  }

  private async loginRequest(params: LoginRequestParams): Promise<Response> {
    const {
      serverUrls,
      email,
      passwordHash,
      authHeaderProfile,
      twoFactorCode,
      twoFactorProvider,
      newDeviceOtp,
      captchaResponse,
    } = params;
    const deviceIdentifier = await this.getDeviceIdentifier();
    const form = new URLSearchParams();
    form.set('grant_type', 'password');
    form.set('username', email.trim());
    form.set('password', passwordHash);
    form.set('scope', 'api offline_access');
    form.set('client_id', 'desktop');
    form.set('deviceIdentifier', deviceIdentifier);
    form.set('deviceType', '8');
    form.set('deviceName', 'linux');
    if (captchaResponse) form.set('captchaResponse', captchaResponse.trim());
    if (newDeviceOtp) {
      form.set('newDeviceOtp', newDeviceOtp.trim());
    } else if (twoFactorCode && typeof twoFactorProvider === 'number') {
      form.set('twoFactorToken', twoFactorCode.trim());
      form.set('twoFactorProvider', String(twoFactorProvider));
      form.set('twoFactorRemember', '1');
    }

    const requestInit: RequestInit = {
      method: 'POST',
      headers: this.buildCommonHeaders(email),
      body: form.toString(),
      cache: 'no-store',
      credentials: 'omit',
    };

    if (this.shouldApplyReferer(authHeaderProfile, serverUrls.vault)) {
      try {
        requestInit.referrer = ensureTrailingSlash(serverUrls.vault);
      } catch {
        // Browser may reject explicit referrer in extension context.
      }
    }

    return fetch(`${ensureTrailingSlash(serverUrls.identity)}connect/token`, requestInit);
  }

  private createSession(params: {
    email: string;
    preset: BitwardenServerPreset;
    serverUrl?: string;
    serverUrls: ServerUrls;
    payload: TokenLikeResponse;
    symmetricEncKey?: string;
    symmetricMacKey?: string;
  }): BitwardenSession {
    const { email, preset, serverUrl, serverUrls, payload, symmetricEncKey, symmetricMacKey } = params;
    return {
      email,
      serverPreset: preset,
      serverUrl,
      serverUrls,
      accessToken: payload.access_token || '',
      refreshToken: payload.refresh_token,
      symmetricEncKey,
      symmetricMacKey,
      expiresAt: Date.now() + (payload.expires_in || 3600) * 1000,
      createdAt: Date.now(),
    };
  }

  private buildTwoFactorPending(params: {
    email: string;
    passwordHash: string;
    tempStretchedEncKey: string;
    tempStretchedMacKey: string;
    preset: BitwardenServerPreset;
    serverUrl?: string;
    serverUrls: ServerUrls;
    providers: number[];
    authHeaderProfile: BitwardenAuthHeaderProfile;
  }): TwoFactorPendingState {
    return {
      email: params.email,
      passwordHash: params.passwordHash,
      tempStretchedEncKey: params.tempStretchedEncKey,
      tempStretchedMacKey: params.tempStretchedMacKey,
      serverPreset: params.preset,
      serverUrl: params.serverUrl,
      serverUrls: params.serverUrls,
      providers: params.providers,
      authHeaderProfile: params.authHeaderProfile,
    };
  }

  private async resolvePrimaryLoginResult(params: {
    parsed: ParsedTokenResponse;
    response: Response;
    email: string;
    passwordHash: string;
    stretchedKey: { encKey: Uint8Array; macKey: Uint8Array };
    preset: BitwardenServerPreset;
    serverUrl?: string;
    serverUrls: ServerUrls;
    authHeaderProfile: BitwardenAuthHeaderProfile;
  }): Promise<LoginResult> {
    const {
      parsed,
      response,
      email,
      passwordHash,
      stretchedKey,
      preset,
      serverUrl,
      serverUrls,
      authHeaderProfile,
    } = params;
    const payload = parsed.payload;
    const providers = detectTwoFactorProviders(payload);

    if (response.ok && payload?.access_token) {
      const sessionKey = await this.extractSessionSymmetricKey(payload, stretchedKey);
      const session = this.createSession({
        email,
        preset,
        serverUrl,
        serverUrls,
        payload,
        symmetricEncKey: sessionKey.symmetricEncKey,
        symmetricMacKey: sessionKey.symmetricMacKey,
      });
      await this.saveSession(session);
      return { status: 'success', session };
    }

    if (providers.length > 0) {
      return {
        status: 'two_factor_required',
        pending: this.buildTwoFactorPending({
          email,
          passwordHash,
          tempStretchedEncKey: bytesToBase64(stretchedKey.encKey),
          tempStretchedMacKey: bytesToBase64(stretchedKey.macKey),
          preset,
          serverUrl,
          serverUrls,
          providers,
          authHeaderProfile,
        }),
      };
    }

    if (isNewDeviceVerificationRequired(payload)) {
      return {
        status: 'two_factor_required',
        pending: this.buildTwoFactorPending({
          email,
          passwordHash,
          tempStretchedEncKey: bytesToBase64(stretchedKey.encKey),
          tempStretchedMacKey: bytesToBase64(stretchedKey.macKey),
          preset,
          serverUrl,
          serverUrls,
          providers: [TWO_FACTOR_EMAIL_NEW_DEVICE],
          authHeaderProfile,
        }),
      };
    }

    if (isCaptchaRequired(payload, parsed.rawBody)) {
      return {
        status: 'captcha_required',
        message: normalizeErrorMessage(payload, 'Captcha required', parsed.rawBody),
        siteKey: getHcaptchaSiteKey(payload),
      };
    }

    return {
      status: 'error',
      message: normalizeErrorMessage(payload, `Login failed (${response.status})`, parsed.rawBody),
    };
  }

  private async resolveTwoFactorLoginResult(params: {
    parsed: ParsedTokenResponse;
    response: Response;
    pending: TwoFactorPendingState;
    fallbackMessage: string;
  }): Promise<LoginResult> {
    const { parsed, response, pending, fallbackMessage } = params;
    const payload = parsed.payload;
    const stretchedKey = {
      encKey: base64ToBytes(pending.tempStretchedEncKey),
      macKey: base64ToBytes(pending.tempStretchedMacKey),
    };

    if (response.ok && payload?.access_token) {
      const sessionKey = await this.extractSessionSymmetricKey(payload, stretchedKey);
      const session = this.createSession({
        email: pending.email,
        preset: pending.serverPreset,
        serverUrl: pending.serverUrl,
        serverUrls: pending.serverUrls,
        payload,
        symmetricEncKey: sessionKey.symmetricEncKey,
        symmetricMacKey: sessionKey.symmetricMacKey,
      });
      await this.saveSession(session);
      return { status: 'success', session };
    }

    const providers = detectTwoFactorProviders(payload);
    if (providers.length > 0) {
      return {
        status: 'two_factor_required',
        pending: {
          ...pending,
          providers,
        },
      };
    }

    if (isNewDeviceVerificationRequired(payload)) {
      return {
        status: 'two_factor_required',
        pending: {
          ...pending,
          providers: [TWO_FACTOR_EMAIL_NEW_DEVICE],
        },
      };
    }

    if (isCaptchaRequired(payload, parsed.rawBody)) {
      return {
        status: 'captcha_required',
        message: normalizeErrorMessage(payload, 'Captcha required', parsed.rawBody),
        siteKey: getHcaptchaSiteKey(payload),
      };
    }

    return {
      status: 'error',
      message: normalizeErrorMessage(payload, fallbackMessage, parsed.rawBody),
    };
  }

  async login(params: {
    preset: BitwardenServerPreset;
    serverUrl?: string;
    email: string;
    password: string;
    captchaResponse?: string;
  }): Promise<LoginResult> {
    const email = params.email.trim();
    const serverUrl = params.serverUrl?.trim();
    const serverUrls = inferServerUrls(params.preset, serverUrl);
    const normalizedCaptcha = params.captchaResponse?.trim() || undefined;
    if (!email || !params.password) {
      return { status: 'error', message: 'Email and master password are required' };
    }
    if (params.preset === 'self_hosted' && !serverUrl) {
      return { status: 'error', message: 'Self-hosted server URL is required' };
    }

    try {
      const preLogin = await this.fetchPrelogin(serverUrls.identity, email);
      const derived = await this.buildPasswordHash(email, params.password, preLogin);
      const passwordHash = derived.passwordHash;
      const stretchedKey = await stretchMasterKey(derived.masterKey);

      const primaryResponse = await this.loginRequest({
        serverUrls,
        email,
        passwordHash,
        captchaResponse: normalizedCaptcha,
        authHeaderProfile: 'monica_default',
      });
      const primaryParsed = await parseTokenResponse(primaryResponse);
      const primaryResult = await this.resolvePrimaryLoginResult({
        parsed: primaryParsed,
        response: primaryResponse,
        email,
        passwordHash,
        stretchedKey,
        preset: params.preset,
        serverUrl,
        serverUrls,
        authHeaderProfile: 'monica_default',
      });

      if (primaryResult.status !== 'error') {
        return primaryResult;
      }

      const shouldRetry = shouldRetryWithKeyguardFallback(
        primaryResponse.status,
        primaryParsed.payload,
        primaryParsed.rawBody,
        !!normalizedCaptcha
      );
      if (!shouldRetry) return primaryResult;

      const retryResponse = await this.loginRequest({
        serverUrls,
        email,
        passwordHash,
        captchaResponse: normalizedCaptcha,
        authHeaderProfile: 'keyguard_fallback',
      });
      const retryParsed = await parseTokenResponse(retryResponse);
      return this.resolvePrimaryLoginResult({
        parsed: retryParsed,
        response: retryResponse,
        email,
        passwordHash,
        stretchedKey,
        preset: params.preset,
        serverUrl,
        serverUrls,
        authHeaderProfile: 'keyguard_fallback',
      });
    } catch (e) {
      return { status: 'error', message: (e as Error).message || 'Login failed' };
    }
  }

  async loginWithTwoFactor(params: {
    pending: TwoFactorPendingState;
    code: string;
    provider: number;
    captchaResponse?: string;
  }): Promise<LoginResult> {
    const { pending, code, provider } = params;
    const trimmedCode = code.trim();
    const normalizedCaptcha = params.captchaResponse?.trim() || undefined;
    if (!trimmedCode) {
      return { status: 'error', message: 'Two-factor code is required' };
    }

    try {
      const isNewDeviceOtp = provider === TWO_FACTOR_EMAIL_NEW_DEVICE;
      const response = await this.loginRequest({
        serverUrls: pending.serverUrls,
        email: pending.email,
        passwordHash: pending.passwordHash,
        authHeaderProfile: pending.authHeaderProfile || 'monica_default',
        newDeviceOtp: isNewDeviceOtp ? trimmedCode : undefined,
        twoFactorCode: isNewDeviceOtp ? undefined : trimmedCode,
        twoFactorProvider: isNewDeviceOtp ? undefined : provider,
        captchaResponse: normalizedCaptcha,
      });
      const parsed = await parseTokenResponse(response);
      return this.resolveTwoFactorLoginResult({
        parsed,
        response,
        pending,
        fallbackMessage: isNewDeviceOtp
          ? `New device verification failed (${response.status})`
          : `Two-factor login failed (${response.status})`,
      });
    } catch (e) {
      return {
        status: 'error',
        message: (e as Error).message || 'Two-factor login failed',
      };
    }
  }

  async verifySession(session: BitwardenSession): Promise<boolean> {
    try {
      const response = await fetch(`${ensureTrailingSlash(session.serverUrls.api)}sync?excludeDomains=true`, {
        method: 'GET',
        headers: {
          Authorization: `Bearer ${session.accessToken}`,
        },
      });
      return response.ok;
    } catch {
      return false;
    }
  }

  async refreshSession(session: BitwardenSession): Promise<BitwardenSession | null> {
    if (!session.refreshToken) return null;
    try {
      const form = new URLSearchParams();
      form.set('grant_type', 'refresh_token');
      form.set('refresh_token', session.refreshToken);
      form.set('client_id', 'desktop');

      const response = await fetch(`${ensureTrailingSlash(session.serverUrls.identity)}connect/token`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          'Bitwarden-Client-Name': BITWARDEN_CLIENT_NAME,
          'Bitwarden-Client-Version': BITWARDEN_CLIENT_VERSION,
          'Keyguard-Client': '1',
        },
        body: form.toString(),
      });
      const parsed = await parseTokenResponse(response);
      if (!response.ok || !parsed.payload?.access_token) return null;

      const refreshed: BitwardenSession = {
        ...session,
        accessToken: parsed.payload.access_token,
        refreshToken: parsed.payload.refresh_token || session.refreshToken,
        expiresAt: Date.now() + (parsed.payload.expires_in || 3600) * 1000,
      };
      await this.saveSession(refreshed);
      return refreshed;
    } catch {
      return null;
    }
  }
}

export const bitwardenAuthClient = new BitwardenAuthClient();
