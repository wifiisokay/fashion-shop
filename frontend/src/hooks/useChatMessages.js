import { useState, useEffect, useCallback, useRef } from 'react';
import { chatApi } from '../api/chatApi';
import { useAuth } from '../contexts/AuthContext';

/**
 * Custom hook quản lý chat messages.
 * - Authenticated user: gọi /api/chat/message, load lịch sử từ DB
 * - Guest: gọi /api/chat/guest/message, giữ history trong memory
 */
export const useChatMessages = () => {
  const { user } = useAuth();
  const isAuthenticated = !!user;

  const [messages, setMessages] = useState([
    {
      role: 'assistant',
      text: 'Xin chào! Mình là trợ lý thời trang AI của Fashion Shop. 👋\nBạn cần mình tư vấn phối đồ, tìm sản phẩm, hay hỗ trợ đơn hàng?',
      suggestedQuestions: ['Tìm áo thun nam', 'Gợi ý outfit đi chơi', 'Chính sách đổi trả'],
    },
  ]);
  const [isLoading, setIsLoading] = useState(false);
  const guestHistoryRef = useRef([]); // Track guest conversation for context

  // Load today's messages khi user đã đăng nhập
  useEffect(() => {
    if (!isAuthenticated) return;

    const loadHistory = async () => {
      try {
        const { data } = await chatApi.getTodayMessages();
        const history = data?.data;
        if (history && history.length > 0) {
          const mapped = history.map((msg) => ({
            role: msg.role === 'user' ? 'user' : 'assistant',
            text: msg.content,
            products: msg.products || null,
            suggestedQuestions: msg.suggestedQuestions || null,
            intent: msg.intent || null,
          }));
          // Thêm welcome message ở đầu
          setMessages([
            {
              role: 'assistant',
              text: 'Xin chào! Mình là trợ lý thời trang AI của Fashion Shop. 👋\nBạn cần mình tư vấn phối đồ, tìm sản phẩm, hay hỗ trợ đơn hàng?',
            },
            ...mapped,
          ]);
        }
      } catch (error) {
        console.warn('Không tải được lịch sử chat:', error);
      }
    };

    loadHistory();
  }, [isAuthenticated]);

  const sendMessage = useCallback(
    async (text) => {
      if (!text.trim()) return;

      // Thêm user message vào UI ngay
      setMessages((prev) => [...prev, { role: 'user', text }]);
      setIsLoading(true);

      try {
        let response;

        if (isAuthenticated) {
          // Authenticated user → gọi backend có lưu session
          const { data } = await chatApi.sendMessage(text);
          response = data?.data;
        } else {
          // Guest → gọi guest endpoint với history context
          const { data } = await chatApi.sendGuestMessage(text, guestHistoryRef.current);
          response = data?.data;

          // Update guest history cho context
          guestHistoryRef.current = [
            ...guestHistoryRef.current,
            { role: 'user', text },
            { role: 'model', text: response?.content || '' },
          ].slice(-10); // Giữ tối đa 10 messages
        }

        if (response) {
          setMessages((prev) => [
            ...prev,
            {
              role: 'assistant',
              text: response.content,
              products: response.products || null,
              suggestedQuestions: response.suggestedQuestions || null,
              intent: response.intent || null,
            },
          ]);
        }
      } catch (error) {
        console.error('Chat error:', error);
        setMessages((prev) => [
          ...prev,
          {
            role: 'assistant',
            text: 'Xin lỗi, hệ thống AI đang bận. Vui lòng thử lại sau nhé! 🙏',
          },
        ]);
      } finally {
        setIsLoading(false);
      }
    },
    [isAuthenticated]
  );

  return {
    messages,
    isLoading,
    sendMessage,
  };
};
