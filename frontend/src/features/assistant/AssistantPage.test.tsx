import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { ThemeModeProvider } from '../../app/ThemeModeProvider';
import AssistantPage from './AssistantPage';
import { chatWithAssistant } from './assistantApi';

vi.mock('./assistantApi', async () => {
  const actual = await vi.importActual<typeof import('./assistantApi')>('./assistantApi');
  return { ...actual, chatWithAssistant: vi.fn() };
});

const mockedChat = vi.mocked(chatWithAssistant);

function renderPage() {
  return render(
    <ThemeModeProvider>
      <AssistantPage />
    </ThemeModeProvider>,
  );
}

beforeEach(() => {
  sessionStorage.clear();
  mockedChat.mockReset();
});

describe('AssistantPage', () => {
  it('shows starter suggestions when the conversation is empty', () => {
    renderPage();

    expect(screen.getByText('Explique-moi la technique MITRE ATT&CK T1110.')).toBeInTheDocument();
  });

  it('sends a message, shows the user bubble then the assistant reply', async () => {
    mockedChat.mockResolvedValue({
      reply: 'Voici une explication de T1110.',
      model: 'simulation',
      generatedAt: '2026-07-27T10:00:00Z',
    });
    renderPage();

    fireEvent.change(screen.getByPlaceholderText(/Écris ton message/), {
      target: { value: 'Bonjour' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Envoyer' }));

    expect(await screen.findByText('Bonjour')).toBeInTheDocument();
    expect(await screen.findByText('Voici une explication de T1110.')).toBeInTheDocument();
    expect(mockedChat).toHaveBeenCalledWith([{ role: 'user', content: 'Bonjour' }], null);
  });

  it('clicking a suggestion sends it directly', async () => {
    mockedChat.mockResolvedValue({
      reply: 'Réponse.',
      model: 'simulation',
      generatedAt: '2026-07-27T10:00:00Z',
    });
    renderPage();

    fireEvent.click(
      screen.getByText('Quelles sont les bonnes pratiques pour traiter un faux positif ?'),
    );

    expect(await screen.findByText('Réponse.')).toBeInTheDocument();
    expect(mockedChat).toHaveBeenCalledTimes(1);
  });

  it('shows a friendly message when the assistant is unavailable (503)', async () => {
    const error = Object.assign(new Error('Service Unavailable'), {
      isAxiosError: true,
      response: { status: 503, data: { detail: 'The AI assistant is currently unavailable' } },
    });
    mockedChat.mockRejectedValue(error);
    renderPage();

    fireEvent.change(screen.getByPlaceholderText(/Écris ton message/), {
      target: { value: 'Bonjour' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Envoyer' }));

    expect(
      await screen.findByText('The AI assistant is currently unavailable'),
    ).toBeInTheDocument();
  });

  it('disables the send button while empty and re-enables it once text is typed', () => {
    renderPage();

    expect(screen.getByRole('button', { name: 'Envoyer' })).toBeDisabled();

    fireEvent.change(screen.getByPlaceholderText(/Écris ton message/), {
      target: { value: 'Bonjour' },
    });

    expect(screen.getByRole('button', { name: 'Envoyer' })).toBeEnabled();
  });

  it('restores messages and context from sessionStorage on mount', () => {
    sessionStorage.setItem(
      'smartsoc.assistant.messages',
      JSON.stringify([{ id: '1', role: 'user', content: 'Message précédent' }]),
    );
    sessionStorage.setItem(
      'smartsoc.assistant.context',
      JSON.stringify({ alertId: 'a-1', summary: 'Alerte CRITICAL sur srv-web-01' }),
    );

    renderPage();

    expect(screen.getByText('Message précédent')).toBeInTheDocument();
    expect(screen.getByText(/Alerte CRITICAL sur srv-web-01/)).toBeInTheDocument();
  });

  it('clearing the conversation empties messages and removes the context', () => {
    sessionStorage.setItem(
      'smartsoc.assistant.messages',
      JSON.stringify([{ id: '1', role: 'user', content: 'Message précédent' }]),
    );
    sessionStorage.setItem('smartsoc.assistant.context', JSON.stringify({ summary: 'Contexte X' }));
    renderPage();

    fireEvent.click(screen.getByRole('button', { name: 'Effacer la conversation' }));

    expect(screen.queryByText('Message précédent')).not.toBeInTheDocument();
    expect(screen.queryByText(/Contexte X/)).not.toBeInTheDocument();
    expect(sessionStorage.getItem('smartsoc.assistant.context')).toBeNull();
  });
});
