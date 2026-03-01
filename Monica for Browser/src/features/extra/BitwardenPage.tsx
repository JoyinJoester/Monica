import { useEffect, useMemo, useState } from 'react';
import styled from 'styled-components';
import { useTranslation } from 'react-i18next';
import { AlertCircle, CheckCircle2, LogOut, Shield, ShieldCheck } from 'lucide-react';
import { Button } from '../../components/common/Button';
import { Input } from '../../components/common/Input';
import {
  bitwardenAuthClient,
  type BitwardenServerPreset,
  type BitwardenSession,
  type BitwardenSyncSettings,
  TWO_FACTOR_EMAIL_NEW_DEVICE,
  type TwoFactorPendingState,
} from '../../utils/bitwarden/BitwardenAuthClient';
import { bitwardenSyncBridge, type BitwardenSyncStatus } from '../../utils/bitwarden/BitwardenSyncBridge';

const Container = styled.div`
  padding: 22px 26px 120px;
  max-width: 980px;
  margin: 0 auto;
`;

const Card = styled.div`
  background: ${({ theme }) => theme.colors.surfaceVariant};
  border: 1px solid ${({ theme }) => theme.colors.outlineVariant};
  border-radius: 16px;
  padding: 16px;
  margin-bottom: 14px;
`;

const Title = styled.h2`
  margin: 0;
  font-size: 18px;
  display: flex;
  align-items: center;
  gap: 8px;
`;

const Desc = styled.div`
  margin-top: 8px;
  opacity: 0.8;
  font-size: 13px;
`;

const Row = styled.div`
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
`;

const Select = styled.select`
  min-height: 42px;
  border-radius: 12px;
  border: 1px solid ${({ theme }) => theme.colors.outlineVariant};
  background: ${({ theme }) => theme.colors.surface};
  color: ${({ theme }) => theme.colors.onSurface};
  padding: 0 12px;
  min-width: 180px;
`;

const Badge = styled.span<{ $ok?: boolean }>`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 12px;
  background: ${({ $ok }) => ($ok ? 'rgba(46,125,50,.2)' : 'rgba(180,40,40,.2)')};
  color: ${({ $ok }) => ($ok ? '#7be0a4' : '#ff9f9f')};
`;

const ErrorText = styled.div`
  margin-top: 10px;
  color: #ff9f9f;
  font-size: 13px;
  display: flex;
  gap: 8px;
  align-items: center;
`;

const ToggleRow = styled.label`
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  border: 1px solid ${({ theme }) => theme.colors.outlineVariant};
  border-radius: 12px;
  padding: 10px 12px;
  margin-top: 8px;
  cursor: pointer;
  transition: all 0.15s ease;
  &:hover {
    background: ${({ theme }) => theme.colors.surfaceVariant};
  }
  &:active {
    transform: scale(0.98);
    opacity: 0.85;
  }
`;

const HelperText = styled.div`
  margin-top: 8px;
  color: ${({ theme }) => theme.colors.onSurfaceVariant};
  font-size: 12px;
  line-height: 1.4;
`;

const providerName = (provider: number, isZh: boolean): string => {
  const names: Record<number, string> = isZh
    ? {
      0: '验证器应用 (TOTP)',
      1: '邮箱验证码',
      2: 'Duo',
      3: 'YubiKey',
      4: 'U2F 安全密钥',
      6: '组织 Duo',
      7: 'WebAuthn',
      [-100]: '新设备邮箱验证',
    }
    : {
      0: 'Authenticator App (TOTP)',
      1: 'Email Code',
      2: 'Duo',
      3: 'YubiKey',
      4: 'U2F Security Key',
      6: 'Organization Duo',
      7: 'WebAuthn',
      [-100]: 'Email New Device OTP',
    };
  return names[provider] || `${isZh ? '未知方式' : 'Unknown Method'} (${provider})`;
};

