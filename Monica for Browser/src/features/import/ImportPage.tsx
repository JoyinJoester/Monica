import { useState, useCallback } from 'react';
import styled from 'styled-components';
import { useTranslation } from 'react-i18next';
import { ArrowLeft, Upload, FileText, Check, AlertCircle, Loader2 } from 'lucide-react';
import { ItemType, OtpType } from '../../types/models';
import type { SecureItem, PasswordEntry, TotpData } from '../../types/models';

// ========== Types ==========
const ImportFormat = {
    AUTO: 'auto',
    CHROME_PASSWORDS: 'chrome',
    MONICA_CSV: 'monica',
    AEGIS_TOTP: 'aegis',
} as const;

type ImportFormatType = typeof ImportFormat[keyof typeof ImportFormat];

// ========== Styled Components ==========
const Container = styled.div`
  padding: 16px;
  max-width: 480px;
  margin: 0 auto;
`;

const Header = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
`;

const BackButton = styled.button`
  background: none;
  border: none;
  cursor: pointer;
  padding: 8px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: ${({ theme }) => theme.colors.onSurface};
  &:hover {
    background: ${({ theme }) => theme.colors.surfaceVariant};
  }
`;

const Title = styled.h2`
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  flex: 1;
`;

const Section = styled.div`
  margin-bottom: 24px;
`;

const SectionTitle = styled.h3`
  font-size: 14px;
  font-weight: 600;
  color: ${({ theme }) => theme.colors.primary};
  margin: 0 0 12px 0;
`;

const FormatSelect = styled.select`
  width: 100%;
  padding: 12px;
  border: 1px solid ${({ theme }) => theme.colors.outline};
  border-radius: 12px;
  background: ${({ theme }) => theme.colors.surface};
  color: ${({ theme }) => theme.colors.onSurface};
  font-size: 14px;
  cursor: pointer;
  &:focus {
    outline: none;
    border-color: ${({ theme }) => theme.colors.primary};
  }
`;

const DropZone = styled.div<{ $isDragging?: boolean }>`
  border: 2px dashed ${({ theme, $isDragging }) =>
        $isDragging ? theme.colors.primary : theme.colors.outline};
  border-radius: 16px;
  padding: 32px;
  text-align: center;
  background: ${({ theme, $isDragging }) =>
        $isDragging ? theme.colors.primaryContainer : theme.colors.surfaceVariant};
  transition: all 0.2s ease;
  cursor: pointer;
  
  &:hover {
    border-color: ${({ theme }) => theme.colors.primary};
    background: ${({ theme }) => theme.colors.primaryContainer};
  }
`;

const DropIcon = styled.div`
  margin-bottom: 12px;
  color: ${({ theme }) => theme.colors.primary};
`;

const DropText = styled.p`
  margin: 0;
  color: ${({ theme }) => theme.colors.onSurfaceVariant};
  font-size: 14px;
`;

const HiddenInput = styled.input`
  display: none;
`;

const ResultCard = styled.div<{ $success?: boolean }>`
  background: ${({ theme, $success }) =>
        $success ? theme.colors.primaryContainer : theme.colors.errorContainer};
  border-radius: 16px;
  padding: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
`;

const ResultIcon = styled.div<{ $success?: boolean }>`
  color: ${({ theme, $success }) =>
        $success ? theme.colors.primary : theme.colors.error};
`;

const ResultText = styled.div`
  flex: 1;
`;

const ResultTitle = styled.div`
  font-weight: 600;
  margin-bottom: 4px;
`;

const ResultDetails = styled.div`
  font-size: 12px;
  color: ${({ theme }) => theme.colors.onSurfaceVariant};
