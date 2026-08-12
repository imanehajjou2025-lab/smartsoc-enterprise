import Autocomplete from '@mui/material/Autocomplete';
import TextField from '@mui/material/TextField';
import type { SxProps, Theme } from '@mui/material/styles';
import type { PlatformUser } from '../../features/admin/usersApi';

/**
 * Sélecteur d'utilisateur de la plateforme, réutilisé partout où une
 * affectation se fait à un compte réel (alertes, incidents...) plutôt
 * qu'à un nom libre — la liste d'utilisateurs reste la seule source de
 * vérité pour qui peut être assigné.
 */
function AssigneeAutocomplete({
  users,
  value,
  onChange,
  placeholder = 'Assigné à',
  sx,
}: {
  users: PlatformUser[];
  value: string;
  onChange: (username: string) => void;
  placeholder?: string;
  sx?: SxProps<Theme>;
}) {
  return (
    <Autocomplete
      size="small"
      options={users}
      getOptionLabel={(u: PlatformUser) => `${u.fullName} (${u.username})`}
      isOptionEqualToValue={(a, b) => a.username === b.username}
      value={users.find((u) => u.username === value) ?? null}
      onChange={(_, selected) => onChange(selected?.username ?? '')}
      renderInput={(params) => <TextField {...params} placeholder={placeholder} />}
      sx={{ minWidth: 220, ...sx }}
    />
  );
}

export default AssigneeAutocomplete;
