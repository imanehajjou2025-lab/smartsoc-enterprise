import { useEffect, useState, type FormEvent } from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Checkbox from '@mui/material/Checkbox';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import FormControlLabel from '@mui/material/FormControlLabel';
import IconButton from '@mui/material/IconButton';
import InputAdornment from '@mui/material/InputAdornment';
import Link from '@mui/material/Link';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import DarkModeOutlinedIcon from '@mui/icons-material/DarkModeOutlined';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutlineOutlined';
import LightModeOutlinedIcon from '@mui/icons-material/LightModeOutlined';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import PersonOutlineOutlinedIcon from '@mui/icons-material/PersonOutlineOutlined';
import VisibilityOffOutlinedIcon from '@mui/icons-material/VisibilityOffOutlined';
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined';
import { keyframes } from '@mui/material/styles';
import { useLocation, useNavigate } from 'react-router-dom';
import { useThemeMode } from '../../app/ThemeModeProvider';
import { useAppDispatch, useAppSelector } from '../../app/hooks';
import BrandLogo from '../../shared/layout/BrandLogo';
import AnimatedBackground from './background/AnimatedBackground';
import { CYBER_PALETTE } from './background/palette';
import { login } from './authSlice';

const REMEMBER_KEY = 'smartsoc.rememberedUsername';

const cardEnter = keyframes`
  from { opacity: 0; transform: translateY(22px) scale(0.97); }
  to { opacity: 1; transform: translateY(0) scale(1); }
`;

const logoIntro = keyframes`
  0% { opacity: 0; transform: scale(0.85); }
  100% { opacity: 1; transform: scale(1); }
`;

