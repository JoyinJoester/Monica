import { useState, useCallback } from 'react';
import styled from 'styled-components';
import { useTranslation } from 'react-i18next';
import { ArrowLeft, Upload, FileText, Check, AlertCircle, Loader2 } from 'lucide-react';
import {
    ImportFormat,
    importFromFile,
    importWithFormat,
    validateFile,
    type ImportResult
} from '../../utils/ImportManager';

const UiFormat = {
    AUTO: 'auto',
    CHROME: 'chrome',
    MONICA: 'monica',
    MONICA_ZIP: 'monica_zip',
    KEEPASS_CSV: 'keepass_csv',
    KEEPASS_KDBX: 'keepass_kdbx',
    AEGIS: 'aegis',
} as const;
type UiFormat = typeof UiFormat[keyof typeof UiFormat];

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
  transition: all 0.15s ease;
  &:hover {
    background: ${({ theme }) => theme.colors.surfaceVariant};
  }
  &:active {
    transform: scale(0.9);
    opacity: 0.7;
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
  &:active {
    transform: scale(0.98);
    opacity: 0.85;
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
  white-space: pre-line;
`;

interface ImportPageProps {
    onBack: () => void;
}

const toManagerFormat = (uiFormat: UiFormat): ImportFormat | null => {
    if (uiFormat === UiFormat.CHROME) return ImportFormat.CHROME_PASSWORD;
    if (uiFormat === UiFormat.MONICA) return ImportFormat.MONICA_CSV;
    if (uiFormat === UiFormat.MONICA_ZIP) return ImportFormat.MONICA_ZIP;
    if (uiFormat === UiFormat.KEEPASS_CSV) return ImportFormat.KEEPASS_CSV;
    if (uiFormat === UiFormat.KEEPASS_KDBX) return ImportFormat.KEEPASS_KDBX;
    if (uiFormat === UiFormat.AEGIS) return ImportFormat.AEGIS_JSON;
    return null;
};

export const ImportPage: React.FC<ImportPageProps> = ({ onBack }) => {
    const { i18n } = useTranslation();
    const isZh = i18n.language.startsWith('zh');

    const [selectedFormat, setSelectedFormat] = useState<UiFormat>(UiFormat.AUTO);
    const [isDragging, setIsDragging] = useState(false);
    const [isImporting, setIsImporting] = useState(false);
    const [result, setResult] = useState<{ success: boolean; message: string; details?: string } | null>(null);

    const renderResultDetails = (importResult: ImportResult) => {
        const lines = [
            isZh ? `总计: ${importResult.totalCount}` : `Total: ${importResult.totalCount}`,
            isZh ? `导入成功: ${importResult.importedCount}` : `Imported: ${importResult.importedCount}`,
            isZh ? `跳过: ${importResult.skippedCount}` : `Skipped: ${importResult.skippedCount}`,
            isZh ? `失败: ${importResult.errorCount}` : `Failed: ${importResult.errorCount}`,
        ];
        if (importResult.errors.length > 0) {
            lines.push('');
            lines.push(...importResult.errors.slice(0, 3));
        }
        return lines.join('\n');
    };

    const handleFile = useCallback(async (file: File) => {
        setIsImporting(true);
        setResult(null);

        try {
            const validation = validateFile(file);
            if (!validation.valid) {
                throw new Error(validation.error || (isZh ? '文件格式无效' : 'Invalid file format'));
            }

            const managerFormat = toManagerFormat(selectedFormat);
            const importResult = managerFormat
                ? await importWithFormat(file, managerFormat)
                : await importFromFile(file);

            setResult({
                success: importResult.success,
                message: importResult.success
                    ? (isZh ? '导入完成' : 'Import Completed')
                    : (isZh ? '导入失败' : 'Import Failed'),
                details: renderResultDetails(importResult),
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
    }, [isZh, selectedFormat]);

    const handleDrop = useCallback((e: React.DragEvent) => {
        e.preventDefault();
        setIsDragging(false);
        const file = e.dataTransfer.files[0];
        if (file) handleFile(file);
    }, [handleFile]);

    const handleFileSelect = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (file) handleFile(file);
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
                    onChange={(e) => setSelectedFormat(e.target.value as UiFormat)}
                >
                    <option value={UiFormat.AUTO}>{isZh ? '自动检测' : 'Auto Detect'}</option>
                    <option value={UiFormat.CHROME}>{isZh ? 'Chrome 密码' : 'Chrome Passwords'}</option>
                    <option value={UiFormat.MONICA}>Monica CSV</option>
                    <option value={UiFormat.MONICA_ZIP}>Monica ZIP</option>
                    <option value={UiFormat.KEEPASS_CSV}>KeePass CSV</option>
                    <option value={UiFormat.KEEPASS_KDBX}>KeePass KDBX</option>
                    <option value={UiFormat.AEGIS}>Aegis TOTP</option>
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
                    accept=".csv,.json,.zip,.kdbx"
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
                            • Chrome {isZh ? '密码导出' : 'Password Export'} (CSV){'\n'}
                            • Monica {isZh ? '数据导出' : 'Data Export'} (CSV / ZIP){'\n'}
                            • KeePass (CSV / KDBX){'\n'}
                            • Aegis Authenticator (JSON)
                        </ResultDetails>
                    </ResultText>
                </ResultCard>
            </Section>
        </Container>
    );
};
