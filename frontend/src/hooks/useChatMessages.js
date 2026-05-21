import { useCallback, useMemo, useRef, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useLocation } from 'react-router-dom';
import { chatApi } from '../api/chatApi';
import { useAuth } from '../contexts/AuthContext';
import { QUERY_KEYS } from '../constants/queryKeys';

const welcomeMessage = {
  role: 'assistant',
  text: 'Xin chào! Mình là Fashi, trợ lý thời trang của Fashion Shop. Bạn cần tìm sản phẩm, phối đồ hay hỗ trợ đơn hàng?',
  suggestedQuestions: ['Tìm áo thun nam', 'Gợi ý outfit đi chơi', 'Chính sách đổi trả'],
};

const normalizeMessage = (msg) => ({
  role: msg.role === 'user' ? 'user' : 'assistant',
  text: msg.content ?? msg.text ?? '',
  products: msg.products || null,
  outfitCombos: msg.outfitCombos || null,
  suggestedQuestions: msg.suggestedQuestions || null,
  intent: msg.intent || null,
  isError: msg.isError || false,
});

const readPageContext = (pathname) => {
  const match = pathname.match(/^\/products\/(\d+)/);
  if (!match) {
    return {};
  }
  let colorId = null;
  try {
    const raw = sessionStorage.getItem('chatProductContext');
    if (raw) {
      const parsed = JSON.parse(raw);
      if (Number(parsed?.productId) === Number(match[1]) && parsed?.colorId != null) {
        colorId = Number(parsed.colorId);
      }
    }
  } catch (_error) {
    colorId = null;
  }
  return colorId != null ? { productId: Number(match[1]), colorId } : { productId: Number(match[1]) };
};

export const useChatMessages = (enabled = false) => {
  const { user } = useAuth();
  const location = useLocation();
  const isAuthenticated = !!user;
  const guestHistoryRef = useRef([]);
  const lastFailedTextRef = useRef(null);
  const [localMessages, setLocalMessages] = useState([welcomeMessage]);

  const historyQuery = useQuery({
    queryKey: QUERY_KEYS.chatMessages('today'),
    queryFn: chatApi.getTodayMessages,
    enabled: enabled && isAuthenticated,
    staleTime: 0,
  });

  const messages = useMemo(() => {
    if (!isAuthenticated) {
      return localMessages;
    }
    const history = (historyQuery.data || []).map(normalizeMessage);
    const optimistic = localMessages.filter((msg) => msg.localOnly);
    return [welcomeMessage, ...history, ...optimistic];
  }, [historyQuery.data, isAuthenticated, localMessages]);

  const sendMutation = useMutation({
    mutationFn: async (text) => {
      const currentPageContext = readPageContext(location.pathname);
      if (isAuthenticated) {
        return chatApi.sendMessage(text, currentPageContext);
      }
      return chatApi.sendGuestMessage(text, guestHistoryRef.current, currentPageContext);
    },
    onMutate: (text) => {
      lastFailedTextRef.current = null;
      setLocalMessages((prev) => [...prev, { role: 'user', text, localOnly: true }]);
      return { text };
    },
    onSuccess: (response, text) => {
      if (!response) return;
      const assistantMessage = normalizeMessage(response);
      if (!isAuthenticated) {
        guestHistoryRef.current = [
          ...guestHistoryRef.current,
          { role: 'user', text },
          { role: 'model', text: response.content || '' },
        ].slice(-10);
      }
      if (isAuthenticated) {
        setLocalMessages((prev) => prev.filter((msg) => !msg.localOnly));
        historyQuery.refetch();
      } else {
        setLocalMessages((prev) => [...prev, assistantMessage]);
      }
    },
    onError: (_error, text) => {
      lastFailedTextRef.current = text;
      setLocalMessages((prev) => [
        ...prev,
        {
          role: 'assistant',
          text: 'Xin lỗi, hệ thống AI đang bận. Bạn có thể thử lại tin nhắn này.',
          isError: true,
          suggestedQuestions: ['Thử lại'],
        },
      ]);
    },
  });

  const sendMessage = useCallback((text) => {
    if (!text?.trim() || sendMutation.isPending) return;
    sendMutation.mutate(text.trim());
  }, [sendMutation]);

  const retryLast = useCallback(() => {
    if (lastFailedTextRef.current) {
      sendMessage(lastFailedTextRef.current);
    }
  }, [sendMessage]);

  return {
    messages,
    isLoading: sendMutation.isPending || historyQuery.isFetching,
    sendMessage,
    retryLast,
    isAuthenticated,
  };
};
