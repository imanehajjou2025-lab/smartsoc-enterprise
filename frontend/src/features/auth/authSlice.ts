import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { problemDetail, refreshSession } from '../../shared/api/client';
import {
  clearTokens,
  getRefreshToken,
  setAccessToken,
  setRefreshToken,
} from '../../shared/api/tokens';
import { fetchMe, loginRequest, logoutRequest, type SessionUser } from './authApi';

export type AuthStatus = 'initializing' | 'authenticated' | 'anonymous';

export interface AuthState {
  user: SessionUser | null;
  status: AuthStatus;
  loginError: string | null;
  loginPending: boolean;
}

const initialState: AuthState = {
  user: null,
  status: 'initializing',
  loginError: null,
  loginPending: false,
};

/** Au chargement de l'app : restaure la session via le refresh token s'il existe. */
export const bootstrapSession = createAsyncThunk<SessionUser | null>('auth/bootstrap', async () => {
  if (!getRefreshToken()) {
    return null;
  }
  try {
    await refreshSession();
    return await fetchMe();
  } catch {
    clearTokens();
    return null;
  }
});

export const login = createAsyncThunk<
  SessionUser,
  { username: string; password: string },
  { rejectValue: string }
>('auth/login', async ({ username, password }, { rejectWithValue }) => {
  try {
    const tokens = await loginRequest(username, password);
    setAccessToken(tokens.accessToken);
    setRefreshToken(tokens.refreshToken);
    return await fetchMe();
  } catch (error) {
    return rejectWithValue(problemDetail(error, 'Connexion impossible. Réessayez.'));
  }
});

export const logout = createAsyncThunk<void>('auth/logout', async () => {
  const refreshToken = getRefreshToken();
  if (refreshToken) {
    // Meilleur effort : la purge locale a lieu quoi qu'il arrive.
    await logoutRequest(refreshToken).catch(() => undefined);
  }
  clearTokens();
});

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(bootstrapSession.fulfilled, (state, action) => {
        state.user = action.payload;
        state.status = action.payload ? 'authenticated' : 'anonymous';
      })
      .addCase(bootstrapSession.rejected, (state) => {
        state.user = null;
        state.status = 'anonymous';
      })
      .addCase(login.pending, (state) => {
        state.loginPending = true;
        state.loginError = null;
      })
      .addCase(login.fulfilled, (state, action) => {
        state.loginPending = false;
        state.user = action.payload;
        state.status = 'authenticated';
      })
      .addCase(login.rejected, (state, action) => {
        state.loginPending = false;
        state.loginError = action.payload ?? 'Connexion impossible. Réessayez.';
      })
      .addCase(logout.fulfilled, (state) => {
        state.user = null;
        state.status = 'anonymous';
      });
  },
});

export default authSlice.reducer;
