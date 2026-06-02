/* eslint-disable react-refresh/only-export-components */
import { createContext, useContext, useState, useEffect } from 'react';
import { authApi } from '../api/authApi';

const AuthContext = createContext(null);

/**
 * Normalize user object từ backend response.
 * Backend trả: { userId, fullName, email, role, avatarUrl, status, createdAt }
 * AuthContext giữ cùng shape này để ProfilePage không cần transform thêm.
 */
const normalizeUser = (rawUser) => {
  if (!rawUser) return null;

  return {
    userId:    rawUser.userId   ?? rawUser.id   ?? null,
    fullName:  rawUser.fullName ?? rawUser.name ?? '',   // backend: fullName
    email:     rawUser.email    ?? '',
    role:      rawUser.role     ?? 'CUSTOMER',
    avatarUrl: rawUser.avatarUrl ?? null,                // backend: avatarUrl
    status:    rawUser.status   ?? 'ACTIVE',
    createdAt: rawUser.createdAt ?? null,
  };
};

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [isLoading, setIsLoading] = useState(true);

  const setAuthUser = (rawUser) => {
    const nextUser = normalizeUser(rawUser);
    setUser(nextUser);

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
