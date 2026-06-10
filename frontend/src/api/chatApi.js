import axiosInstance from './axiosInstance';

export const chatApi = {
  sendMessage: async (content, context = {}) => {
    const { signal, ...payload } = context;
    return axiosInstance.post(
      '/api/chat/message',
      { content, message: content, ...payload },
      {
        timeout: 40000,
        ...(signal ? { signal } : {}),
      }
    );
  },

  getTodaySession: async () => {
    const { data } = await axiosInstance.get('/api/chat/session/today');
    return data?.data ?? null;
  },

  getTodayMessages: async () => {
    const { data } = await axiosInstance.get('/api/chat/messages/today');
    return data?.data ?? [];
  },

  getSessions: async (page = 0, size = 10) => {
    const { data } = await axiosInstance.get('/api/chat/sessions', { params: { page, size } });
    return data?.data ?? null;
  },

  getSessionMessages: async (sessionId) => {
    const { data } = await axiosInstance.get(`/api/chat/sessions/${sessionId}`);
    return data?.data ?? [];
  },

  getOutfitSuggestions: async (productId, colorId, refresh = false) => {
    const { data } = await axiosInstance.get(`/api/products/${productId}/outfit-suggestions`, {
      params: { ...(colorId ? { colorId } : {}), ...(refresh ? { refresh: true } : {}) },
      timeout: 40000,
    });
    return data?.data ?? null;
  },
};
