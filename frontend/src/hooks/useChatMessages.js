import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useLocation } from 'react-router-dom';
import { chatApi } from '../api/chatApi';
import { useAuth } from '../contexts/AuthContext';
import { QUERY_KEYS } from '../constants/queryKeys';

const welcomeMessage = {
  id: 'welcome-message',
  role: 'assistant',
  text: 'Xin chào! Mình là Fashi, trợ lý thời trang của Fashion Shop. Bạn cần tìm sản phẩm, phối đồ hay hỗ trợ đơn hàng?',
  suggestedQuestions: ['Tìm áo thun nam', 'Gợi ý outfit đi chơi', 'Chính sách đổi trả'],
  products: [],
  outfitCombos: [],
  styleTips: [],
};

const normalizeChatResponse = (apiResult) => {
  if (!apiResult) {
    throw new Error('EMPTY_RESPONSE');
  }

  let dataPayload = null;
  if (apiResult.data !== undefined) {
    if (apiResult.data?.data !== undefined) {
      dataPayload = apiResult.data.data;
    } else {
      dataPayload = apiResult.data;
    }
  } else {
    dataPayload = apiResult;
  }

  if (!dataPayload || typeof dataPayload !== 'object') {
    throw new Error('INVALID_FORMAT');
  }

  const content = dataPayload.content ?? dataPayload.text ?? dataPayload.message;
  if (content === undefined || content === null) {
    throw new Error('MISSING_CONTENT');
  }

  return {
    id: dataPayload.id || ('msg-' + Date.now() + '-' + Math.random().toString(36).substr(2, 4)),
    role: dataPayload.role === 'user' ? 'user' : 'assistant',
    text: String(content),
    products: Array.isArray(dataPayload.products) ? dataPayload.products : [],
    outfitCombos: Array.isArray(dataPayload.outfitCombos) ? dataPayload.outfitCombos : [],
    styleTips: Array.isArray(dataPayload.styleTips) ? dataPayload.styleTips : [],
    context: dataPayload.context || null,
    isFromFallback: dataPayload.isFromFallback !== undefined ? !!dataPayload.isFromFallback : false,
    suggestedQuestions: Array.isArray(dataPayload.suggestedQuestions) ? dataPayload.suggestedQuestions : [],
    intent: dataPayload.intent || null,
    searchStatus: dataPayload.searchStatus || null,
    requestedKeyword: dataPayload.requestedKeyword || null,
    fallbackRole: dataPayload.fallbackRole || null,
    internalIntent: dataPayload.internalIntent || null,
    isError: !!dataPayload.isError,
  };
};

const normalizeMessage = (msg) => {
  try {
    return normalizeChatResponse(msg);
  } catch (e) {
    return {
      id: msg?.id || null,
      role: msg?.role === 'user' ? 'user' : 'assistant',
      text: String(msg?.content ?? msg?.text ?? ''),
      products: Array.isArray(msg?.products) ? msg.products : [],
      outfitCombos: Array.isArray(msg?.outfitCombos) ? msg.outfitCombos : [],
      styleTips: Array.isArray(msg?.styleTips) ? msg.styleTips : [],
      context: msg?.context || null,
      isFromFallback: !!msg?.isFromFallback,
      suggestedQuestions: Array.isArray(msg?.suggestedQuestions) ? msg.suggestedQuestions : [],
      intent: msg?.intent || null,
      searchStatus: msg?.searchStatus || null,
      requestedKeyword: msg?.requestedKeyword || null,
      fallbackRole: msg?.fallbackRole || null,
      internalIntent: msg?.internalIntent || null,
      isError: !!msg?.isError,
    };
  }
};

const getFriendlyErrorMessage = (error) => {
  if (!error) {
    return 'Xin lỗi, Fashi đang gặp sự cố khi xử lý câu hỏi này. Bạn thử hỏi lại giúp mình nhé.';
  }

  const isAxiosError = error.isAxiosError || (error.config && error.request);
  if (isAxiosError) {
    if (error.code === 'ECONNABORTED' || error.message?.toLowerCase().includes('timeout')) {
      return 'Fashi đang mất nhiều thời gian để phản hồi. Bạn thử gửi lại câu hỏi giúp mình nhé.';
    }

    if (error.code === 'ERR_CANCELED' || error.name === 'CanceledError') {
      return 'Yêu cầu phối đồ đã bị hủy. Bạn có thể thử lại nhé.';
    }

    const status = error.response?.status;
    if (status === 401) {
      return 'Bạn cần đăng nhập để tiếp tục sử dụng chatbot.';
    }
    if (status === 403) {
      return 'Tài khoản hiện không có quyền thực hiện thao tác này.';
    }
    if (status >= 500) {
      return 'Xin lỗi, hiện tại chatbot đang gặp lỗi tạm thời. Bạn thử lại sau ít phút nhé.';
    }

    if (!error.response) {
      return 'Không thể kết nối đến máy chủ. Bạn kiểm tra kết nối mạng hoặc thử lại sau nhé.';
    }
  }

  if (error.message === 'EMPTY_RESPONSE' || error.message === 'INVALID_FORMAT' || error.message === 'MISSING_CONTENT') {
    return 'Xin lỗi, phản hồi từ hệ thống không đúng định dạng. Bạn thử lại nhé.';
  }

  if (error.message === 'LOGIN_REQUIRED') {
    return 'Bạn cần đăng nhập để tiếp tục sử dụng chatbot.';
  }

  return 'Xin lỗi, Fashi đang gặp sự cố khi xử lý câu hỏi này. Bạn thử hỏi lại giúp mình nhé.';
};