export const BitwardenPage = () => {
  const { i18n } = useTranslation();
  const isZh = i18n.language.startsWith('zh');

  const [serverPreset, setServerPreset] = useState<BitwardenServerPreset>('us');
  const [customServerUrl, setCustomServerUrl] = useState('');
  const [email, setEmail] = useState('');
  const [masterPassword, setMasterPassword] = useState('');
  const [twoFactorCode, setTwoFactorCode] = useState('');
  const [twoFactorProvider, setTwoFactorProvider] = useState<number>(0);
  const [pending, setPending] = useState<TwoFactorPendingState | null>(null);
  const [session, setSession] = useState<BitwardenSession | null>(null);
  const [sessionValid, setSessionValid] = useState<boolean | null>(null);
  const [syncSettings, setSyncSettings] = useState<BitwardenSyncSettings>({
    autoSyncEnabled: false,
    syncOnWifiOnly: false,
  });
  const [syncStatus, setSyncStatus] = useState<BitwardenSyncStatus | null>(null);
  const [captchaResponse, setCaptchaResponse] = useState('');
  const [captchaMessage, setCaptchaMessage] = useState('');
  const [captchaSiteKey, setCaptchaSiteKey] = useState<string | undefined>();
  const [captchaForTwoFactor, setCaptchaForTwoFactor] = useState(false);
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const canSubmit = useMemo(() => {
    if (!email.trim() || !masterPassword) return false;
    if (serverPreset === 'self_hosted' && !customServerUrl.trim()) return false;
    return true;
  }, [customServerUrl, email, masterPassword, serverPreset]);

  useEffect(() => {
    const bootstrap = async () => {
      const settings = await bitwardenAuthClient.loadSyncSettings();
      setSyncSettings(settings);
      const status = await bitwardenSyncBridge.loadStatus();
      setSyncStatus(status);

      const stored = await bitwardenAuthClient.loadSession();
      let active = stored;
      if (active && active.expiresAt <= Date.now() + 60_000) {
        const refreshed = await bitwardenAuthClient.refreshSession(active);
        active = refreshed || active;
      }
      setSession(active);
      if (active) {
        const ok = await bitwardenAuthClient.verifySession(active);
        setSessionValid(ok);
      }
    };
    void bootstrap();
  }, []);

  useEffect(() => {
    const timer = setInterval(() => {
      void bitwardenSyncBridge.loadStatus().then(setSyncStatus);
    }, 3000);
    return () => clearInterval(timer);
  }, []);

  const updateSyncSettings = async (next: BitwardenSyncSettings) => {
    setSyncSettings(next);
    await bitwardenAuthClient.saveSyncSettings(next);
  };

  const clearCaptchaState = () => {
    setCaptchaResponse('');
    setCaptchaMessage('');
    setCaptchaSiteKey(undefined);
    setCaptchaForTwoFactor(false);
  };

  const applyCaptchaChallenge = (message: string, siteKey?: string, forTwoFactor = false) => {
    setCaptchaMessage(message);
    setCaptchaSiteKey(siteKey);
    setCaptchaForTwoFactor(forTwoFactor);
  };

  const handleLogin = async () => {
    setError('');
    setIsLoading(true);
    const result = await bitwardenAuthClient.login({
      preset: serverPreset,
      serverUrl: customServerUrl,
      email,
      password: masterPassword,
      captchaResponse: captchaForTwoFactor ? undefined : (captchaResponse.trim() || undefined),
    });
    setIsLoading(false);

    if (result.status === 'success') {
      setSession(result.session);
      setSessionValid(true);
      setPending(null);
      setTwoFactorCode('');
      clearCaptchaState();
      return;
    }
    if (result.status === 'two_factor_required') {
      setPending(result.pending);
      setTwoFactorProvider(result.pending.providers[0] ?? 0);
      clearCaptchaState();
      return;
    }
    if (result.status === 'captcha_required') {
      applyCaptchaChallenge(result.message, result.siteKey, false);
      setError('');
      return;
    }
    clearCaptchaState();
    setError(result.message);
  };

  const handleTwoFactorLogin = async () => {
    if (!pending) return;
    setError('');
    setIsLoading(true);
    const result = await bitwardenAuthClient.loginWithTwoFactor({
      pending,
      code: twoFactorCode,
      provider: twoFactorProvider,
      captchaResponse: captchaForTwoFactor ? (captchaResponse.trim() || undefined) : undefined,
    });
    setIsLoading(false);

    if (result.status === 'success') {
      setSession(result.session);
      setSessionValid(true);
      setPending(null);
      setTwoFactorCode('');
      clearCaptchaState();
      return;
    }
    if (result.status === 'captcha_required') {
      applyCaptchaChallenge(result.message, result.siteKey, true);
      setError('');
      return;
    }
    if (result.status === 'two_factor_required') {
      setPending(result.pending);
      setTwoFactorProvider(result.pending.providers[0] ?? 0);
      clearCaptchaState();
      return;
    }
    clearCaptchaState();
    setError(result.message);
  };

  const handleLogout = async () => {
    await bitwardenAuthClient.clearSession();
    setSession(null);
    setSessionValid(null);
    setPending(null);
    setTwoFactorCode('');
    clearCaptchaState();
    setError('');
  };

  const handleRefreshToken = async () => {
    if (!session) return;
    setError('');
    setIsLoading(true);
    const refreshed = await bitwardenAuthClient.refreshSession(session);
    setIsLoading(false);
    if (!refreshed) {
      setError(isZh ? '刷新 Token 失败，请重新登录。' : 'Refresh token failed. Please login again.');
      return;
    }
    setSession(refreshed);
    const ok = await bitwardenAuthClient.verifySession(refreshed);
    setSessionValid(ok);
  };

  const handleManualSync = async () => {
    setError('');
    setIsLoading(true);
    await bitwardenSyncBridge.runManualSync();
    const status = await bitwardenSyncBridge.loadStatus();
    setSyncStatus(status);
    setIsLoading(false);
    if (status?.lastError) {
      setError(status.lastError);
    }
  };

  const isNewDeviceOtpFlow = !!pending && twoFactorProvider === TWO_FACTOR_EMAIL_NEW_DEVICE;

  return (
    <Container>
      <Card>
        <Title><ShieldCheck size={18} />Bitwarden</Title>
        <Desc>
          {isZh
            ? '对齐 Android：支持官方/欧盟/自托管服务器登录，支持两步验证（TOTP/邮箱等）流程。'
            : 'Android-aligned entry: supports official/EU/self-hosted login and two-factor flow (TOTP/Email/etc.).'}
        </Desc>
      </Card>

      {session ? (
        <Card>
          <Row style={{ justifyContent: 'space-between' }}>
            <div>
              <div style={{ fontWeight: 600 }}>{session.email}</div>
              <div style={{ fontSize: 12, opacity: 0.75, marginTop: 2 }}>{session.serverUrls.vault}</div>
              <div style={{ fontSize: 12, opacity: 0.75, marginTop: 2 }}>
                {isZh ? 'Token 到期' : 'Token Expiry'}: {new Date(session.expiresAt).toLocaleString()}
              </div>
              <div style={{ fontSize: 12, opacity: 0.75, marginTop: 2 }}>
                {isZh ? '上次同步' : 'Last Sync'}:{' '}
                {syncStatus?.lastSyncAt ? new Date(syncStatus.lastSyncAt).toLocaleString() : (isZh ? '暂无' : 'N/A')}
                {typeof syncStatus?.lastCipherCount === 'number'
                  ? ` · ${isZh ? '条目' : 'Ciphers'}: ${syncStatus.lastCipherCount}`
                  : ''}
              </div>
              <div style={{ fontSize: 12, opacity: 0.75, marginTop: 2 }}>
                {isZh ? '同步队列' : 'Sync Queue'}:{' '}
                {`${isZh ? '待处理' : 'Pending'} ${syncStatus?.pendingQueueCount ?? 0} · `}
                {`${isZh ? '失败' : 'Failed'} ${syncStatus?.failedQueueCount ?? 0} · `}
                {`${isZh ? '冲突' : 'Conflicts'} ${syncStatus?.conflictCount ?? 0}`}
              </div>
              {syncStatus?.emptyVaultProtectionBlocked && (
                <div style={{ fontSize: 12, color: '#ffb3b3', marginTop: 4 }}>
                  {isZh
                    ? `空库保护已触发：本地 ${syncStatus.emptyVaultLocalCount ?? 0}，服务端 ${syncStatus.emptyVaultServerCount ?? 0}`
                    : `Empty-vault protection: local ${syncStatus.emptyVaultLocalCount ?? 0}, server ${syncStatus.emptyVaultServerCount ?? 0}`}
                </div>
              )}
            </div>
            <Badge $ok={!!sessionValid}>
              {sessionValid ? <CheckCircle2 size={14} /> : <AlertCircle size={14} />}
              {sessionValid == null
                ? (isZh ? '检测中' : 'Checking')
                : sessionValid
                  ? (isZh ? '已连接' : 'Connected')
                  : (isZh ? '令牌失效' : 'Token Expired')}
            </Badge>
          </Row>
          <ToggleRow>
            <span>{isZh ? '自动同步' : 'Auto Sync'}</span>
            <input
              type="checkbox"
              checked={syncSettings.autoSyncEnabled}
              onChange={(e) =>
                void updateSyncSettings({
                  autoSyncEnabled: e.target.checked,
                  syncOnWifiOnly: e.target.checked ? syncSettings.syncOnWifiOnly : false,
                })
              }
            />
          </ToggleRow>
          <ToggleRow>
            <span>{isZh ? '仅 Wi-Fi 同步' : 'Sync On Wi-Fi Only'}</span>
            <input
              type="checkbox"
              checked={syncSettings.syncOnWifiOnly}
              disabled={!syncSettings.autoSyncEnabled}
              onChange={(e) =>
                void updateSyncSettings({
                  ...syncSettings,
                  syncOnWifiOnly: e.target.checked,
                })
              }
            />
          </ToggleRow>
          <Row style={{ marginTop: 14 }}>
            <Button variant="secondary" onClick={handleManualSync} disabled={isLoading || syncStatus?.inProgress}>
              {syncStatus?.inProgress
                ? (isZh ? '同步中...' : 'Syncing...')
                : (isZh ? '立即同步' : 'Sync Now')}
            </Button>
            <Button variant="secondary" onClick={handleRefreshToken} disabled={isLoading}>
              {isLoading ? (isZh ? '刷新中...' : 'Refreshing...') : (isZh ? '刷新 Token' : 'Refresh Token')}
            </Button>
            <Button variant="secondary" onClick={handleLogout}>
              <LogOut size={14} />
              {isZh ? '登出 Bitwarden' : 'Logout Bitwarden'}
            </Button>
          </Row>
        </Card>
      ) : (
        <Card>
          <Row>
            <Select value={serverPreset} onChange={(e) => setServerPreset(e.target.value as BitwardenServerPreset)}>
              <option value="us">{isZh ? '美国官方' : 'Official US'}</option>
              <option value="eu">{isZh ? '欧洲官方' : 'Official EU'}</option>
              <option value="self_hosted">{isZh ? '自托管' : 'Self Hosted'}</option>
            </Select>
          </Row>
          {serverPreset === 'self_hosted' && (
            <Input
              label={isZh ? '自托管服务器 URL' : 'Self-hosted Server URL'}
              placeholder="https://vault.example.com"
              value={customServerUrl}
              onChange={(e) => setCustomServerUrl(e.target.value)}
            />
          )}
          <Input
            label={isZh ? '邮箱' : 'Email'}
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
          <Input
            label={isZh ? '主密码' : 'Master Password'}
            type="password"
            value={masterPassword}
            onChange={(e) => setMasterPassword(e.target.value)}
          />
          {!captchaForTwoFactor && captchaMessage && (
            <>
              <Input
                label={isZh ? 'Captcha Response' : 'Captcha Response'}
                value={captchaResponse}
                onChange={(e) => setCaptchaResponse(e.target.value)}
                placeholder={isZh ? '粘贴 hCaptcha token' : 'Paste hCaptcha token'}
              />
              <HelperText>
                {captchaMessage}
                {captchaSiteKey ? ` · SiteKey: ${captchaSiteKey}` : ''}
              </HelperText>
            </>
          )}
          <Row>
            <Button onClick={handleLogin} disabled={!canSubmit || isLoading}>
              <Shield size={14} />
              {isLoading ? (isZh ? '登录中...' : 'Signing in...') : (isZh ? '登录 Bitwarden' : 'Login Bitwarden')}
            </Button>
          </Row>
        </Card>
      )}

      {!session && pending && (
        <Card>
          <Title><Shield size={16} />{isZh ? '两步验证' : 'Two-Factor Authentication'}</Title>
          <Desc>
            {isNewDeviceOtpFlow
              ? (isZh
                ? '检测到新设备验证，请输入邮箱收到的 New Device OTP。'
                : 'New device verification is required. Enter the OTP from your email.')
              : (isZh ? '请选择验证方式并输入验证码。' : 'Select provider and input verification code.')}
          </Desc>
          <Row style={{ marginTop: 10 }}>
            <Select value={String(twoFactorProvider)} onChange={(e) => setTwoFactorProvider(parseInt(e.target.value, 10))}>
              {pending.providers.map((provider) => (
                <option key={provider} value={provider}>{providerName(provider, isZh)}</option>
              ))}
            </Select>
          </Row>
          <Input
            label={isNewDeviceOtpFlow
              ? (isZh ? '新设备验证码 (OTP)' : 'New Device OTP')
              : (isZh ? '验证码' : 'Verification Code')}
            value={twoFactorCode}
            onChange={(e) => setTwoFactorCode(e.target.value)}
          />
          {captchaForTwoFactor && captchaMessage && (
            <>
              <Input
                label={isZh ? 'Captcha Response' : 'Captcha Response'}
                value={captchaResponse}
                onChange={(e) => setCaptchaResponse(e.target.value)}
                placeholder={isZh ? '粘贴 hCaptcha token' : 'Paste hCaptcha token'}
              />
              <HelperText>
                {captchaMessage}
                {captchaSiteKey ? ` · SiteKey: ${captchaSiteKey}` : ''}
              </HelperText>
            </>
          )}
          <Row>
            <Button onClick={handleTwoFactorLogin} disabled={!twoFactorCode.trim() || isLoading}>
              {isLoading ? (isZh ? '验证中...' : 'Verifying...') : (isZh ? '完成验证' : 'Verify')}
            </Button>
            <Button
              variant="text"
              onClick={() => {
                setPending(null);
                setTwoFactorCode('');
                clearCaptchaState();
              }}
            >
              {isZh ? '取消' : 'Cancel'}
            </Button>
          </Row>
        </Card>
      )}

      {error && (
        <ErrorText>
          <AlertCircle size={14} />
          {error}
        </ErrorText>
      )}
      {!error && syncStatus?.lastError && (
        <ErrorText>
          <AlertCircle size={14} />
          {syncStatus.lastError}
        </ErrorText>
      )}
    </Container>
  );
};
