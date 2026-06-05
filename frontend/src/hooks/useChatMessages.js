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
  products: msg.products || [],
  outfitCombos: msg.outfitCombos || [],
  styleTips: msg.styleTips || [],
  context: msg.context || null,
  isFromFallback: msg.isFromFallback || false,
  suggestedQuestions: msg.suggestedQuestions || [],
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
    mutationFn: async ({ text, context }) => {
      const currentPageContext = context || readPageContext(location.pathname);
      if (isAuthenticated) {
        return chatApi.sendMessage(text, currentPageContext);
      }
      throw new Error('LOGIN_REQUIRED');
    },
    onMutate: ({ text }) => {
      lastFailedTextRef.current = null;
      setLocalMessages((prev) => [...prev, { role: 'user', text, localOnly: true }]);
      return { text };
    },
    onSuccess: (response, variables) => {
      if (!response) return;
      const text = variables?.text || '';
      const assistantMessage = normalizeMessage(response);
      if (isAuthenticated) {
        setLocalMessages((prev) => prev.filter((msg) => !msg.localOnly));
        historyQuery.refetch();
      } else {
        setLocalMessages((prev) => [...prev, assistantMessage]);
      }
    },
    onError: (_error, variables) => {
      const text = variables?.text || '';
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

  const sendMessage = useCallback((text, context = null) => {
    if (!isAuthenticated || !text?.trim() || sendMutation.isPending) return;
    sendMutation.mutate({ text: text.trim(), context });
  }, [isAuthenticated, sendMutation]);

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
