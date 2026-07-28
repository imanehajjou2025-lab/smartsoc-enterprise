import { useEffect, useReducer, useRef, useState } from 'react';
import Alert from '@mui/material/Alert';
import Avatar from '@mui/material/Avatar';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import IconButton from '@mui/material/IconButton';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import CloseIcon from '@mui/icons-material/Close';
import DeleteSweepIcon from '@mui/icons-material/DeleteSweep';
import PersonIcon from '@mui/icons-material/Person';
import SendIcon from '@mui/icons-material/Send';
import SmartToyOutlinedIcon from '@mui/icons-material/SmartToyOutlined';
import axios from 'axios';
import { problemDetail } from '../../shared/api/client';
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

const STARTER_SUGGESTIONS = [
  'Comment analyser une alerte suspecte, étape par étape ?',
  'Explique-moi la technique MITRE ATT&CK T1110.',
  'Quelles sont les bonnes pratiques pour traiter un faux positif ?',
  "Aide-moi à rédiger un résumé d'incident pour ma hiérarchie.",
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

/** Page de dialogue avec l'assistant IA (ADR-008) : sans état côté backend — cette page est propriétaire de la conversation. */
function AssistantPage() {
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

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: 'calc(100vh - 140px)' }}>
      <Stack direction="row" spacing={1.5} sx={{ mb: 2, alignItems: 'center' }}>
        <Typography variant="h5" component="h2">
          Assistant IA
        </Typography>
        <Box sx={{ flexGrow: 1 }} />
        <Tooltip title="Effacer la conversation">
          <span>
            <IconButton
              size="small"
              onClick={clearConversation}
              disabled={messages.length === 0 && !context}
              aria-label="Effacer la conversation"
            >
              <DeleteSweepIcon fontSize="small" />
            </IconButton>
          </span>
        </Tooltip>
      </Stack>

      {context?.summary && (
        <Alert
          severity="info"
          sx={{ mb: 2 }}
          action={
            <IconButton size="small" onClick={dismissContext} aria-label="Retirer le contexte">
              <CloseIcon fontSize="small" />
            </IconButton>
          }
        >
          Contexte : {context.summary}
        </Alert>
      )}

      <Paper
        variant="outlined"
        sx={{
          flexGrow: 1,
          overflowY: 'auto',
          p: 2,
          mb: 2,
          display: 'flex',
          flexDirection: 'column',
        }}
      >
        {messages.length === 0 && (
          <Box sx={{ m: 'auto', textAlign: 'center', maxWidth: 480 }}>
            <SmartToyOutlinedIcon sx={{ fontSize: 40, color: 'text.secondary', mb: 1 }} />
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              Pose une question à l'assistant, ou essaie une suggestion :
            </Typography>
            <Stack
              direction="row"
              spacing={1}
              useFlexGap
              sx={{ flexWrap: 'wrap', justifyContent: 'center' }}
            >
              {STARTER_SUGGESTIONS.map((suggestion) => (
                <Chip
                  key={suggestion}
                  label={suggestion}
                  onClick={() => send(suggestion)}
                  sx={{ mb: 1 }}
                />
              ))}
            </Stack>
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
                  width: 30,
                  height: 30,
                  bgcolor: message.role === 'user' ? 'primary.main' : 'secondary.main',
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
                  bgcolor: message.failed
                    ? 'error.main'
                    : message.role === 'user'
                      ? 'primary.main'
                      : 'background.paper',
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
        />
        <IconButton
          color="primary"
          disabled={sending || !input.trim()}
          onClick={() => void send(input)}
          aria-label="Envoyer"
        >
          {sending ? <CircularProgress size={20} /> : <SendIcon />}
        </IconButton>
      </Stack>
    </Box>
  );
}

export default AssistantPage;
