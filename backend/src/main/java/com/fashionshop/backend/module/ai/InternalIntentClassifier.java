package com.fashionshop.backend.module.ai;

import com.fashionshop.backend.domain.ChatMessage;
import com.fashionshop.backend.module.ai.dto.ProductContextDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InternalIntentClassifier {

    private final IntentClassifier intentClassifier;

    public Classification classify(String message, ProductContextDto productContext, List<ChatMessage> recentMessages,
                                   String occasionTag, String styleTag) {
        return classify(message, productContext, recentMessages, occasionTag, styleTag,
                intentClassifier.classify(message, recentMessages));
    }

    public Classification classify(String message, ProductContextDto productContext, List<ChatMessage> recentMessages,
                                   String occasionTag, String styleTag, ChatIntent baseIntent) {
        String normalized = VietnameseTextNormalizer.padded(message);
        boolean hasProductContext = productContext != null && productContext.getProductId() != null;
        boolean hasMentionedProduct = !ProductSearchDictionary.productTerms(message).isEmpty();
        boolean hasFashionSignal = hasFashionSignal(normalized);
        boolean hasExplicitOutfitSignal = containsAny(normalized,
            " phoi ", " phoi do ", " mac voi ", " mac voi gi ", " ket hop ", " mix ",
            " outfit ", " set do ", " nen mac gi ", " mac gi ");
        boolean hasExplicitSearchSignal = containsAny(normalized,
            " tim ", " xem ", " mua ", " co ", " shop co ", " con hang ",
            " gia ", " sale ", " giam gia ", " duoi ", " san pham nao ");
        boolean hasGenericSuggest = containsAny(normalized, " goi y ", " tu van ");
        boolean asksOccasionRecommendation = occasionTag != null
            && containsAny(normalized, " de mac ", " nen mac ", " mac di ", " di choi ", " di lam ",
                " ngoai troi ", " hop di ", " cho toi ", " goi y ");
        boolean asksDetail = containsAny(normalized, " chat lieu ", " vai ", " hop ", " phong cach ", " size ",
            " mau nao ", " mau gi ", " form ", " trong dip ", " de mac ", " hop voi ",
            " mac dip nao ", " mac vao dip nao ", " dung vao dip nao ")
            || containsAny(normalized, " san pham nay ", " cai nay ", " mau nay ");
        boolean asksPolicy = containsAny(normalized, " doi tra ", " chinh sach ", " bao hanh ", " van chuyen ",
            " ship ", " giao hang ", " thanh toan ", " vnpay ", " cod ");

        if (baseIntent == ChatIntent.ORDER_INQUIRY || baseIntent == ChatIntent.RETURN_SUPPORT) {
            return new Classification(InternalChatIntent.SHOP_POLICY, baseIntent, "ORDER_OR_RETURN");
        }
        if (asksPolicy || baseIntent == ChatIntent.GENERAL_SUPPORT) {
            return new Classification(InternalChatIntent.SHOP_POLICY, ChatIntent.GENERAL_SUPPORT, "POLICY");
        }
        if (hasProductContext && asksDetail && !hasExplicitOutfitSignal) {
            return new Classification(InternalChatIntent.PRODUCT_DETAIL_QA, ChatIntent.GENERAL_SUPPORT, "DETAIL");
        }
        if (hasExplicitOutfitSignal || (baseIntent == ChatIntent.OUTFIT_SUGGEST && !hasGenericSuggest)) {
            if ((hasProductContext || hasMentionedProduct) && occasionTag != null) {
                return new Classification(InternalChatIntent.OUTFIT_BY_PRODUCT_AND_OCCASION, ChatIntent.OUTFIT_SUGGEST, "OUTFIT");
            }
            if (hasProductContext || hasMentionedProduct || hasRoleKeyword(normalized)) {
                return new Classification(InternalChatIntent.OUTFIT_BY_PRODUCT, ChatIntent.OUTFIT_SUGGEST, "OUTFIT");
            }
            if (occasionTag != null) {
                return new Classification(InternalChatIntent.OUTFIT_BY_OCCASION, ChatIntent.OUTFIT_SUGGEST, "OUTFIT");
            }
            return new Classification(InternalChatIntent.STYLE_ADVICE, ChatIntent.GENERAL_SUPPORT, "STYLE");
        }
        if (hasExplicitSearchSignal || (hasGenericSuggest && (hasMentionedProduct || hasRoleKeyword(normalized)))) {
            return new Classification(InternalChatIntent.PRODUCT_SEARCH, ChatIntent.PRODUCT_SEARCH, "SEARCH");
        }
        if (asksOccasionRecommendation && !hasProductContext) {
            return new Classification(InternalChatIntent.OUTFIT_BY_OCCASION, ChatIntent.OUTFIT_SUGGEST, "OUTFIT");
        }
        if (baseIntent == ChatIntent.PRODUCT_SEARCH) {
            return new Classification(InternalChatIntent.PRODUCT_SEARCH, ChatIntent.PRODUCT_SEARCH, "SEARCH");
        }
        if (styleTag != null && !hasProductContext) {
            return new Classification(InternalChatIntent.STYLE_ADVICE, ChatIntent.GENERAL_SUPPORT, "STYLE");
        }
        if (baseIntent == ChatIntent.CHITCHAT && !hasFashionSignal) {
            return new Classification(InternalChatIntent.SMALLTALK, ChatIntent.CHITCHAT, "SMALLTALK");
        }
        if (baseIntent == ChatIntent.CHITCHAT && hasFashionSignal) {
            InternalChatIntent fallback = hasRoleKeyword(normalized)
                    ? InternalChatIntent.PRODUCT_SEARCH
                    : InternalChatIntent.STYLE_ADVICE;
            ChatIntent responseIntent = fallback == InternalChatIntent.PRODUCT_SEARCH
                    ? ChatIntent.PRODUCT_SEARCH
                    : ChatIntent.GENERAL_SUPPORT;
            return new Classification(fallback, responseIntent, fallback == InternalChatIntent.PRODUCT_SEARCH ? "SEARCH" : "STYLE",
                    true, InternalChatIntent.SMALLTALK);
        }
        if (baseIntent == ChatIntent.OUT_OF_SCOPE) {
            return new Classification(InternalChatIntent.OUT_OF_SCOPE, ChatIntent.OUT_OF_SCOPE, "OUT_OF_SCOPE");
        }
        return new Classification(InternalChatIntent.SMALLTALK, ChatIntent.GENERAL_SUPPORT, "DEFAULT");
    }

    private boolean hasFashionSignal(String normalized) {
        return containsAny(normalized,
            " goi y ", " phoi ", " phoi do ", " mix ", " mac voi gi ", " nen mac ", " tim ",
            " co ban ", " shop co ", " san pham ", " ao ", " quan ", " dam ", " vay ",
            " khoac ", " mau ", " size ", " thoang mat ", " toi ", " sang ", " nam ", " nu ",
            " di choi ", " di lam ", " mua he ", " mua dong ");
    }

    private boolean hasRoleKeyword(String normalized) {
        return containsAny(normalized,
            " ao ", " thun ", " polo ", " so mi ", " quan ", " jean ", " jeans ", " denim ",
            " kaki ", " short ", " shorts ", " dam ", " vay ", " khoac ", " jacket ", " cardigan ", " blazer ");
    }

    private boolean containsAny(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) {
                return true;
            }
        }
        return false;
    }

    public record Classification(InternalChatIntent internalIntent, ChatIntent responseIntent, String questionType,
                                 boolean smalltalkBlockedByFashionSignal,
                                 InternalChatIntent internalIntentBeforeFallback) {
        public Classification(InternalChatIntent internalIntent, ChatIntent responseIntent, String questionType) {
            this(internalIntent, responseIntent, questionType, false, internalIntent);
        }
    }
}
