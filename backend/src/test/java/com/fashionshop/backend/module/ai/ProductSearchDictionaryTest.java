package com.fashionshop.backend.module.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductSearchDictionaryTest {

    @Test
    void expandsVietnameseAndEnglishProductSynonyms() {
        assertThat(ProductSearchDictionary.productTerms("t-shirt nam")).contains("áo thun", "ao thun");
        assertThat(ProductSearchDictionary.productTerms("áo nỉ nam")).contains("hoodie");
        assertThat(ProductSearchDictionary.productTerms("quần đùi đi biển")).contains("quần short", "quan short");
        assertThat(ProductSearchDictionary.productTerms("áo linen oversized")).contains("linen", "oversized");
    }

    @Test
    void resolvesOccasionSynonyms() {
        assertThat(ProductSearchDictionary.occasionTag("đi làm")).isEqualTo("work");
        assertThat(ProductSearchDictionary.occasionTag("dao pho")).isEqualTo("street");
        assertThat(ProductSearchDictionary.occasionTag("đi biển")).isEqualTo("beach");
    }

    @Test
    void supportsRequestedCatalogQueryVocabulary() {
        assertThat(ProductSearchDictionary.productTerms("áo polo nam mặc với gì")).contains("ao polo");
        assertThat(ProductSearchDictionary.productTerms("áo sơ mi trắng nam đi làm dưới 500k")).contains("ao so mi");
        assertThat(ProductSearchDictionary.productTerms("quần tây nam phối với gì")).contains("quan tay");
        assertThat(ProductSearchDictionary.productTerms("áo thun nữ màu trắng dưới 300k")).contains("ao thun");
        assertThat(ProductSearchDictionary.productTerms("quần short nam đi biển")).contains("quan short");
        assertThat(ProductSearchDictionary.productTerms("váy nữ đi hẹn hò")).contains("vay dam");
        assertThat(ProductSearchDictionary.productTerms("hoodie nam mặc với gì")).contains("hoodie");
        assertThat(ProductSearchDictionary.productTerms("áo nỉ nam dưới 500k")).contains("hoodie");
        assertThat(ProductSearchDictionary.occasionTag("outfit nữ đi làm")).isEqualTo("work");
        assertThat(ProductSearchDictionary.occasionTag("outfit nam đi dạo phố")).isEqualTo("street");
    }
}
