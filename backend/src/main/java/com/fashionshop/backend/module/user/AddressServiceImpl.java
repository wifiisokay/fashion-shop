package com.fashionshop.backend.module.user;

import com.fashionshop.backend.domain.Address;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.domain.repository.AddressRepository;
import com.fashionshop.backend.domain.repository.UserRepository;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import com.fashionshop.backend.module.user.dto.request.AddressRequest;
import com.fashionshop.backend.module.user.dto.response.AddressResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    // ============================================================
    // GET
    // ============================================================

    @Override
    @Transactional(readOnly = true)
    public List<AddressResponse> getAddresses(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw BusinessException.notFound(ErrorCode.USER_NOT_FOUND);
        }
        return addressRepository
            .findByUser_IdOrderByIsDefaultDescCreatedAtDesc(userId)
            .stream()
            .map(AddressResponse::fromEntity)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AddressResponse getAddressById(Long addressId, Long userId) {
        Address address = addressRepository.findByIdAndUser_Id(addressId, userId)
            .orElseThrow(() -> BusinessException.notFound(ErrorCode.ADDRESS_NOT_FOUND));
        return AddressResponse.fromEntity(address);
    }

    // ============================================================
    // CREATE
    // ============================================================

    @Override
    @Transactional
    public AddressResponse createAddress(Long userId, AddressRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> BusinessException.notFound(ErrorCode.USER_NOT_FOUND));

        boolean isFirstAddress = !addressRepository.existsByUser_Id(userId);

        // Case 1: Địa chỉ đầu tiên → tự động là default
        // Case 2: Có địa chỉ cũ + request muốn set default → unset cũ trước
        // Case 3: Có địa chỉ cũ + request không set default → tạo bình thường
        boolean shouldBeDefault;
        if (isFirstAddress) {
            shouldBeDefault = true;
        } else if (request.isDefault()) {
            unsetAllDefaultForUser(userId);
            shouldBeDefault = true;
        } else {
            shouldBeDefault = false;
        }

        Address address = Address.builder()
            .user(user)
            .fullName(request.getFullName())
            .phone(request.getPhone())
            .province(request.getProvince())
            .provinceCode(request.getProvinceCode())
            .district(request.getDistrict())
            .districtCode(request.getDistrictCode())
            .ward(request.getWard())
            .wardCode(request.getWardCode())
            .street(request.getStreet())
            .isDefault(shouldBeDefault)
            .build();

        return AddressResponse.fromEntity(addressRepository.save(address));
    }

    // ============================================================
    // UPDATE
    // ============================================================

    @Override
    @Transactional
    public AddressResponse updateAddress(Long addressId, Long userId, AddressRequest request) {
        Address address = addressRepository.findByIdAndUser_Id(addressId, userId)
            .orElseThrow(() -> BusinessException.notFound(ErrorCode.ADDRESS_NOT_FOUND));

        // Logic isDefault khi update:
        // - request=true, chưa là default → unset cũ, set địa chỉ này làm default
        // - request=false, đang là default → GIỮ NGUYÊN true (không cho unset bằng update)
        // - request=false, không phải default → cập nhật bình thường
        if (request.isDefault() && !address.isDefault()) {
            unsetAllDefaultForUser(userId);
            address.setDefault(true);
        } else if (!request.isDefault() && address.isDefault()) {
            // Giữ nguyên — không cho phép unset default bằng endpoint update
            log.debug("Attempted to unset default via update for address {}. Ignored.", addressId);
        } else {
            address.setDefault(request.isDefault());
        }

        address.setFullName(request.getFullName());
        address.setPhone(request.getPhone());
        address.setProvince(request.getProvince());
        address.setProvinceCode(request.getProvinceCode());
        address.setDistrict(request.getDistrict());
        address.setDistrictCode(request.getDistrictCode());
        address.setWard(request.getWard());
        address.setWardCode(request.getWardCode());
        address.setStreet(request.getStreet());

        return AddressResponse.fromEntity(addressRepository.save(address));
    }

    // ============================================================
    // DELETE
    // ============================================================

    @Override
    @Transactional
    public void deleteAddress(Long addressId, Long userId) {
        Address address = addressRepository.findByIdAndUser_Id(addressId, userId)
            .orElseThrow(() -> BusinessException.notFound(ErrorCode.ADDRESS_NOT_FOUND));

        // Nếu xóa địa chỉ default và còn địa chỉ khác → chọn địa chỉ mới nhất làm default
        if (address.isDefault() && addressRepository.countByUser_Id(userId) > 1) {
            addressRepository
                .findByUser_IdOrderByIsDefaultDescCreatedAtDesc(userId)
                .stream()
                .filter(a -> !a.getId().equals(addressId))   // loại bỏ cái đang xóa
                .findFirst()
                .ifPresent(next -> {
                    next.setDefault(true);
                    addressRepository.save(next);
                });
        }

        addressRepository.delete(address);
    }

    // ============================================================
    // SET DEFAULT
    // ============================================================

    @Override
    @Transactional
    public AddressResponse setDefault(Long addressId, Long userId) {
        Address address = addressRepository.findByIdAndUser_Id(addressId, userId)
            .orElseThrow(() -> BusinessException.notFound(ErrorCode.ADDRESS_NOT_FOUND));

        // Early return nếu đã là default — tránh query + save thừa
        if (address.isDefault()) {
            return AddressResponse.fromEntity(address);
        }

        unsetAllDefaultForUser(userId);
        address.setDefault(true);

        return AddressResponse.fromEntity(addressRepository.save(address));
    }

    // ============================================================
    // Private helpers
    // ============================================================

    /**
     * Unset tất cả địa chỉ đang là default của user.
     * Bulk save để giảm số lần query.
     */
    private void unsetAllDefaultForUser(Long userId) {
        List<Address> defaults = addressRepository.findByUser_IdAndIsDefaultTrue(userId);
        if (defaults.isEmpty()) return;
        defaults.forEach(a -> a.setDefault(false));
        addressRepository.saveAll(defaults);
    }
}