`;

// ========== Import Logic ==========
function parseCSV(content: string): Record<string, string>[] {
    const lines = content.trim().split(/\r?\n/);
    if (lines.length < 2) return [];

    const headers = parseCSVLine(lines[0]);
    const items: Record<string, string>[] = [];

    for (let i = 1; i < lines.length; i++) {
        const values = parseCSVLine(lines[i]);
        const item: Record<string, string> = {};
        headers.forEach((header, index) => {
            item[header] = values[index] || '';
        });
        items.push(item);
    }

    return items;
}

function parseCSVLine(line: string): string[] {
    const result: string[] = [];
    let current = '';
    let inQuotes = false;

    for (let i = 0; i < line.length; i++) {
        const char = line[i];
        if (char === '"') {
            if (inQuotes && line[i + 1] === '"') {
                current += '"';
                i++;
            } else {
                inQuotes = !inQuotes;
            }
        } else if (char === ',' && !inQuotes) {
            result.push(current);
            current = '';
        } else {
            current += char;
        }
    }
    result.push(current);
    return result;
}

function detectFormat(content: string, filename: string): ImportFormatType {
    const lowerFilename = filename.toLowerCase();

    if (lowerFilename.endsWith('.json')) {
        try {
            const json = JSON.parse(content);
            if (json.db && json.db.entries) {
                return ImportFormat.AEGIS_TOTP;
            }
        } catch {
            // Not valid JSON
        }
    }

    if (lowerFilename.endsWith('.csv')) {
        const firstLine = content.split(/\r?\n/)[0].toLowerCase();
        if (firstLine.includes('name') && firstLine.includes('url') && firstLine.includes('username') && firstLine.includes('password')) {
            return ImportFormat.CHROME_PASSWORDS;
        }
        if (firstLine.includes('id') && firstLine.includes('type') && firstLine.includes('title') && firstLine.includes('data')) {
            return ImportFormat.MONICA_CSV;
        }
    }

    return ImportFormat.AUTO;
}

async function importChromePasswords(content: string): Promise<{ count: number; items: Partial<SecureItem>[] }> {
    const rows = parseCSV(content);
    const items: Partial<SecureItem>[] = [];

    for (const row of rows) {
        const passwordData: PasswordEntry = {
            username: row.username || row.Username || '',
            password: row.password || row.Password || '',
            website: row.url || row.URL || '',
            category: 'default',
        };

        items.push({
            itemType: ItemType.Password,
            title: row.name || row.Name || row.url || 'Imported Password',
            notes: row.note || row.Note || '',
            isFavorite: false,
            sortOrder: 0,
            itemData: passwordData,
        });
    }

    return { count: items.length, items };
}

async function importAegisTotp(content: string): Promise<{ count: number; items: Partial<SecureItem>[] }> {
    const json = JSON.parse(content);
    const items: Partial<SecureItem>[] = [];

    if (json.db && json.db.entries) {
        for (const entry of json.db.entries) {
            const totpData: TotpData = {
                secret: entry.info?.secret || '',
                issuer: entry.issuer || '',
                accountName: entry.name || '',
                period: entry.info?.period || 30,
                digits: entry.info?.digits || 6,
                algorithm: entry.info?.algo || 'SHA1',
                otpType: OtpType.TOTP,
            };

            if (!totpData.secret) continue;

            items.push({
                itemType: ItemType.Totp,
                title: entry.issuer || entry.name || 'Imported TOTP',
                notes: '',
                isFavorite: entry.favorite || false,
                sortOrder: 0,
                itemData: totpData,
            });
        }
    }

    return { count: items.length, items };
}

async function importMonicaCsv(content: string): Promise<{ count: number; items: Partial<SecureItem>[] }> {
    const rows = parseCSV(content);
    const items: Partial<SecureItem>[] = [];

    const itemTypeMap: Record<string, ItemType> = {
        'PASSWORD': ItemType.Password,
        'NOTE': ItemType.Note,
        'TOTP': ItemType.Totp,
        'DOCUMENT': ItemType.Document,
    };

    for (const row of rows) {
        const typeStr = (row.Type || row.type || '').toUpperCase();
        const itemType = itemTypeMap[typeStr];
        if (itemType === undefined) continue;

        let itemData: unknown;
        try {
            itemData = JSON.parse(row.Data || row.data || '{}');
        } catch {
            itemData = {};
        }

        items.push({
            itemType,
            title: row.Title || row.title || 'Imported Item',
            notes: row.Notes || row.notes || '',
            isFavorite: (row.IsFavorite || row.isFavorite) === 'true',
            sortOrder: 0,
            itemData: itemData as PasswordEntry | TotpData | undefined,
        });
    }

    return { count: items.length, items };
}

// ========== Component ==========
interface ImportPageProps {
    onBack: () => void;
}

export const ImportPage: React.FC<ImportPageProps> = ({ onBack }) => {
    const { i18n } = useTranslation();
    const isZh = i18n.language.startsWith('zh');

    const [selectedFormat, setSelectedFormat] = useState<ImportFormatType>(ImportFormat.AUTO);
    const [isDragging, setIsDragging] = useState(false);
    const [isImporting, setIsImporting] = useState(false);
    const [result, setResult] = useState<{ success: boolean; message: string; details?: string } | null>(null);

    const handleFile = useCallback(async (file: File) => {
        setIsImporting(true);
        setResult(null);

        try {
            const content = await file.text();
            let format = selectedFormat;

            if (format === ImportFormat.AUTO) {
                format = detectFormat(content, file.name);
                if (format === ImportFormat.AUTO) {
                    throw new Error(isZh ? '无法识别文件格式' : 'Unable to detect file format');
                }
            }

            let importResult: { count: number; items: Partial<SecureItem>[] };

            switch (format) {
                case ImportFormat.CHROME_PASSWORDS:
                    importResult = await importChromePasswords(content);
                    break;
                case ImportFormat.AEGIS_TOTP:
                    importResult = await importAegisTotp(content);
                    break;
                case ImportFormat.MONICA_CSV:
                    importResult = await importMonicaCsv(content);
                    break;
                default:
                    throw new Error(isZh ? '不支持的格式' : 'Unsupported format');
            }

            // Save items to storage
            const { saveItem } = await import('../../utils/storage');
            let savedCount = 0;
            for (const item of importResult.items) {
                try {
                    await saveItem(item as SecureItem);
                    savedCount++;
                } catch (e) {
                    console.error('[ImportPage] Failed to save item:', e);
                }
            }

            setResult({
                success: true,
                message: isZh ? '导入成功' : 'Import Successful',
                details: isZh
                    ? `成功导入 ${savedCount} 个项目`
                    : `Successfully imported ${savedCount} items`,
            });
        } catch (error) {
            const err = error as Error;
            setResult({
                success: false,
                message: isZh ? '导入失败' : 'Import Failed',
                details: err.message,
            });
        } finally {
            setIsImporting(false);
        }
    }, [selectedFormat, isZh]);

    const handleDrop = useCallback((e: React.DragEvent) => {
        e.preventDefault();
        setIsDragging(false);

        const file = e.dataTransfer.files[0];
        if (file) {
            handleFile(file);
        }
    }, [handleFile]);

    const handleFileSelect = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (file) {
            handleFile(file);
        }
    }, [handleFile]);

    return (
        <Container>
            <Header>
                <BackButton onClick={onBack}>
                    <ArrowLeft size={20} />
                </BackButton>
                <Title>{isZh ? '导入数据' : 'Import Data'}</Title>
            </Header>

            <Section>
                <SectionTitle>{isZh ? '选择格式' : 'Select Format'}</SectionTitle>
                <FormatSelect
                    value={selectedFormat}
                    onChange={(e) => setSelectedFormat(e.target.value as ImportFormatType)}
                >
                    <option value={ImportFormat.AUTO}>{isZh ? '自动检测' : 'Auto Detect'}</option>
                    <option value={ImportFormat.CHROME_PASSWORDS}>{isZh ? 'Chrome 密码' : 'Chrome Passwords'}</option>
                    <option value={ImportFormat.MONICA_CSV}>Monica CSV</option>
                    <option value={ImportFormat.AEGIS_TOTP}>Aegis TOTP</option>
                </FormatSelect>
            </Section>

            <Section>
                <SectionTitle>{isZh ? '选择文件' : 'Select File'}</SectionTitle>
                <DropZone
                    $isDragging={isDragging}
                    onDragOver={(e) => { e.preventDefault(); setIsDragging(true); }}
                    onDragLeave={() => setIsDragging(false)}
                    onDrop={handleDrop}
                    onClick={() => document.getElementById('file-input')?.click()}
                >
                    <DropIcon>
                        {isImporting ? <Loader2 size={32} className="animate-spin" /> : <Upload size={32} />}
                    </DropIcon>
                    <DropText>
                        {isImporting
                            ? (isZh ? '导入中...' : 'Importing...')
                            : (isZh ? '拖放文件到此处，或点击选择' : 'Drop file here or click to select')
                        }
                    </DropText>
                </DropZone>
                <HiddenInput
                    id="file-input"
                    type="file"
                    accept=".csv,.json"
                    onChange={handleFileSelect}
                />
            </Section>

            {result && (
                <Section>
                    <ResultCard $success={result.success}>
                        <ResultIcon $success={result.success}>
                            {result.success ? <Check size={24} /> : <AlertCircle size={24} />}
                        </ResultIcon>
                        <ResultText>
                            <ResultTitle>{result.message}</ResultTitle>
                            {result.details && <ResultDetails>{result.details}</ResultDetails>}
                        </ResultText>
                    </ResultCard>
                </Section>
            )}

            <Section>
                <SectionTitle>{isZh ? '支持的格式' : 'Supported Formats'}</SectionTitle>
                <ResultCard $success>
                    <FileText size={20} />
                    <ResultText>
                        <ResultDetails>
                            • Chrome {isZh ? '密码导出' : 'Password Export'} (CSV)<br />
                            • Monica {isZh ? '数据导出' : 'Data Export'} (CSV)<br />
                            • Aegis Authenticator (JSON)
                        </ResultDetails>
                    </ResultText>
                </ResultCard>
            </Section>
        </Container>
    );
};
