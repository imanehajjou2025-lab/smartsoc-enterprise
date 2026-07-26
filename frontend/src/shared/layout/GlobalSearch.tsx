import { useEffect, useMemo, useState } from 'react';
import Dialog from '@mui/material/Dialog';
import DnsIcon from '@mui/icons-material/Dns';
import InputAdornment from '@mui/material/InputAdornment';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import ListSubheader from '@mui/material/ListSubheader';
import SearchIcon from '@mui/icons-material/Search';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { listAssets } from '../../features/assets/assetsApi';
import { navigation } from './navigation';

interface Props {
  open: boolean;
  onClose: () => void;
}

/**
 * Recherche rapide (Ctrl+K) : navigation filtrée en direct (statique,
 * toujours disponible) + recherche d'actifs par hostname, seule requête
 * du périmètre à réellement supporter un paramètre `search` libre
 * (`GET /assets?search=`) — aucune autre recherche n'est simulée.
 */
function GlobalSearch({ open, onClose }: Props) {
  const navigate = useNavigate();
  const [query, setQuery] = useState('');

  useEffect(() => {
    if (!open) setQuery('');
  }, [open]);

  const navMatches = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return [];
    return navigation.flatMap((section) =>
      section.items.filter((item) => item.label.toLowerCase().includes(q)),
    );
  }, [query]);

  const assetSearch = query.trim().length >= 2 ? query.trim() : '';
  const { data: assets } = useQuery({
    queryKey: ['global-search-assets', assetSearch],
    queryFn: () => listAssets({ search: assetSearch, page: 0, size: 5 }),
    enabled: Boolean(assetSearch),
  });

  const go = (path: string) => {
    onClose();
    navigate(path);
  };

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="sm"
      fullWidth
      slotProps={{ paper: { sx: { position: 'fixed', top: 96, m: 0 } } }}
    >
      <TextField
        autoFocus
        fullWidth
        placeholder="Rechercher un module ou un actif par nom d'hôte…"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        slotProps={{
          input: {
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon fontSize="small" />
              </InputAdornment>
            ),
          },
        }}
        sx={{ p: 1.5, '& fieldset': { border: 'none' } }}
      />
      {query.trim() !== '' && (
        <List dense sx={{ maxHeight: 360, overflowY: 'auto', pb: 1 }}>
          {navMatches.length > 0 && (
            <>
              <ListSubheader sx={{ bgcolor: 'transparent' }}>Modules</ListSubheader>
              {navMatches.map((item) => {
                const Icon = item.icon;
                return (
                  <ListItemButton key={item.path} onClick={() => go(item.path)}>
                    <ListItemIcon sx={{ minWidth: 36 }}>
                      <Icon fontSize="small" />
                    </ListItemIcon>
                    <ListItemText primary={item.label} />
                  </ListItemButton>
                );
              })}
            </>
          )}
          {assets && assets.items.length > 0 && (
            <>
              <ListSubheader sx={{ bgcolor: 'transparent' }}>Actifs</ListSubheader>
              {assets.items.map((asset) => (
                <ListItemButton key={asset.id} onClick={() => go(`/assets?selected=${asset.id}`)}>
                  <ListItemIcon sx={{ minWidth: 36 }}>
                    <DnsIcon fontSize="small" />
                  </ListItemIcon>
                  <ListItemText primary={asset.hostname} secondary={asset.displayName} />
                </ListItemButton>
              ))}
            </>
          )}
          {navMatches.length === 0 && (!assets || assets.items.length === 0) && (
            <Typography variant="body2" color="text.secondary" sx={{ px: 2, py: 2 }}>
              Aucun résultat.
            </Typography>
          )}
        </List>
      )}
    </Dialog>
  );
}

export default GlobalSearch;
