import { useEffect, useReducer, useRef, useState } from 'react';
import Alert from '@mui/material/Alert';
import Avatar from '@mui/material/Avatar';
import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import IconButton from '@mui/material/IconButton';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { alpha, keyframes, useTheme } from '@mui/material/styles';
import AutoAwesomeOutlinedIcon from '@mui/icons-material/AutoAwesomeOutlined';
import ChatBubbleOutlineOutlinedIcon from '@mui/icons-material/ChatBubbleOutlineOutlined';
import CheckCircleOutlineOutlinedIcon from '@mui/icons-material/CheckCircleOutlineOutlined';
import CloseIcon from '@mui/icons-material/Close';
import DeleteSweepIcon from '@mui/icons-material/DeleteSweep';
import LinkOutlinedIcon from '@mui/icons-material/LinkOutlined';
import PersonIcon from '@mui/icons-material/Person';
import SecurityOutlinedIcon from '@mui/icons-material/SecurityOutlined';
import SendIcon from '@mui/icons-material/Send';
import SmartToyOutlinedIcon from '@mui/icons-material/SmartToyOutlined';
import SummarizeOutlinedIcon from '@mui/icons-material/SummarizeOutlined';
import WarningAmberOutlinedIcon from '@mui/icons-material/WarningAmberOutlined';
import axios from 'axios';
import { severityColors } from '../../app/theme';
import { problemDetail } from '../../shared/api/client';
import ActionCard from '../../shared/components/ActionCard';
import PageHeaderBanner from '../../shared/components/PageHeaderBanner';
import { chatWithAssistant, type ChatContext, type ChatMessage } from './assistantApi';
import { clearAssistantContext, readAssistantContext } from './assistantContext';

const MESSAGES_STORAGE_KEY = 'smartsoc.assistant.messages';

/**
 * Message affiché à l'écran : sur-ensemble de ChatMessage (contrat) avec
 * un état d'affichage local. `pending`/`content` vide = bulle "en train
 * d'écrire" — le même champ sert de cible d'ajout progressif le jour où
 * le backend streamera réellement (voir `appendToLastAssistant`), donc
 * aucune refonte ne sera nécessaire à ce moment-là.
 */
interface DisplayMessage extends ChatMessage {
  id: string;
  pending?: boolean;
  failed?: boolean;
}

type MessagesAction =
  | { type: 'hydrate'; messages: DisplayMessage[] }
  | { type: 'addUser'; content: string }
  | { type: 'startAssistantReply' }
  | { type: 'appendToLastAssistant'; delta: string }
  | { type: 'finalizeLastAssistant' }
  | { type: 'markLastAssistantFailed'; errorMessage: string }
  | { type: 'clear' };

function messagesReducer(state: DisplayMessage[], action: MessagesAction): DisplayMessage[] {
  switch (action.type) {
    case 'hydrate':
      return action.messages;
    case 'addUser':
      return [...state, { id: crypto.randomUUID(), role: 'user', content: action.content }];
    case 'startAssistantReply':
      return [...state, { id: crypto.randomUUID(), role: 'assistant', content: '', pending: true }];
    case 'appendToLastAssistant':
      return state.map((m, i) =>
        i === state.length - 1 ? { ...m, content: m.content + action.delta } : m,
      );
    case 'finalizeLastAssistant':
      return state.map((m, i) => (i === state.length - 1 ? { ...m, pending: false } : m));
    case 'markLastAssistantFailed':
      return state.map((m, i) =>
        i === state.length - 1
          ? { ...m, pending: false, failed: true, content: action.errorMessage }
          : m,
      );
    case 'clear':
      return [];
    default:
      return state;
  }
}

function loadStoredMessages(): DisplayMessage[] {
  try {
    const raw = sessionStorage.getItem(MESSAGES_STORAGE_KEY);
    return raw ? (JSON.parse(raw) as DisplayMessage[]) : [];
  } catch {
    return [];
  }
}

interface Suggestion {
  icon: React.ReactNode;
  title: string;
  question: string;
  color: string;
}

