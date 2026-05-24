import axiosInstance from './axiosInstance';

export const chatApi = {
  sendMessage: async (content, context = {}) => {
    const { data } = await axiosInstance.post('/api/chat/message', { content, ...context });
    return data?.data ?? null;
  },

  sendGuestMessage: async (content, history = [], context = {}) => {
    const { data } = await axiosInstance.post('/api/chat/guest/message', { content, history, ...context });
    return data?.data ?? null;
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
    });
    return data?.data ?? null;
  },
};