const replaceMessageById = (messages, id, newMessage) => {
  if (!id) {
    return [...messages, newMessage];
  }
  const index = messages.findIndex((m) => m.id === id);
  if (index !== -1) {
    const updated = [...messages];
    updated[index] = { ...newMessage, id };
    return updated;
  }
  return [...messages, newMessage];
};

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
  const productId = Number(match[1]);
  return {
    productId,
    colorId,
    productContext: {
      productId,
      colorId,
    },
  };
};

export const useChatMessages = (enabled = false) => {
  const { user } = useAuth();
  const location = useLocation();
  const isAuthenticated = !!user;
  const lastFailedTextRef = useRef(null);
  const isSendingRef = useRef(false);
  const abortControllerRef = useRef(null);
  const activeRequestIdRef = useRef(null);
  const isMountedRef = useRef(true);

  const [localMessages, setLocalMessages] = useState([welcomeMessage]);
  const [isLoadingState, setIsLoadingState] = useState(false);

  useEffect(() => {
    isMountedRef.current = true;
    return () => {
      isMountedRef.current = false;
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }
    };
  }, []);

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
    mutationFn: async ({ text, context, signal }) => {
      const currentPageContext = context || readPageContext(location.pathname);
      if (isAuthenticated) {
        const response = await chatApi.sendMessage(text, { ...currentPageContext, signal });
        return normalizeChatResponse(response);
      }
      throw new Error('LOGIN_REQUIRED');
    },
    onMutate: ({ text, userMsgId, loadingMessageId }) => {
      lastFailedTextRef.current = null;

      const userMessage = {
        id: userMsgId,
        role: 'user',
        text,
        localOnly: true,
      };

      const loadingMessage = {
        id: loadingMessageId,
        role: 'assistant',
        text: 'Fashi đang suy nghĩ...',
        isLoading: true,
        localOnly: true,
        products: [],
        outfitCombos: [],
        styleTips: [],
        suggestedQuestions: [],
      };

      setLocalMessages((prev) => [...prev, userMessage, loadingMessage]);
      return { userMsgId, loadingMessageId };
    },
    onSuccess: async (response, variables, context) => {
      if (!response) return;
      const { userMsgId, loadingMessageId } = context || {};

      const assistantMessage = {
        ...response,
        id: loadingMessageId,
        localOnly: true,
      };

      if (isMountedRef.current) {
        setLocalMessages((prev) => replaceMessageById(prev, loadingMessageId, assistantMessage));
      }

      if (isAuthenticated) {
        try {
          await historyQuery.refetch();
        } catch (e) {
          console.error('Refetch history error:', e);
        } finally {
          if (isMountedRef.current) {
            setLocalMessages((prev) => prev.filter((msg) => msg.id !== loadingMessageId && msg.id !== userMsgId));
          }
        }
      }
    },
    onError: (error, variables, context) => {
      const { userMsgId, loadingMessageId } = context || {};
      const errorMessage = getFriendlyErrorMessage(error);

      const errorMsgObj = {
        id: loadingMessageId || 'error-' + Date.now(),
        role: 'assistant',
        text: errorMessage,
        isError: true,
        localOnly: true,
        suggestedQuestions: ['Thử lại'],
        products: [],
        outfitCombos: [],
        styleTips: [],
      };

      if (isMountedRef.current) {
        setLocalMessages((prev) => replaceMessageById(prev, loadingMessageId, errorMsgObj));
      }

      lastFailedTextRef.current = variables?.text || '';
    },
  });

  const sendMessage = useCallback(async (text, context = null) => {
    if (!isAuthenticated || !text?.trim() || isSendingRef.current || sendMutation.isPending || historyQuery.isFetching) return;

    const controller = new AbortController();
    abortControllerRef.current = controller;

    const requestId = 'req-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9);
    activeRequestIdRef.current = requestId;
    isSendingRef.current = true;
    setIsLoadingState(true);

    const userMsgId = 'user-' + Date.now() + '-' + Math.random().toString(36).substr(2, 4);
    const loadingMessageId = 'loading-' + Date.now() + '-' + Math.random().toString(36).substr(2, 4);

    try {
      await sendMutation.mutateAsync({
        text: text.trim(),
        context,
        userMsgId,
        loadingMessageId,
        signal: controller.signal,
      });
    } catch (error) {
      console.error('SendMessage error:', error);
    } finally {
      if (activeRequestIdRef.current === requestId && isMountedRef.current) {
        isSendingRef.current = false;
        setIsLoadingState(false);
      }
    }
  }, [isAuthenticated, sendMutation, historyQuery.isFetching]);

  const retryLast = useCallback(() => {
    if (lastFailedTextRef.current) {
      sendMessage(lastFailedTextRef.current);
    }
  }, [sendMessage]);

  return {
    messages,
    isLoading: isLoadingState || sendMutation.isPending || historyQuery.isFetching,
    sendMessage,
    retryLast,
    isAuthenticated,
  };
};
