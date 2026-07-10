import { describe, expect, it } from 'vitest';
import reducer, { bootstrapSession, login, logout, type AuthState } from './authSlice';
import type { SessionUser } from './authApi';

const admin: SessionUser = { username: 'admin', userId: 'u-1', role: 'ADMIN' };

const initial: AuthState = {
  user: null,
  status: 'initializing',
  loginError: null,
  loginPending: false,
};

describe('authSlice', () => {
  it('starts in initializing state (session restore in progress)', () => {
    expect(reducer(undefined, { type: 'noop' })).toEqual(initial);
  });

  it('becomes authenticated when bootstrap restores a session', () => {
    const state = reducer(initial, bootstrapSession.fulfilled(admin, 'req'));
    expect(state.status).toBe('authenticated');
    expect(state.user).toEqual(admin);
  });

  it('becomes anonymous when no session can be restored', () => {
    const state = reducer(initial, bootstrapSession.fulfilled(null, 'req'));
    expect(state.status).toBe('anonymous');
    expect(state.user).toBeNull();
  });

  it('stores the RFC 9457 detail on login failure and clears it on retry', () => {
    const failed = reducer(
      initial,
      login.rejected(null, 'req', { username: 'a', password: 'b' }, 'Invalid username or password'),
    );
    expect(failed.loginError).toBe('Invalid username or password');
    expect(failed.loginPending).toBe(false);

    const retrying = reducer(failed, login.pending('req', { username: 'a', password: 'b' }));
    expect(retrying.loginError).toBeNull();
    expect(retrying.loginPending).toBe(true);
  });

  it('clears the session on logout', () => {
    const authenticated = reducer(
      initial,
      login.fulfilled(admin, 'req', { username: 'a', password: 'b' }),
    );
    const state = reducer(authenticated, logout.fulfilled(undefined, 'req'));
    expect(state.status).toBe('anonymous');
    expect(state.user).toBeNull();
  });
});
