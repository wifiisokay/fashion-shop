package com.fashionshop.backend.module.user;

import com.fashionshop.backend.common.enums.Role;
import com.fashionshop.backend.common.enums.UserStatus;
import com.fashionshop.backend.domain.Address;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.domain.repository.AddressRepository;
import com.fashionshop.backend.domain.repository.UserRepository;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import com.fashionshop.backend.module.user.dto.request.AddressRequest;
import com.fashionshop.backend.module.user.dto.response.AddressResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddressServiceImplTest {

    @Mock private AddressRepository addressRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private AddressServiceImpl addressService;

    private User mockUser;
    private Address addr1; // isDefault=true
    private Address addr2; // isDefault=false
    private Address addr3; // isDefault=false

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
            .id(1L).email("test@test.com")
            .role(Role.CUSTOMER).status(UserStatus.ACTIVE)
            .build();

        addr1 = buildAddress(1L, true,  LocalDateTime.now().minusDays(2));
        addr2 = buildAddress(2L, false, LocalDateTime.now().minusDays(1));
        addr3 = buildAddress(3L, false, LocalDateTime.now());
    }

    // ================================================================
    // getAddresses
    // ================================================================

    @Test
    void getAddresses_returnsListOrderedByDefaultFirst() {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(addressRepository.findByUser_IdOrderByIsDefaultDescCreatedAtDesc(1L))
            .thenReturn(List.of(addr1, addr2, addr3));

        List<AddressResponse> result = addressService.getAddresses(1L);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).isDefault()).isTrue();
    }

    @Test
    void getAddresses_returnsEmptyList_whenNoAddresses() {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(addressRepository.findByUser_IdOrderByIsDefaultDescCreatedAtDesc(1L))
            .thenReturn(Collections.emptyList());

        List<AddressResponse> result = addressService.getAddresses(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void getAddresses_throwsUserNotFound_whenUserNotExist() {
        when(userRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> addressService.getAddresses(99L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    // ================================================================
    // getAddressById
    // ================================================================

    @Test
    void getAddressById_returnsAddress_whenOwned() {
        when(addressRepository.findByIdAndUser_Id(1L, 1L)).thenReturn(Optional.of(addr1));

        AddressResponse result = addressService.getAddressById(1L, 1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.isDefault()).isTrue();
    }

    @Test
    void getAddressById_throwsNotFound_whenNotOwned() {
        when(addressRepository.findByIdAndUser_Id(1L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.getAddressById(1L, 2L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ADDRESS_NOT_FOUND);
    }

    @Test
    void getAddressById_throwsNotFound_whenAddressNotExist() {
        when(addressRepository.findByIdAndUser_Id(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.getAddressById(99L, 1L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ADDRESS_NOT_FOUND);
    }

    // ================================================================
    // createAddress
    // ================================================================

    @Test
    void createAddress_setsDefaultTrue_whenFirstAddress() {
        AddressRequest request = buildRequest(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(addressRepository.existsByUser_Id(1L)).thenReturn(false);
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        addressService.createAddress(1L, request);

        ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
        verify(addressRepository).save(captor.capture());
        assertThat(captor.getValue().isDefault()).isTrue(); // auto set true vì địa chỉ đầu tiên
        verify(addressRepository, never()).findByUser_IdAndIsDefaultTrue(any());
    }

    @Test
    void createAddress_setsDefaultTrue_whenRequestedAndHasOthers() {
        AddressRequest request = buildRequest(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(addressRepository.existsByUser_Id(1L)).thenReturn(true);
        when(addressRepository.findByUser_IdAndIsDefaultTrue(1L)).thenReturn(List.of(addr1));
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        addressService.createAddress(1L, request);

        // addr1 phải bị unset
        assertThat(addr1.isDefault()).isFalse();
        verify(addressRepository).saveAll(List.of(addr1));

        ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
        verify(addressRepository).save(captor.capture());
        assertThat(captor.getValue().isDefault()).isTrue();
    }

    @Test
    void createAddress_doesNotChangeDefault_whenIsDefaultFalse() {
        AddressRequest request = buildRequest(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(addressRepository.existsByUser_Id(1L)).thenReturn(true);
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        addressService.createAddress(1L, request);

        verify(addressRepository, never()).findByUser_IdAndIsDefaultTrue(anyLong());
        ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
        verify(addressRepository).save(captor.capture());
        assertThat(captor.getValue().isDefault()).isFalse();
    }

    @Test
    void createAddress_throwsUserNotFound_whenUserNotExist() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.createAddress(99L, buildRequest(false)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    // ================================================================
    // updateAddress
    // ================================================================

    @Test
    void updateAddress_updatesFields_correctly() {
        AddressRequest request = buildRequest(true);
        request.setFullName("Tên Mới");
        request.setPhone("0987654321");
        when(addressRepository.findByIdAndUser_Id(1L, 1L)).thenReturn(Optional.of(addr1));
        when(addressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        addressService.updateAddress(1L, 1L, request);

        assertThat(addr1.getFullName()).isEqualTo("Tên Mới");
        assertThat(addr1.getPhone()).isEqualTo("0987654321");
    }

    @Test
    void updateAddress_unsetOthers_whenSettingDefault() {
        // addr2 chưa là default, request muốn set default
        AddressRequest request = buildRequest(true);
        when(addressRepository.findByIdAndUser_Id(2L, 1L)).thenReturn(Optional.of(addr2));
        when(addressRepository.findByUser_IdAndIsDefaultTrue(1L)).thenReturn(List.of(addr1));
        when(addressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        addressService.updateAddress(2L, 1L, request);

        assertThat(addr1.isDefault()).isFalse(); // addr1 bị unset
        assertThat(addr2.isDefault()).isTrue();  // addr2 trở thành default
        verify(addressRepository).saveAll(List.of(addr1));
    }

    @Test
    void updateAddress_keepsDefault_whenUnsetRequested() {
        // addr1 đang là default, request.isDefault=false → không được unset
        AddressRequest request = buildRequest(false);
        when(addressRepository.findByIdAndUser_Id(1L, 1L)).thenReturn(Optional.of(addr1));
        when(addressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        addressService.updateAddress(1L, 1L, request);

        assertThat(addr1.isDefault()).isTrue(); // vẫn giữ nguyên true
        verify(addressRepository, never()).findByUser_IdAndIsDefaultTrue(anyLong());
    }

    @Test
    void updateAddress_throwsNotFound_whenNotOwned() {
        when(addressRepository.findByIdAndUser_Id(1L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.updateAddress(1L, 2L, buildRequest(false)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ADDRESS_NOT_FOUND);
    }

    // ================================================================
    // deleteAddress
    // ================================================================

    @Test
    void deleteAddress_setsNewDefault_whenDeletingDefault() {
        // addr1 là default, còn 2 địa chỉ khác
        when(addressRepository.findByIdAndUser_Id(1L, 1L)).thenReturn(Optional.of(addr1));
        when(addressRepository.countByUser_Id(1L)).thenReturn(3L);
        // Trả danh sách không bao gồm addr1 ở vị trí đầu sau khi lọc
        when(addressRepository.findByUser_IdOrderByIsDefaultDescCreatedAtDesc(1L))
            .thenReturn(List.of(addr1, addr3, addr2)); // addr3 là mới nhất còn lại

        addressService.deleteAddress(1L, 1L);

        assertThat(addr3.isDefault()).isTrue(); // addr3 được chọn làm default mới
        verify(addressRepository).save(addr3);
        verify(addressRepository).delete(addr1);
    }

    @Test
    void deleteAddress_noNewDefault_whenDeletingLastAddress() {
        when(addressRepository.findByIdAndUser_Id(1L, 1L)).thenReturn(Optional.of(addr1));
        when(addressRepository.countByUser_Id(1L)).thenReturn(1L);

        addressService.deleteAddress(1L, 1L);

        verify(addressRepository, never()).findByUser_IdOrderByIsDefaultDescCreatedAtDesc(anyLong());
        verify(addressRepository).delete(addr1);
    }

    @Test
    void deleteAddress_noDefaultChange_whenDeletingNonDefault() {
        when(addressRepository.findByIdAndUser_Id(2L, 1L)).thenReturn(Optional.of(addr2));

        addressService.deleteAddress(2L, 1L);

        verify(addressRepository, never()).countByUser_Id(anyLong());
        verify(addressRepository).delete(addr2);
    }

    @Test
    void deleteAddress_throwsNotFound_whenNotOwned() {
        when(addressRepository.findByIdAndUser_Id(1L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.deleteAddress(1L, 2L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ADDRESS_NOT_FOUND);
    }

    // ================================================================
    // setDefault
    // ================================================================

    @Test
    void setDefault_setsCorrectly() {
        // addr2 chưa là default, cần set
        when(addressRepository.findByIdAndUser_Id(2L, 1L)).thenReturn(Optional.of(addr2));
        when(addressRepository.findByUser_IdAndIsDefaultTrue(1L)).thenReturn(List.of(addr1));
        when(addressRepository.save(addr2)).thenReturn(addr2);

        AddressResponse result = addressService.setDefault(2L, 1L);

        assertThat(addr1.isDefault()).isFalse(); // cũ bị unset
        assertThat(addr2.isDefault()).isTrue();  // mới thành default
        assertThat(result.getId()).isEqualTo(2L);
        verify(addressRepository).saveAll(List.of(addr1));
    }

    @Test
    void setDefault_noOp_whenAlreadyDefault() {
        // addr1 đã là default → early return
        when(addressRepository.findByIdAndUser_Id(1L, 1L)).thenReturn(Optional.of(addr1));

        AddressResponse result = addressService.setDefault(1L, 1L);

        assertThat(result.getId()).isEqualTo(1L);
        verify(addressRepository, never()).findByUser_IdAndIsDefaultTrue(anyLong());
        verify(addressRepository, never()).save(any());
    }

    @Test
    void setDefault_throwsNotFound_whenNotOwned() {
        when(addressRepository.findByIdAndUser_Id(1L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.setDefault(1L, 2L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ADDRESS_NOT_FOUND);
    }

    // ================================================================
    // Fixtures
    // ================================================================

    private Address buildAddress(Long id, boolean isDefault, LocalDateTime createdAt) {
        Address a = Address.builder()
            .id(id)
            .user(mockUser)
            .fullName("Người Dùng " + id)
            .phone("091234567" + id)
            .province("Hà Nội").provinceCode(1)
            .district("Ba Đình").districtCode(1)
            .ward("Phúc Xá").wardCode("00031")
            .street("123 Đường Test")
            .isDefault(isDefault)
            .build();
        // Set createdAt thông qua reflection để test sort
        try {
            var field = Address.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(a, createdAt);
        } catch (Exception ignored) { }
        return a;
    }

    private AddressRequest buildRequest(boolean isDefault) {
        AddressRequest r = new AddressRequest();
        r.setFullName("Nguyễn Văn A");
        r.setPhone("0912345678");
        r.setProvince("Hà Nội");
        r.setProvinceCode(1);
        r.setDistrict("Ba Đình");
        r.setDistrictCode(1);
        r.setWard("Phúc Xá");
        r.setWardCode("00031");
        r.setStreet("123 Đường ABC");
        // isDefault setter là setDefault() do Lombok stripping 'is' prefix
        // Dùng reflection để set field trực tiếp tránh nhầm lẫn
        try {
            var field = AddressRequest.class.getDeclaredField("isDefault");
            field.setAccessible(true);
            field.set(r, isDefault);
        } catch (Exception ignored) { }
        return r;
    }
}
