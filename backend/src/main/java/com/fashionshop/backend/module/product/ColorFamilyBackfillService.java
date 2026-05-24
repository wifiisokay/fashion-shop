package com.fashionshop.backend.module.product;

import com.fashionshop.backend.domain.ProductColor;
import com.fashionshop.backend.domain.repository.ProductColorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ColorFamilyBackfillService {

    private final ProductColorRepository colorRepository;

    @Transactional
    public Result run() {
        int backfilledColors = 0;

        for (ProductColor color : colorRepository.findAll()) {
            String derived = ColorFamilyDeriver.derive(color.getColorCode());
            if (!Objects.equals(derived, color.getColorFamily())) {
                color.setColorFamily(derived);
                backfilledColors++;
            }
        }
        colorRepository.flush();

        log.info("[COLOR_BACKFILL] backfilledColors={}", backfilledColors);
        return new Result(backfilledColors, 0);
    }

    public record Result(int backfilledColors, int syncedProducts) {
    }
}
