import { useState, useEffect, useCallback, useMemo } from 'react';
import styled from 'styled-components';
import { useTranslation } from 'react-i18next';
import { Card, CardTitle, CardSubtitle } from '../../components/common/Card';
import { Input } from '../../components/common/Input';
import { Button } from '../../components/common/Button';
import {
  Plus, Search, Copy, Trash2, Eye, EyeOff,
  RefreshCw, ChevronDown, ChevronUp, Star, FolderPlus
} from 'lucide-react';
import { getPasswords, saveItem, deleteItem, updateItem, getCategories, saveCategory } from '../../utils/storage';
import type { SecureItem, PasswordEntry } from '../../types/models';
import type { Category } from '../../utils/storage';
import { ItemType } from '../../types/models';

// ========== Styled Components ==========
const Container = styled.div<{ $manager: boolean }>`
  padding: 16px;
  padding-bottom: 100px;

  ${({ $manager }) => $manager && `
    max-width: 1040px;
    margin: 0 auto;
    padding: 20px 24px 120px;
  `}

  @media (max-width: 900px) {
    max-width: none;
    margin: 0;
    padding: 16px;
    padding-bottom: 100px;
  }
`;

const SearchContainer = styled.div`
  position: relative;
  margin-bottom: 16px;
  svg { position: absolute; right: 16px; top: 50%; transform: translateY(-50%); color: ${({ theme }) => theme.colors.onSurfaceVariant}; width: 20px; height: 20px; }
`;

const FAB = styled(Button)<{ $manager: boolean }>`
  position: fixed; bottom: 90px; right: 20px; width: 56px; height: 56px;
  border-radius: 16px; padding: 0; box-shadow: 0 4px 12px rgba(0,0,0,0.2);
  svg { width: 24px; height: 24px; }

  ${({ $manager }) => $manager && `
    right: 24px;
    bottom: 24px;
  `}
`;

const CardActions = styled.div`
  display: flex; gap: 8px; margin-top: 12px;
`;

const IconButton = styled.button`
  background: none; border: none; cursor: pointer; padding: 8px; border-radius: 8px;
  color: ${({ theme }) => theme.colors.onSurfaceVariant}; transition: all 0.2s ease;
  &:hover { background-color: ${({ theme }) => theme.colors.surfaceVariant}; }
  svg { width: 18px; height: 18px; }
`;

const Modal = styled.div`
  position: fixed; inset: 0; background: rgba(0,0,0,0.5);
  display: flex; align-items: flex-start; justify-content: center;
  z-index: 100; padding: 16px; overflow-y: auto;
`;

const ModalContent = styled.div`
  background: ${({ theme }) => theme.colors.surface};
  border-radius: 16px; padding: 24px; width: 100%; max-width: 400px;
  margin: 20px 0;
`;

const ModalHeader = styled.div`
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 16px;
`;

const ModalTitle = styled.h2`
  margin: 0; font-size: 18px; color: ${({ theme }) => theme.colors.onSurface};
`;

const ButtonRow = styled.div`
  display: flex; gap: 12px; margin-top: 20px;
`;

const EmptyState = styled.div`
  text-align: center; padding: 48px 0; color: ${({ theme }) => theme.colors.onSurfaceVariant};
`;

const PasswordField = styled.div`
  position: relative;
  display: flex;
  align-items: center;
  gap: 4px;
`;

const PasswordActions = styled.div`
  display: flex;
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
`;

const SectionHeader = styled.div<{ expanded: boolean }>`
  display: flex; justify-content: space-between; align-items: center;
  padding: 12px; margin-top: 12px;
  background: ${({ theme }) => theme.colors.surfaceVariant};
  border-radius: 8px; cursor: pointer;
  font-weight: 600; font-size: 14px;
  color: ${({ theme }) => theme.colors.primary};
`;

const SectionContent = styled.div`
  padding: 12px 0;
`;

const CategoryBadge = styled.span`
  display: inline-block; padding: 4px 10px; border-radius: 12px;
  background: ${({ theme }) => theme.colors.secondaryContainer};
  color: ${({ theme }) => theme.colors.onSecondaryContainer};
  font-size: 11px; font-weight: 500; margin-right: 8px;
`;

const FavoriteIcon = styled(Star) <{ active: boolean }>`
  color: ${({ theme, active }) => active ? '#FFC107' : theme.colors.outline};
  fill: ${({ active }) => active ? '#FFC107' : 'none'};
  cursor: pointer;
`;

