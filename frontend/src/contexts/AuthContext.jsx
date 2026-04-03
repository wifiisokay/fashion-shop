/* eslint-disable react-refresh/only-export-components */
import { createContext, useContext, useState, useEffect } from 'react';
import { authApi } from '../api/authApi';

const AuthContext = createContext(null);
const USER_STORAGE_KEY = 'user';

const normalizeUser = (rawUser) => {
  if (!rawUser) return null;

  return {
    userId: rawUser.userId ?? rawUser.id ?? null,
    fullName: rawUser.fullName ?? rawUser.name ?? '',
    email: rawUser.email ?? '',
    role: rawUser.role ?? 'CUSTOMER',
    avatarUrl: rawUser.avatarUrl ?? null,
  };
};

const getStoredUser = () => {
  try {
    const raw = localStorage.getItem(USER_STORAGE_KEY);
    return raw ? normalizeUser(JSON.parse(raw)) : null;
  } catch {
    return null;
  }
};

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(getStoredUser);
  const [isLoading, setIsLoading] = useState(true);

  const setAuthUser = (rawUser) => {
    const nextUser = normalizeUser(rawUser);
    setUser(nextUser);

    if (nextUser) {
      localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(nextUser));
    } else {
      localStorage.removeItem(USER_STORAGE_KEY);
    }
  };

  useEffect(() => {
    let isMounted = true;

    const bootstrapAuth = async () => {
      try {
        const response = await authApi.me();
        if (isMounted) {
          setAuthUser(response.data?.data);
        }
      } catch {
        if (isMounted) {
          setAuthUser(null);
        }
      } finally {
        if (isMounted) {
          setIsLoading(false);
        }
      }
    };

    bootstrapAuth();

    const onUnauthorized = () => {
      if (isMounted) {
        setAuthUser(null);
      }
    };

    window.addEventListener('auth:unauthorized', onUnauthorized);

    return () => {
      isMounted = false;
      window.removeEventListener('auth:unauthorized', onUnauthorized);
    };
  }, []);

  const login = (userData) => {
    setAuthUser(userData);
  };

  const logout = async (options = {}) => {
    const { skipRequest = false } = options;

    if (!skipRequest) {
      try {
        await authApi.logout();
      } catch {
        // Keep client state cleanup even when backend logout fails.
      }
    }

    setAuthUser(null);
  };

  const isAuthenticated = !!user;

  return (
    <AuthContext.Provider value={{ user, isAuthenticated, isLoading, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