const SUGGESTIONS: Suggestion[] = [
  {
    icon: <WarningAmberOutlinedIcon />,
    title: 'Analyser une alerte',
    question: 'Comment analyser une alerte suspecte, étape par étape ?',
    color: severityColors.critical,
  },
  {
    icon: <SecurityOutlinedIcon />,
    title: 'Expliquer une technique',
    question: 'Explique-moi la technique MITRE ATT&CK T1110.',
    color: severityColors.info,
  },
  {
    icon: <SummarizeOutlinedIcon />,
    title: "Résumer un incident",
    question: "Aide-moi à rédiger un résumé d'incident pour ma hiérarchie.",
    color: severityColors.high,
  },
  {
    icon: <CheckCircleOutlineOutlinedIcon />,
    title: 'Bonnes pratiques',
    question: 'Quelles sont les bonnes pratiques pour traiter un faux positif ?',
    color: severityColors.low,
  },
];

/** Message d'erreur adapté au cas (jamais le générique brut d'axios). */
function describeError(error: unknown): string {
  if (axios.isAxiosError(error)) {
    if (error.code === 'ECONNABORTED') {
      return 'Le service met trop de temps à répondre. Réessaie dans quelques instants.';
    }
    if (!error.response) {
      return 'Impossible de contacter le serveur.';
    }
    if (error.response.status === 503) {
      return problemDetail(
        error,
        "L'assistant est actuellement indisponible. Réessaie dans quelques instants.",
      );
    }
    if (error.response.status === 403) {
      return 'Action non autorisée.';
    }
    return problemDetail(error, 'Une erreur est survenue.');
  }
  return 'Une erreur est survenue.';
}

const float = keyframes`
  0%, 100% { transform: translateY(0px); }
  50% { transform: translateY(-8px); }
`;

const glowPulse = keyframes`
  0%, 100% { opacity: 0.55; transform: scale(1); }
  50% { opacity: 1; transform: scale(1.08); }
`;

const orbit = keyframes`
  from { transform: rotate(0deg) translateX(46px) rotate(0deg); }
  to { transform: rotate(360deg) translateX(46px) rotate(-360deg); }
`;

const ORBIT_PARTICLES = [
  { color: severityColors.info, size: 7, duration: '5.5s', delay: '0s' },
  { color: severityColors.low, size: 6, duration: '7s', delay: '1.2s' },
  { color: severityColors.high, size: 5, duration: '6.2s', delay: '2.4s' },
];

/**
 * Mascotte décorative : robot flottant + halo pulsé + particules en
 * orbite, en CSS pur (aucune donnée). N'apparaît que sur l'écran de
 * bienvenue — jamais au premier plan une fois la conversation démarrée.
 */
function AssistantMascot() {
  const accent = severityColors.info;
  return (
    <Box sx={{ position: 'relative', width: 120, height: 120, mx: 'auto', mb: 2 }}>
      <Box
        sx={{
          position: 'absolute',
          inset: 0,
          margin: 'auto',
          width: '100%',
          height: '100%',
          borderRadius: '50%',
          background: `radial-gradient(circle, ${alpha(accent, 0.35)} 0%, transparent 70%)`,
          animation: `${glowPulse} 3s ease-in-out infinite`,
        }}
      />
      {ORBIT_PARTICLES.map((p, i) => (
        <Box
          key={i}
          sx={{
            position: 'absolute',
            top: '50%',
            left: '50%',
            width: p.size,
            height: p.size,
            marginTop: `-${p.size / 2}px`,
            marginLeft: `-${p.size / 2}px`,
            borderRadius: '50%',
            bgcolor: p.color,
            boxShadow: `0 0 8px 2px ${alpha(p.color, 0.7)}`,
            animation: `${orbit} ${p.duration} linear infinite`,
            animationDelay: p.delay,
          }}
        />
      ))}
      <Box
        sx={{
          position: 'absolute',
          inset: 0,
          margin: 'auto',
          width: 72,
          height: 72,
          borderRadius: '50%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: accent,
          bgcolor: alpha(accent, 0.16),
          border: '1px solid',
          borderColor: alpha(accent, 0.5),
          boxShadow: `0 0 30px 6px ${alpha(accent, 0.35)}`,
          animation: `${float} 3.6s ease-in-out infinite`,
        }}
      >
        <SmartToyOutlinedIcon sx={{ fontSize: 36 }} />
      </Box>
    </Box>
  );
}