function LoginPage() {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const location = useLocation();
  const { mode, toggle } = useThemeMode();
  const { status, loginError, loginPending } = useAppSelector((state) => state.auth);
  const palette = CYBER_PALETTE[mode];
  const dark = mode === 'dark';

  const [username, setUsername] = useState(() => localStorage.getItem(REMEMBER_KEY) ?? '');
  const [password, setPassword] = useState('');
  const [rememberMe, setRememberMe] = useState(() => Boolean(localStorage.getItem(REMEMBER_KEY)));
  const [showPassword, setShowPassword] = useState(false);
  const [forgotOpen, setForgotOpen] = useState(false);

  const from = (location.state as { from?: { pathname: string } } | null)?.from?.pathname;

  useEffect(() => {
    if (status === 'authenticated') {
      navigate(from ?? '/dashboard', { replace: true });
    }
  }, [status, from, navigate]);

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    if (username && password) {
      if (rememberMe) {
        localStorage.setItem(REMEMBER_KEY, username);
      } else {
        localStorage.removeItem(REMEMBER_KEY);
      }
      void dispatch(login({ username, password }));
    }
  };

  const fieldSx = {
    '& .MuiOutlinedInput-root': {
      borderRadius: 2,
      transition: 'box-shadow .25s ease, border-color .25s ease',
      bgcolor: dark ? 'rgba(255,255,255,0.03)' : 'rgba(15,23,42,0.02)',
    },
    '& .MuiOutlinedInput-root.Mui-focused': {
      boxShadow: `0 0 0 4px ${palette.blue}26`,
    },
    // Chrome force un fond bleu/jaune opaque sur les champs auto-remplis — on le
    // neutralise avec le vieux hack `box-shadow inset` (seul moyen fiable, pas de
    // propriété standard pour ça) pour garder l'apparence sombre/claire voulue.
    '& input:-webkit-autofill': {
      WebkitBoxShadow: `0 0 0 1000px ${dark ? '#0b1220' : '#ffffff'} inset`,
      WebkitTextFillColor: palette.textPrimary,
      caretColor: palette.textPrimary,
    },
    '& input:-webkit-autofill:focus, & input:-webkit-autofill:hover': {
      WebkitBoxShadow: `0 0 0 1000px ${dark ? '#0b1220' : '#ffffff'} inset`,
    },
  };

  return (
    <Box
      sx={{
        position: 'relative',
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        overflow: 'hidden',
        color: palette.textPrimary,
      }}
    >
      <AnimatedBackground mode={mode} />

      <IconButton
        onClick={toggle}
        aria-label={dark ? 'Passer en mode clair' : 'Passer en mode sombre'}
        sx={{
          position: 'absolute',
          top: 20,
          right: 20,
          zIndex: 2,
          color: palette.textPrimary,
          bgcolor: dark ? 'rgba(255,255,255,0.06)' : 'rgba(15,23,42,0.05)',
          backdropFilter: 'blur(6px)',
          transition: 'background-color .3s ease, transform .2s ease',
          '&:hover': { transform: 'rotate(-14deg) scale(1.08)' },
        }}
      >
        {dark ? <LightModeOutlinedIcon /> : <DarkModeOutlinedIcon />}
      </IconButton>

      <Box
        sx={{
          position: 'relative',
          zIndex: 1,
          width: 400,
          maxWidth: '92vw',
          p: 4,
          borderRadius: '28px',
          backdropFilter: 'blur(18px)',
          bgcolor: palette.cardBg,
          border: '1px solid',
          borderColor: palette.cardBorder,
          boxShadow: dark
            ? `0 30px 70px rgba(0,0,0,0.55), 0 0 40px ${palette.blue}1f`
            : `0 24px 60px rgba(15,23,42,0.12), 0 0 30px ${palette.blue}14`,
          animation: `${cardEnter} 0.6s cubic-bezier(0.22,1,0.36,1) both`,
          transition: 'background-color .5s ease, border-color .5s ease',
          '@media (prefers-reduced-motion: reduce)': { animation: 'none' },
        }}
      >
        <Box sx={{ display: 'flex', justifyContent: 'center', mb: 2 }}>
          <Box
            sx={{
              animation: `${logoIntro} 0.7s cubic-bezier(0.22,1,0.36,1) both`,
              '@media (prefers-reduced-motion: reduce)': { animation: 'none' },
            }}
          >
            <BrandLogo variant="full" height={116} />
          </Box>
        </Box>

        <Typography variant="h6" align="center" sx={{ fontWeight: 700 }}>
          Console de supervision sécurité
        </Typography>
        <Typography variant="body2" align="center" sx={{ mb: 3, color: palette.textSecondary }}>
          Protéger. Détecter.{' '}
          <Box component="span" sx={{ color: palette.cyan, fontWeight: 600 }}>
            Réagir.
          </Box>
        </Typography>

        {loginError && (
          <Box
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1,
              mb: 2,
              p: 1.25,
              borderRadius: 2,
              bgcolor: `${palette.red}14`,
              border: '1px solid',
              borderColor: `${palette.red}40`,
              color: palette.red,
            }}
          >
            <ErrorOutlineIcon fontSize="small" />
            <Typography variant="body2">{loginError}</Typography>
          </Box>
        )}

        <Box component="form" onSubmit={handleSubmit}>
          <TextField
            label="Nom d'utilisateur"
            placeholder="Entrez votre nom d'utilisateur"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            fullWidth
            required
            autoFocus
            autoComplete="username"
            margin="normal"
            slotProps={{
              input: {
                startAdornment: (
                  <InputAdornment position="start">
                    <PersonOutlineOutlinedIcon fontSize="small" sx={{ color: palette.textSecondary }} />
                  </InputAdornment>
                ),
              },
            }}
            sx={fieldSx}
          />
          <TextField
            label="Mot de passe"
            placeholder="Entrez votre mot de passe"
            type={showPassword ? 'text' : 'password'}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            fullWidth
            required
            autoComplete="current-password"
            margin="normal"
            slotProps={{
              input: {
                startAdornment: (
                  <InputAdornment position="start">
                    <LockOutlinedIcon fontSize="small" sx={{ color: palette.textSecondary }} />
                  </InputAdornment>
                ),
                endAdornment: (
                  <InputAdornment position="end">
                    <IconButton
                      size="small"
                      onClick={() => setShowPassword((v) => !v)}
                      aria-label={showPassword ? 'Masquer le mot de passe' : 'Afficher le mot de passe'}
                      edge="end"
                    >
                      {showPassword ? (
                        <VisibilityOffOutlinedIcon fontSize="small" />
                      ) : (
                        <VisibilityOutlinedIcon fontSize="small" />
                      )}
                    </IconButton>
                  </InputAdornment>
                ),
              },
            }}
            sx={fieldSx}
          />

          <Box
            sx={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              mt: 0.5,
              flexWrap: 'wrap',
            }}
          >
            <FormControlLabel
              control={
                <Checkbox
                  size="small"
                  checked={rememberMe}
                  onChange={(e) => setRememberMe(e.target.checked)}
                  sx={{ color: palette.textSecondary, '&.Mui-checked': { color: palette.blue } }}
                />
              }
              label={
                <Typography variant="body2" sx={{ color: palette.textSecondary }}>
                  Se souvenir de moi
                </Typography>
              }
            />
            <Link
              component="button"
              type="button"
              variant="body2"
              underline="hover"
              onClick={() => setForgotOpen(true)}
              sx={{ color: palette.cyan }}
            >
              Mot de passe oublié ?
            </Link>
          </Box>

          <Button
            type="submit"
            fullWidth
            size="large"
            disabled={loginPending || !username || !password}
            endIcon={<ArrowForwardIcon sx={{ transition: 'transform .25s ease' }} />}
            sx={{
              mt: 2.5,
              py: 1.2,
              fontWeight: 700,
              letterSpacing: 0.5,
              color: '#fff',
              textTransform: 'uppercase',
              background: `linear-gradient(90deg, ${palette.blue}, ${palette.cyan})`,
              boxShadow: `0 8px 24px ${palette.blue}40`,
              transition: 'transform .2s ease, box-shadow .2s ease',
              '&:hover': {
                transform: 'translateY(-2px)',
                boxShadow: `0 14px 32px ${palette.blue}55`,
                '& .MuiButton-endIcon svg': { transform: 'translateX(4px)' },
              },
              '&.Mui-disabled': { color: 'rgba(255,255,255,0.5)' },
            }}
          >
            {loginPending ? 'Connexion…' : 'Se connecter'}
          </Button>
        </Box>

        <Typography variant="caption" align="center" sx={{ display: 'block', mt: 3, color: palette.textSecondary }}>
          © {new Date().getFullYear()}{' '}
          <Box component="span" sx={{ color: palette.blue, fontWeight: 600 }}>
            ISIX Enterprise
          </Box>
          . Tous droits réservés.
        </Typography>
      </Box>

      <Dialog open={forgotOpen} onClose={() => setForgotOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Mot de passe oublié</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary">
            Contactez votre administrateur SmartSOC pour réinitialiser votre mot de passe.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setForgotOpen(false)}>Fermer</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}

export default LoginPage;
