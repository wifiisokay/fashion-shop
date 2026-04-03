import { useState, useRef, useEffect } from 'react';
import { chatApi } from '../api/chatApi';

export const useChatMessages = () => {
  const [messages, setMessages] = useState([
    { 
      role: 'model', 
      text: 'Xin chào! Mình là trợ lý thời trang của Fashion Shop. Mình có thể giúp bạn phối đồ hay tìm kiếm trang phục phù hợp cho dịp nào không?' 
    }
  ]);
  const [isLoading, setIsLoading] = useState(false);
  const chatSessionRef = useRef(null);

  // Khởi tạo session chat một lần khi hook được mount
  useEffect(() => {
    if (!chatSessionRef.current) {
      try {
        chatSessionRef.current = chatApi.createSession();
      } catch (error) {
        console.warn('Không khởi tạo được phiên chat AI:', error);
      }
    }
  }, []);

  const sendMessage = async (text) => {
    if (!text.trim()) return;

    // Thêm tin nhắn của user vào UI ngay lập tức
    setMessages((prev) => [...prev, { role: 'user', text }]);
    setIsLoading(true);

    try {
      if (!chatSessionRef.current) {
        throw new Error('CHAT_UNAVAILABLE');
      }

      // Gọi API Gemini thông qua SDK
      const response = await chatSessionRef.current.sendMessage({ message: text });
      
      // Thêm phản hồi của AI vào UI
      setMessages((prev) => [...prev, { role: 'model', text: response.text }]);
    } catch (error) {
      console.error('Lỗi khi gọi Gemini API:', error);

      const friendlyMessage =
        error.code === 'MISSING_GEMINI_KEY' || error.message === 'CHAT_UNAVAILABLE'
          ? 'Tính năng tư vấn AI chưa được cấu hình API key. Vui lòng thử lại sau.'
          : 'Xin lỗi, hiện tại hệ thống tư vấn đang bận. Bạn vui lòng thử lại sau nhé!';

      setMessages((prev) => [
        ...prev, 
        { role: 'model', text: friendlyMessage }
      ]);
    } finally {
      setIsLoading(false);
    }
  };

  return {
    messages,
    isLoading,
    sendMessage,
  };
};
