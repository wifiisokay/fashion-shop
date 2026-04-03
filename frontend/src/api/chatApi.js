import { GoogleGenAI } from '@google/genai';

let aiClient = null;

const getAiClient = () => {
  if (aiClient) return aiClient;

  const apiKey = import.meta.env.VITE_GEMINI_API_KEY;
  if (!apiKey) {
    const error = new Error('Thiếu VITE_GEMINI_API_KEY cho Chat AI');
    error.code = 'MISSING_GEMINI_KEY';
    throw error;
  }

  aiClient = new GoogleGenAI({ apiKey });
  return aiClient;
};

export const chatApi = {
  createSession: () => {
    const ai = getAiClient();
    return ai.chats.create({
      model: 'gemini-3-flash-preview',
      config: {
        systemInstruction: `Bạn là chuyên gia tư vấn thời trang của Fashion Shop.
        Nhiệm vụ của bạn:
        - Tư vấn cách phối đồ (mix & match) cho khách hàng.
        - Gợi ý trang phục casual theo mùa (xuân, hạ, thu, đông).
        - Gợi ý trang phục đi làm, đi chơi, dự tiệc.
        - Trả lời ngắn gọn, thân thiện, lịch sự và chuyên nghiệp.
        - Nếu khách hàng hỏi những vấn đề không liên quan đến thời trang, hãy khéo léo từ chối và hướng họ về chủ đề thời trang.`,
        temperature: 0.7,
      },
    });
  },
};