const StyledSelect = styled.select`
  flex: 1;
  padding: 14px 16px;
  border-radius: 12px;
  border: 1px solid ${({ theme }) => theme.colors.outlineVariant};
  font-size: 15px;
  background: ${({ theme }) => theme.colors.surfaceVariant};
  color: ${({ theme }) => theme.colors.onSurface};
  cursor: pointer;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='16' height='16' fill='%23999' viewBox='0 0 16 16'%3E%3Cpath d='M4.5 6l3.5 4 3.5-4z'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 12px center;
  padding-right: 40px;
  transition: all 0.2s ease;

  &:hover {
    border-color: ${({ theme }) => theme.colors.primary};
  }

  &:focus {
    outline: none;
    border-color: ${({ theme }) => theme.colors.primary};
    box-shadow: 0 0 0 2px ${({ theme }) => theme.colors.primary}20;
  }

  option {
    background: ${({ theme }) => theme.colors.surface};
    color: ${({ theme }) => theme.colors.onSurface};
    padding: 12px;
  }
`;

const AddCategoryButton = styled.button`
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 12px;
  border-radius: 12px;
  border: 1px dashed ${({ theme }) => theme.colors.outlineVariant};
  background: transparent;
  color: ${({ theme }) => theme.colors.primary};
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    background: ${({ theme }) => theme.colors.primaryContainer};
    border-style: solid;
    border-color: ${({ theme }) => theme.colors.primary};
  }

  svg {
    width: 20px;
    height: 20px;
  }
`;

const FilterContainer = styled.div`
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 8px;
  margin-bottom: 12px;
  
  &::-webkit-scrollbar {
    height: 4px;
  }
  &::-webkit-scrollbar-thumb {
    background: ${({ theme }) => theme.colors.outlineVariant};
    border-radius: 2px;
  }
`;

const FilterChip = styled.button<{ active: boolean }>`
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 20px;
  border: 1px solid ${({ theme, active }) => active ? theme.colors.primary : theme.colors.outlineVariant};
  background: ${({ theme, active }) => active ? theme.colors.primaryContainer : 'transparent'};
  color: ${({ theme, active }) => active ? theme.colors.onPrimaryContainer : theme.colors.onSurfaceVariant};
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s ease;

  &:hover {
    border-color: ${({ theme }) => theme.colors.primary};
    background: ${({ theme, active }) => active ? theme.colors.primaryContainer : theme.colors.surfaceVariant};
  }

  svg {
    width: 14px;
    height: 14px;
  }
`;

