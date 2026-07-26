import { useEffect, useState } from 'react';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';

function pad(n: number) {
  return String(n).padStart(2, '0');
}

/** Horloge locale + UTC, purement cliente (aucun appel réseau) — repère temporel pour l'analyste. */
function UtcClock() {
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(id);
  }, []);

  const local = `${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`;
  const utc = `${pad(now.getUTCHours())}:${pad(now.getUTCMinutes())}:${pad(now.getUTCSeconds())}`;

  return (
    <Stack sx={{ alignItems: 'flex-end', display: { xs: 'none', md: 'flex' } }}>
      <Typography variant="caption" sx={{ fontFamily: 'monospace', lineHeight: 1.3 }}>
        {local}
      </Typography>
      <Typography
        variant="caption"
        color="text.secondary"
        sx={{ fontFamily: 'monospace', lineHeight: 1.1 }}
      >
        {utc} UTC
      </Typography>
    </Stack>
  );
}

export default UtcClock;
