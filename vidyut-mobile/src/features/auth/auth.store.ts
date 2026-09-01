import { create } from 'zustand';
import { User } from './auth.types';
import { tokenStorage } from '../../services/tokenStorage';
import { setUnauthorizedHandler } from '../../services/apiClient';

interface AuthState {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (user: User, token: string) => Promise<void>;
  logout: () => Promise<void>;
  loadPersistedAuth: () => Promise<void>;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  token: null,
  isAuthenticated: false,
  isLoading: true,

  login: async (user: User, token: string) => {
    await tokenStorage.saveToken(token);
    await tokenStorage.saveUser(user);
    set({ user, token, isAuthenticated: true, isLoading: false });
  },

  logout: async () => {
    await tokenStorage.clearAll();
    set({ user: null, token: null, isAuthenticated: false, isLoading: false });
  },

  loadPersistedAuth: async () => {
    set({ isLoading: true });
    try {
      const token = await tokenStorage.getToken();
      const user = await tokenStorage.getUser();

      if (token && user) {
        set({ user, token, isAuthenticated: true, isLoading: false });
        return;
      }
      set({ user: null, token: null, isAuthenticated: false, isLoading: false });
    } catch {
      set({ user: null, token: null, isAuthenticated: false, isLoading: false });
    }
  },
}));

setUnauthorizedHandler(() => {
  useAuthStore.setState({ user: null, token: null, isAuthenticated: false, isLoading: false });
});