// ========== Password Generator ==========
const generatePassword = (length = 16, options = { upper: true, lower: true, numbers: true, symbols: true }) => {
  let chars = '';
  if (options.upper) chars += 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
  if (options.lower) chars += 'abcdefghijklmnopqrstuvwxyz';
  if (options.numbers) chars += '0123456789';
  if (options.symbols) chars += '!@#$%^&*()_+-=[]{}|;:,.<>?';
  let result = '';
  for (let i = 0; i < length; i++) {
    result += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return result;
};

// Categories are now user-created (no static presets)

// ========== Main Component ==========
export const PasswordList = () => {
  const { t, i18n } = useTranslation();
  const isZh = i18n.language?.startsWith('zh');
  const isManagerMode = useMemo(() => new URLSearchParams(window.location.search).get('manager') === '1', []);
  const prefersReducedMotion = useMemo(
    () => window.matchMedia('(prefers-reduced-motion: reduce)').matches,
    []
  );

  const [passwords, setPasswords] = useState<SecureItem[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [search, setSearch] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [passwordVisible, setPasswordVisible] = useState(false);

  // Form State - Complete fields matching Android
  const [form, setForm] = useState({
    title: '',
    website: '',
    username: '',
    password: '',
    notes: '',
    email: '',
    phone: '',
    categoryId: 0, // Changed from string to number
    isFavorite: false,
  });

  // Collapsible sections
  const [showPersonalInfo, setShowPersonalInfo] = useState(false);

  // Filter state: 'all', 'favorites', or category id (number)
  const [activeFilter, setActiveFilter] = useState<'all' | 'favorites' | number>('all');

  const resetForm = () => {
    setForm({
      title: '', website: '', username: '', password: '', notes: '',
      email: '', phone: '', categoryId: 0, isFavorite: false,
    });
    setEditingId(null);
    setPasswordVisible(false);
    setShowPersonalInfo(false);
  };

  const loadData = useCallback(async () => {
    const [pwds, cats] = await Promise.all([getPasswords(), getCategories()]);
    setPasswords(pwds);
    setCategories(cats);
  }, []);

  useEffect(() => { loadData(); }, [loadData]);

  // Listen for storage changes to auto-refresh
  useEffect(() => {
    const handleStorageChange = (changes: { [key: string]: chrome.storage.StorageChange }, areaName: string) => {
      if (areaName === 'local' && changes['monica_vault']) {
        loadData();
      }
    };
    chrome.storage.onChanged.addListener(handleStorageChange);
    return () => {
      chrome.storage.onChanged.removeListener(handleStorageChange);
    };
  }, [loadData]);

  // Apply both search and category filter
  const filteredPasswords = passwords.filter(p => {
    // Search filter
    const matchesSearch =
      p.title.toLowerCase().includes(search.toLowerCase()) ||
      (p.itemData as PasswordEntry).username?.toLowerCase().includes(search.toLowerCase()) ||
      (p.itemData as PasswordEntry).website?.toLowerCase().includes(search.toLowerCase());

    if (!matchesSearch) return false;

    // Category filter
    if (activeFilter === 'all') return true;
    if (activeFilter === 'favorites') return p.isFavorite;
    // Filter by category id
    return (p.itemData as PasswordEntry).categoryId === activeFilter;
  });

  const handleSave = async () => {
    if (!form.title || !form.password) return;

    const itemData: PasswordEntry = {
      username: form.username,
      password: form.password,
      website: form.website,
      categoryId: form.categoryId || undefined,
    };

    if (editingId) {
      await updateItem(editingId, {
        title: form.title,
        notes: form.notes,
        isFavorite: form.isFavorite,
        itemData,
      });
    } else {
      await saveItem({
        itemType: ItemType.Password,
        title: form.title,
        notes: form.notes,
        isFavorite: form.isFavorite,
        sortOrder: 0,
        itemData,
      });
    }

    resetForm();
    setShowModal(false);
    loadData();
  };

  const handleEdit = (item: SecureItem) => {
    const data = item.itemData as PasswordEntry;
    setForm({
      title: item.title,
      website: data.website || '',
      username: data.username || '',
      password: data.password || '',
      notes: item.notes || '',
      email: '',
      phone: '',
      categoryId: data.categoryId || 0,
      isFavorite: item.isFavorite,
    });
    setEditingId(item.id);
    setShowModal(true);
  };

  const handleCopy = (text: string) => {
    navigator.clipboard.writeText(text);
  };

  const handleDelete = async (id: number) => {
    if (confirm(t('common.confirmDelete'))) {
      await deleteItem(id);
      loadData();
    }
  };

  const handleGeneratePassword = () => {
    setForm({ ...form, password: generatePassword(16) });
  };

  return (
    <Container $manager={isManagerMode}>
      <SearchContainer>
        <Input placeholder={t('app.searchPlaceholder')} value={search} onChange={(e) => setSearch(e.target.value)} />
        <Search />
      </SearchContainer>

      {/* Filter Tabs */}
      <FilterContainer>
        <FilterChip
          active={activeFilter === 'all'}
          onClick={() => setActiveFilter('all')}
        >
          {isZh ? '全部' : 'All'}
        </FilterChip>
        <FilterChip
          active={activeFilter === 'favorites'}
          onClick={() => setActiveFilter('favorites')}
        >
          <Star size={14} />
          {isZh ? '收藏' : 'Favorites'}
        </FilterChip>
        {categories.map(cat => (
          <FilterChip
            key={cat.id}
            active={activeFilter === cat.id}
            onClick={() => setActiveFilter(cat.id)}
          >
            {cat.name}
          </FilterChip>
        ))}
      </FilterContainer>

      {filteredPasswords.length === 0 && <EmptyState>{t('common.noItems')}</EmptyState>}

      {filteredPasswords.map((item) => {
        const data = (item.itemData || {}) as PasswordEntry;
        return (
          <Card
            key={item.id}
            onClick={() => handleEdit(item)}
            style={{ cursor: 'pointer' }}
            initial={prefersReducedMotion ? false : { opacity: 0, y: 10 }}
            animate={prefersReducedMotion ? undefined : { opacity: 1, y: 0 }}
            transition={prefersReducedMotion ? undefined : { duration: 0.2, ease: 'easeOut' }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div>
                <CardTitle>{item.title}</CardTitle>
                <CardSubtitle>{data.username || 'No Username'}</CardSubtitle>
                <CardSubtitle style={{ opacity: 0.6, fontSize: 12 }}>{data.website || 'No Website'}</CardSubtitle>
                {data.categoryId && (
                  <CategoryBadge>
                    {categories.find(c => c.id === data.categoryId)?.name || (isZh ? '未知分类' : 'Unknown')}
                  </CategoryBadge>
                )}
              </div>
              {item.isFavorite && <Star size={16} fill="#FFC107" color="#FFC107" />}
            </div>
            <CardActions onClick={(e) => e.stopPropagation()}>
              <IconButton onClick={() => handleCopy(data.password)} title={t('common.copy')}>
                <Copy />
              </IconButton>
              <IconButton onClick={() => handleDelete(item.id)} title={t('common.delete')}>
                <Trash2 />
              </IconButton>
            </CardActions>
          </Card>
        );
      })}

      <FAB $manager={isManagerMode} onClick={() => { resetForm(); setShowModal(true); }}>
        <Plus />
      </FAB>

      {showModal && (
        <Modal onClick={() => { setShowModal(false); resetForm(); }}>
          <ModalContent onClick={(e) => e.stopPropagation()}>
            <ModalHeader>
              <ModalTitle>{editingId ? t('common.edit') : t('common.add')} {t('passwords.title')}</ModalTitle>
              <FavoriteIcon
                active={form.isFavorite}
                onClick={() => setForm({ ...form, isFavorite: !form.isFavorite })}
                size={24}
              />
            </ModalHeader>

            <Input label={`${t('passwords.title')} *`} value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />

            {/* Category Selector with Add New */}
            <div style={{ marginTop: 12 }}>
              <label style={{ fontSize: 12, color: 'gray', marginBottom: 4, display: 'block' }}>{t('passwords.category')}</label>
              <div style={{ display: 'flex', gap: 8 }}>
                <StyledSelect
                  value={form.categoryId}
                  onChange={(e) => setForm({ ...form, categoryId: Number(e.target.value) })}
                >
                  <option value={0}>{isZh ? '无分类' : 'No Category'}</option>
                  {categories.map(cat => (
                    <option key={cat.id} value={cat.id}>{cat.name}</option>
                  ))}
                </StyledSelect>
                <AddCategoryButton
                  onClick={async () => {
                    const name = prompt(isZh ? '输入新分类名称：' : 'Enter new category name:');
                    if (name?.trim()) {
                      const newCat = await saveCategory(name.trim());
                      setCategories([...categories, newCat]);
                      setForm({ ...form, categoryId: newCat.id });
                    }
                  }}
                  title={isZh ? '新建分类' : 'Add Category'}
                >
                  <FolderPlus />
                </AddCategoryButton>
              </div>
            </div>

            <Input label={t('passwords.website')} value={form.website} onChange={(e) => setForm({ ...form, website: e.target.value })} />
            <Input label={t('passwords.username')} value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} />

            {/* Password with visibility toggle and generator */}
            <PasswordField>
              <Input
                label={`${t('passwords.password')} *`}
                type={passwordVisible ? 'text' : 'password'}
                value={form.password}
                onChange={(e) => setForm({ ...form, password: e.target.value })}
                style={{ flex: 1, paddingRight: 80 }}
              />
              <PasswordActions>
                <IconButton onClick={handleGeneratePassword} title="Generate"><RefreshCw size={18} /></IconButton>
                <IconButton onClick={() => setPasswordVisible(!passwordVisible)}>
                  {passwordVisible ? <EyeOff size={18} /> : <Eye size={18} />}
                </IconButton>
              </PasswordActions>
            </PasswordField>

            {/* Collapsible: Personal Info */}
            <SectionHeader expanded={showPersonalInfo} onClick={() => setShowPersonalInfo(!showPersonalInfo)}>
              <span>{isZh ? '个人信息' : 'Personal Info'}</span>
              {showPersonalInfo ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
            </SectionHeader>
            {showPersonalInfo && (
              <SectionContent>
                <Input label={isZh ? '邮箱' : 'Email'} type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
                <Input label={isZh ? '手机号' : 'Phone'} type="tel" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
              </SectionContent>
            )}

            {/* Notes */}
            <div style={{ marginTop: 12 }}>
              <label style={{ fontSize: 12, color: 'gray' }}>{isZh ? '备注' : 'Notes'}</label>
              <textarea
                value={form.notes}
                onChange={(e) => setForm({ ...form, notes: e.target.value })}
                style={{
                  width: '100%', minHeight: 80, padding: 12, marginTop: 4,
                  borderRadius: 12, border: '1px solid currentColor', fontSize: 14,
                  resize: 'vertical', fontFamily: 'inherit',
                  background: 'transparent', color: 'inherit'
                }}
              />
            </div>

            <ButtonRow>
              <Button variant="text" onClick={() => { setShowModal(false); resetForm(); }}>{t('common.cancel')}</Button>
              <Button onClick={handleSave} disabled={!form.title || !form.password}>{t('common.save')}</Button>
            </ButtonRow>
          </ModalContent>
        </Modal>
      )}
    </Container>
  );
};