function MiniStat({ icon, label, value, color }: { icon: React.ReactNode; label: string; value: string; color: string }) {
  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1,
        p: 1.25,
        borderRadius: 2,
        border: '1px solid',
        borderColor: alpha(color, 0.25),
        bgcolor: (t) => alpha(color, t.palette.mode === 'dark' ? 0.1 : 0.06),
      }}
    >
      <Box
        sx={{
          width: 28,
          height: 28,
          borderRadius: '50%',
          flexShrink: 0,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color,
          bgcolor: alpha(color, 0.16),
        }}
      >
        {icon}
      </Box>
      <Box sx={{ minWidth: 0 }}>
        <Typography variant="subtitle2" sx={{ fontWeight: 800, lineHeight: 1.1 }} noWrap>
          {value}
        </Typography>
        <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block' }}>
          {label}
        </Typography>
      </Box>
    </Box>
  );
}

/** Page de dialogue avec l'assistant IA (ADR-008) : sans état côté backend — cette page est propriétaire de la conversation. */
function AssistantPage() {
  const theme = useTheme();
  const [messages, dispatch] = useReducer(messagesReducer, undefined, loadStoredMessages);
  const [context, setContext] = useState<ChatContext | null>(() => readAssistantContext());
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    sessionStorage.setItem(MESSAGES_STORAGE_KEY, JSON.stringify(messages));
    bottomRef.current?.scrollIntoView?.({ behavior: 'smooth', block: 'end' });
  }, [messages]);

  async function send(text: string) {
    const content = text.trim();
    if (!content || sending) return;

    // Les messages en échec (bulle d'erreur affichée à l'écran, ex. "assistant
    // indisponible") ne sont jamais de la vraie conversation : les renvoyer au
    // service IA pourrait déclencher ses propres garde-fous (ex. un ';' pris
    // pour une tentative d'injection SQL) sur du texte qu'il n'a jamais dit.
    const outgoing: ChatMessage[] = [
      ...messages.filter((m) => !m.failed),
      { role: 'user' as const, content },
    ].map(({ role, content: c }) => ({ role, content: c }));

    dispatch({ type: 'addUser', content });
    dispatch({ type: 'startAssistantReply' });
    setInput('');
    setSending(true);

    try {
      const response = await chatWithAssistant(outgoing, context);
      dispatch({ type: 'appendToLastAssistant', delta: response.reply });
      dispatch({ type: 'finalizeLastAssistant' });
    } catch (error) {
      dispatch({ type: 'markLastAssistantFailed', errorMessage: describeError(error) });
    } finally {
      setSending(false);
    }
  }

  function clearConversation() {
    dispatch({ type: 'clear' });
    sessionStorage.removeItem(MESSAGES_STORAGE_KEY);
    clearAssistantContext();
    setContext(null);
  }

  function dismissContext() {
    clearAssistantContext();
    setContext(null);
  }

  const assistantReplies = messages.filter((m) => m.role === 'assistant' && !m.pending && !m.failed).length;

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: 'calc(100vh - 100px)' }}>
      <PageHeaderBanner
        icon={<SmartToyOutlinedIcon sx={{ color: theme.palette.primary.main, fontSize: 28 }} />}
        title="Assistant IA"
        subtitle="Votre analyste SOC augmenté — pose une question, obtiens une réponse contextualisée."
        badges={[
          {
            icon: <AutoAwesomeOutlinedIcon />,
            label: 'BETA',
            active: true,
            activeColor: severityColors.info,
          },
        ]}
      />

      {context?.summary && (
        <Alert
          severity="info"
          sx={{ mb: 2 }}
          icon={<LinkOutlinedIcon fontSize="small" />}
          action={
            <IconButton size="small" onClick={dismissContext} aria-label="Retirer le contexte">
              <CloseIcon fontSize="small" />
            </IconButton>
          }
        >
          Contexte : {context.summary}
        </Alert>
      )}

      <Box sx={{ flexGrow: 1, minHeight: 0, display: 'flex', gap: 2.5 }}>
        <Stack spacing={2} sx={{ width: 280, flexShrink: 0, overflowY: 'auto' }}>
          <Paper variant="outlined" sx={{ p: 2, borderRadius: 3 }}>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1.5 }}>
              <AutoAwesomeOutlinedIcon fontSize="small" sx={{ color: severityColors.info }} />
              <Typography
                variant="caption"
                sx={{ fontWeight: 800, letterSpacing: 0.5, textTransform: 'uppercase' }}
              >
                Suggestions rapides
              </Typography>
            </Stack>
            <Stack spacing={1}>
              {SUGGESTIONS.map((s) => (
                <ActionCard
                  key={s.title}
                  icon={s.icon}
                  title={s.title}
                  description={s.question}
                  color={s.color}
                  disabled={sending}
                  onClick={() => void send(s.question)}
                />
              ))}
            </Stack>
          </Paper>

          <Paper variant="outlined" sx={{ p: 2, borderRadius: 3 }}>
            <Typography
              variant="caption"
              color="text.secondary"
              sx={{ fontWeight: 800, letterSpacing: 0.5, textTransform: 'uppercase', mb: 1.5, display: 'block' }}
            >
              Cette conversation
            </Typography>
            <Stack spacing={1}>
              <MiniStat
                icon={<ChatBubbleOutlineOutlinedIcon fontSize="small" />}
                label="Messages échangés"
                value={String(messages.length)}
                color={severityColors.info}
              />
              <MiniStat
                icon={<SmartToyOutlinedIcon fontSize="small" />}
                label="Réponses de l'IA"
                value={String(assistantReplies)}
                color={severityColors.low}
              />
              <MiniStat
                icon={<LinkOutlinedIcon fontSize="small" />}
                label="Contexte lié"
                value={context ? 'Oui' : 'Non'}
                color={context ? severityColors.high : severityColors.medium}
              />
            </Stack>
          </Paper>
        </Stack>

        <Box sx={{ flexGrow: 1, minWidth: 0, display: 'flex', flexDirection: 'column' }}>
          <Paper
            variant="outlined"
            sx={{
              flexGrow: 1,
              overflowY: 'auto',
              p: 2.5,
              mb: 2,
              borderRadius: 3,
              display: 'flex',
              flexDirection: 'column',
              background: (t) =>
                `linear-gradient(180deg, ${alpha(severityColors.info, t.palette.mode === 'dark' ? 0.05 : 0.03)} 0%, ${t.palette.background.paper} 240px)`,
            }}
          >
            {messages.length === 0 && (
              <Box
                sx={{
                  m: 'auto',
                  width: '100%',
                  maxWidth: 420,
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  textAlign: 'center',
                }}
              >
                <AssistantMascot />
                <Typography variant="h6" sx={{ fontWeight: 800, mb: 0.5 }}>
                  Bonjour 👋
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Je suis ton assistant IA spécialisé en cybersécurité. Pose une question, ou pars
                  d'une suggestion à gauche.
                </Typography>
              </Box>
            )}

            <Stack spacing={2}>
              {messages.map((message) => (
                <Stack
                  key={message.id}
                  direction="row"
                  spacing={1.5}
                  sx={{
                    alignItems: 'flex-start',
                    flexDirection: message.role === 'user' ? 'row-reverse' : 'row',
                  }}
                >
                  <Avatar
                    sx={{
                      width: 32,
                      height: 32,
                      color: message.role === 'user' ? '#fff' : severityColors.info,
                      bgcolor:
                        message.role === 'user' ? 'primary.main' : alpha(severityColors.info, 0.16),
                      border: message.role === 'user' ? 'none' : '1px solid',
                      borderColor: alpha(severityColors.info, 0.4),
                      boxShadow:
                        message.role === 'user'
                          ? 'none'
                          : `0 0 10px 2px ${alpha(severityColors.info, 0.3)}`,
                    }}
                  >
                    {message.role === 'user' ? (
                      <PersonIcon fontSize="small" />
                    ) : (
                      <SmartToyOutlinedIcon fontSize="small" />
                    )}
                  </Avatar>
                  <Paper
                    variant="outlined"
                    sx={{
                      p: 1.5,
                      maxWidth: '70%',
                      borderRadius: 2.5,
                      bgcolor: message.failed
                        ? 'error.main'
                        : message.role === 'user'
                          ? 'primary.main'
                          : 'background.paper',
                      borderColor: message.failed
                        ? 'error.main'
                        : message.role === 'user'
                          ? 'primary.main'
                          : alpha(severityColors.info, 0.2),
                      color:
                        message.failed || message.role === 'user'
                          ? 'primary.contrastText'
                          : 'text.primary',
                    }}
                  >
                    {message.pending && !message.content ? (
                      <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                        <CircularProgress size={14} />
                        <Typography variant="body2" color="text.secondary">
                          L'assistant rédige une réponse…
                        </Typography>
                      </Stack>
                    ) : (
                      <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
                        {message.content}
                      </Typography>
                    )}
                  </Paper>
                </Stack>
              ))}
            </Stack>
            <div ref={bottomRef} />
          </Paper>

          <Stack direction="row" spacing={1}>
            <Tooltip title="Effacer la conversation">
              <span>
                <IconButton
                  onClick={clearConversation}
                  disabled={messages.length === 0 && !context}
                  aria-label="Effacer la conversation"
                  sx={{
                    width: 44,
                    height: 44,
                    border: '1px solid',
                    borderColor: (t) => alpha(t.palette.error.main, 0.35),
                    color: 'error.main',
                    bgcolor: (t) => alpha(t.palette.error.main, 0.08),
                    '&:hover': { bgcolor: (t) => alpha(t.palette.error.main, 0.16) },
                  }}
                >
                  <DeleteSweepIcon fontSize="small" />
                </IconButton>
              </span>
            </Tooltip>
            <TextField
              fullWidth
              multiline
              maxRows={4}
              size="small"
              placeholder="Écris ton message… (Entrée pour envoyer, Maj+Entrée pour une nouvelle ligne)"
              value={input}
              disabled={sending}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault();
                  void send(input);
                }
              }}
              sx={{
                '& .MuiOutlinedInput-root': {
                  borderRadius: 3,
                  bgcolor: (t) => alpha(severityColors.info, t.palette.mode === 'dark' ? 0.06 : 0.03),
                  '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
                    borderColor: severityColors.info,
                    boxShadow: `0 0 0 3px ${alpha(severityColors.info, 0.15)}`,
                  },
                },
              }}
            />
            <Tooltip title="Envoyer">
              <span>
                <IconButton
                  disabled={sending || !input.trim()}
                  onClick={() => void send(input)}
                  aria-label="Envoyer"
                  sx={{
                    width: 44,
                    height: 44,
                    color: '#fff',
                    bgcolor: '#2f81f7',
                    boxShadow: '0 0 16px rgba(47,129,247,0.45)',
                    '&:hover': { bgcolor: '#1f6feb', boxShadow: '0 0 20px rgba(47,129,247,0.6)' },
                    '&.Mui-disabled': { color: 'text.disabled', bgcolor: 'action.disabledBackground', boxShadow: 'none' },
                  }}
                >
                  {sending ? <CircularProgress size={20} color="inherit" /> : <SendIcon fontSize="small" />}
                </IconButton>
              </span>
            </Tooltip>
          </Stack>
          <Typography
            variant="caption"
            color="text.secondary"
            sx={{ textAlign: 'center', mt: 1, opacity: 0.8 }}
          >
            Les réponses de l'IA se basent sur les données disponibles et peuvent contenir des
            inexactitudes.
          </Typography>
        </Box>
      </Box>
    </Box>
  );
}

export default AssistantPage;
