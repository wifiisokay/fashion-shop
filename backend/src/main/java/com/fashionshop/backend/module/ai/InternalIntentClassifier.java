package com.fashionshop.backend.module.ai;

import com.fashionshop.backend.domain.ChatMessage;
import com.fashionshop.backend.module.ai.dto.ProductContextDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class InternalIntentClassifier {

    private final IntentClassifier intentClassifier;

    public Classification classify(String message, ProductContextDto productContext, List<ChatMessage> recentMessages,
                                   String occasionTag, String styleTag) {
        ChatIntent baseIntent = intentClassifier.classify(message, recentMessages);
        String normalized = normalizeVi(message);
        boolean hasProductContext = productContext != null && productContext.getProductId() != null;
        boolean hasMentionedProduct = !ProductSearchDictionary.productTerms(message).isEmpty();
        boolean asksOutfit = containsAny(normalized, " phoi ", " mac voi ", " mix ", " outfit ");
        boolean asksDetail = containsAny(normalized, " chat lieu ", " vai ", " hop ", " phong cach ", " size ",
            " mau nao ", " mau gi ", " form ", " trong dip ", " de mac ", " hop voi ",
            " mac dip nao ", " mac vao dip nao ", " dung vao dip nao ")
            || normalized.contains(" san pham nay ") || normalized.contains(" cai nay ") || normalized.contains(" mau nay ");
        boolean asksPolicy = containsAny(normalized, " doi tra ", " chinh sach ", " bao hanh ", " van chuyen ", " ship ", " giao hang ");
        boolean asksSmalltalk = baseIntent == ChatIntent.CHITCHAT;

        if (baseIntent == ChatIntent.ORDER_INQUIRY || baseIntent == ChatIntent.RETURN_SUPPORT) {
            return new Classification(InternalChatIntent.SHOP_POLICY, baseIntent, "ORDER_OR_RETURN");
        }
        if (asksPolicy || baseIntent == ChatIntent.GENERAL_SUPPORT) {
            return new Classification(InternalChatIntent.SHOP_POLICY, ChatIntent.GENERAL_SUPPORT, "POLICY");
        }
        if (asksSmalltalk) {
            return new Classification(InternalChatIntent.SMALLTALK, ChatIntent.CHITCHAT, "SMALLTALK");
        }

        if (hasProductContext && asksDetail && !asksOutfit) {
            return new Classification(InternalChatIntent.PRODUCT_DETAIL_QA, ChatIntent.GENERAL_SUPPORT, "DETAIL");
        }

        if (asksOutfit) {
            if ((hasProductContext || hasMentionedProduct) && occasionTag != null) {
                return new Classification(InternalChatIntent.OUTFIT_BY_PRODUCT_AND_OCCASION, ChatIntent.OUTFIT_SUGGEST, "OUTFIT");
            }
            if (hasProductContext || hasMentionedProduct) {
                return new Classification(InternalChatIntent.OUTFIT_BY_PRODUCT, ChatIntent.OUTFIT_SUGGEST, "OUTFIT");
            }
            if (occasionTag != null) {
                return new Classification(InternalChatIntent.OUTFIT_BY_OCCASION, ChatIntent.OUTFIT_SUGGEST, "OUTFIT");
            }
        }

        if (styleTag != null && !hasProductContext && baseIntent == ChatIntent.PRODUCT_SEARCH) {
            return new Classification(InternalChatIntent.STYLE_ADVICE, ChatIntent.GENERAL_SUPPORT, "STYLE");
        }

        if (baseIntent == ChatIntent.PRODUCT_SEARCH) {
            return new Classification(InternalChatIntent.PRODUCT_SEARCH, ChatIntent.PRODUCT_SEARCH, "SEARCH");
        }

        if (baseIntent == ChatIntent.OUTFIT_SUGGEST) {
            return new Classification(InternalChatIntent.OUTFIT_BY_PRODUCT, ChatIntent.OUTFIT_SUGGEST, "OUTFIT");
        }

        if (baseIntent == ChatIntent.OUT_OF_SCOPE) {
            return new Classification(InternalChatIntent.OUT_OF_SCOPE, ChatIntent.OUT_OF_SCOPE, "OUT_OF_SCOPE");
        }

        return new Classification(InternalChatIntent.SMALLTALK, ChatIntent.GENERAL_SUPPORT, "DEFAULT");
    }

    private boolean containsAny(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeVi(String input) {
        if (input == null) {
            return "";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .replace('đ', 'd')
            .replace('Đ', 'D')
            .toLowerCase(Locale.ROOT)
            .replace('-', ' ')
            .trim();
        return " " + normalized + " ";
    }

    public record Classification(InternalChatIntent internalIntent, ChatIntent responseIntent, String questionType) {
    }
}
