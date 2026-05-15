import axiosInstance from './axiosInstance';

/**
 * Chat API — gọi Backend REST API (không còn gọi Gemini SDK trực tiếp).
 * Bảo mật: API key chỉ ở backend, FE không chạm tới.
 */
export const chatApi = {
  /** Authenticated: gửi message → nhận AI response */
  sendMessage: (content) =>
    axiosInstance.post('/api/chat/message', { content }),

  /** Guest: gửi message không cần đăng nhập (giới hạn intent) */
  sendGuestMessage: (content, history = []) =>
    axiosInstance.post('/api/chat/guest/message', { content, history }),

  /** Lấy/tạo session hôm nay */
  getTodaySession: () =>
    axiosInstance.get('/api/chat/session/today'),

  /** Lấy messages của session hôm nay */
  getTodayMessages: () =>
    axiosInstance.get('/api/chat/messages/today'),

  /** Danh sách sessions phân trang */
  getSessions: (page = 0, size = 10) =>
    axiosInstance.get('/api/chat/sessions', { params: { page, size } }),

  /** Messages của 1 session cụ thể */
  getSessionMessages: (sessionId) =>
    axiosInstance.get(`/api/chat/sessions/${sessionId}`),
};
