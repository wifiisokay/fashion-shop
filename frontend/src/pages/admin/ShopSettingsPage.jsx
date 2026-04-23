import { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { QUERY_KEYS } from '../../constants/queryKeys';
import { shopConfigApi } from '../../api/shopConfigApi';
import { ghnApi } from '../../api/ghnApi';
import Button from '../../components/ui/Button';
import Spinner from '../../components/ui/Spinner';
import { Warehouse, MapPin, Save, CheckCircle2, AlertTriangle } from 'lucide-react';

const ShopSettingsPage = () => {
  const queryClient = useQueryClient();

  // Fetch current config
  const { data: config, isLoading } = useQuery({
    queryKey: QUERY_KEYS.shopConfig(),
    queryFn: async () => {
      const res = await shopConfigApi.getConfig();
      return res.data.data;
    },
  });

  // Form state
  const [selectedProvinceId, setSelectedProvinceId] = useState(null);
  const [selectedDistrictId, setSelectedDistrictId] = useState(null);
  const [selectedWardCode, setSelectedWardCode] = useState('');
  const [street, setStreet] = useState('');
  const [provinceName, setProvinceName] = useState('');
  const [districtName, setDistrictName] = useState('');
  const [wardName, setWardName] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  // GHN master-data — shared API via React Query
  const { data: provinces = [], isLoading: loadingProvinces } = useQuery({
    queryKey: QUERY_KEYS.ghnProvinces(),
    queryFn: async () => {
      const res = await ghnApi.getProvinces();
      return res.data.data || [];
    },
    staleTime: 30 * 60 * 1000,
  });

  const { data: districts = [], isLoading: loadingDistricts } = useQuery({
    queryKey: QUERY_KEYS.ghnDistricts(selectedProvinceId),
    queryFn: async () => {
      const res = await ghnApi.getDistricts(selectedProvinceId);
      return res.data.data || [];
    },
    enabled: !!selectedProvinceId,
    staleTime: 30 * 60 * 1000,
  });

  const { data: wards = [], isLoading: loadingWards } = useQuery({
    queryKey: QUERY_KEYS.ghnWards(selectedDistrictId),
    queryFn: async () => {
      const res = await ghnApi.getWards(selectedDistrictId);
      return res.data.data || [];
    },
    enabled: !!selectedDistrictId && !!selectedProvinceId,
    staleTime: 30 * 60 * 1000,
  });

  // Populate form when config loads
  useEffect(() => {
    if (config) {
      setSelectedProvinceId(config.provinceId || null);
      setSelectedDistrictId(config.districtId);
      setSelectedWardCode(config.wardCode || '');
      setStreet(config.street || '');
      setProvinceName(config.provinceName || '');
      setDistrictName(config.districtName || '');
      setWardName(config.wardName || '');
    }
  }, [config]);

  // Save mutation
  const saveMutation = useMutation({
    mutationFn: (data) => shopConfigApi.updateConfig(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.shopConfig() });
      setSuccessMsg('Đã cập nhật địa chỉ kho hàng thành công!');
      setTimeout(() => setSuccessMsg(''), 4000);
    },
  });

  const handleProvinceChange = (e) => {
    const id = e.target.value ? Number(e.target.value) : null;
    const name = e.target.options[e.target.selectedIndex].text;
    setSelectedProvinceId(id);
    setProvinceName(id ? name : '');
    setSelectedDistrictId(null);
    setDistrictName('');
    setSelectedWardCode('');
    setWardName('');
  };

  const handleDistrictChange = (e) => {
    const id = e.target.value ? Number(e.target.value) : null;
    const name = e.target.options[e.target.selectedIndex].text;
    setSelectedDistrictId(id);
    setDistrictName(id ? name : '');
    setSelectedWardCode('');
    setWardName('');
  };

  const handleWardChange = (e) => {
    const code = e.target.value;
    const name = e.target.options[e.target.selectedIndex].text;
    setSelectedWardCode(code);
    setWardName(code ? name : '');
  };

  const handleSave = () => {
    if (!selectedDistrictId || !selectedWardCode) {
      alert('Vui lòng chọn đầy đủ Quận/Huyện và Phường/Xã.');
      return;
    }
    saveMutation.mutate({
      provinceId: selectedProvinceId,
      districtId: selectedDistrictId,
      wardCode: selectedWardCode,
      provinceName,
      districtName,
      wardName,
      street,
    });
  };

  if (isLoading) {
    return (
      <div className="flex justify-center py-20">
        <Spinner size="lg" />
      </div>
    );
  }

  return (
    <div className="max-w-3xl mx-auto space-y-8">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-3">
          <Warehouse className="w-7 h-7" />
          Cài đặt kho hàng
        </h1>
        <p className="text-gray-500 mt-1">
          Cấu hình địa chỉ kho để hệ thống tính phí vận chuyển GHN chính xác.
        </p>
      </div>

      {/* Current address display */}
      {config?.districtName && (
        <div className="bg-gray-50 rounded-xl border border-gray-200 p-5">
          <h3 className="text-sm font-medium text-gray-500 uppercase tracking-wider mb-2 flex items-center gap-2">
            <MapPin className="w-4 h-4" /> Địa chỉ kho hiện tại
          </h3>
          <p className="text-gray-900 font-medium">
            {[config.street, config.wardName, config.districtName, config.provinceName]
              .filter(Boolean)
              .join(', ')}
          </p>
          <p className="text-xs text-gray-400 mt-1">
            GHN District ID: {config.districtId} | Ward Code: {config.wardCode}
            {config.updatedAt && ` | Cập nhật: ${new Date(config.updatedAt).toLocaleString('vi-VN')}`}
          </p>
        </div>
      )}

      {/* Form */}
      <div className="bg-white rounded-xl border border-gray-200 p-6 space-y-5">
        <h2 className="text-lg font-semibold text-gray-900">Thay đổi địa chỉ kho</h2>

        <p className="text-sm text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-4 py-2.5 flex items-start gap-2">
          <AlertTriangle className="w-4 h-4 mt-0.5 shrink-0" />
          <span>
            Danh sách địa chỉ lấy từ <strong>GHN Master Data</strong> — cùng nguồn dữ liệu
            với form địa chỉ của khách hàng, đảm bảo mã tỉnh/quận/phường thống nhất khi tính phí ship.
          </span>
        </p>

        {/* Tỉnh/Thành */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Tỉnh/Thành phố
          </label>
          <select
            value={selectedProvinceId ?? ''}
            onChange={handleProvinceChange}
            disabled={loadingProvinces}
            className="w-full px-3 py-2.5 border border-gray-300 rounded-lg focus:ring-2 focus:ring-black focus:border-black disabled:bg-gray-100 transition-colors"
          >
            <option value="">
              {loadingProvinces ? 'Đang tải...' : '— Chọn Tỉnh/Thành —'}
            </option>
            {provinces.map((p) => (
              <option key={p.ProvinceID} value={p.ProvinceID}>
                {p.ProvinceName}
              </option>
            ))}
          </select>
        </div>

        {/* Quận/Huyện */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Quận/Huyện
          </label>
          <select
            value={selectedDistrictId ?? ''}
            onChange={handleDistrictChange}
            disabled={!selectedProvinceId || loadingDistricts}
            className="w-full px-3 py-2.5 border border-gray-300 rounded-lg focus:ring-2 focus:ring-black focus:border-black disabled:bg-gray-100 transition-colors"
          >
            <option value="">
              {loadingDistricts ? 'Đang tải...' : '— Chọn Quận/Huyện —'}
            </option>
            {districts.map((d) => (
              <option key={d.DistrictID} value={d.DistrictID}>
                {d.DistrictName}
              </option>
            ))}
          </select>
        </div>

        {/* Phường/Xã */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Phường/Xã
          </label>
          <select
            value={selectedWardCode}
            onChange={handleWardChange}
            disabled={!selectedDistrictId || loadingWards}
            className="w-full px-3 py-2.5 border border-gray-300 rounded-lg focus:ring-2 focus:ring-black focus:border-black disabled:bg-gray-100 transition-colors"
          >
            <option value="">
              {loadingWards ? 'Đang tải...' : '— Chọn Phường/Xã —'}
            </option>
            {wards.map((w) => (
              <option key={w.WardCode} value={w.WardCode}>
                {w.WardName}
              </option>
            ))}
          </select>
        </div>

        {/* Địa chỉ cụ thể */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Địa chỉ cụ thể (số nhà, tên đường)
          </label>
          <input
            type="text"
            value={street}
            onChange={(e) => setStreet(e.target.value)}
            placeholder="VD: 123 Nguyễn Huệ"
            className="w-full px-3 py-2.5 border border-gray-300 rounded-lg focus:ring-2 focus:ring-black focus:border-black transition-colors"
          />
        </div>

        {/* Success Message */}
        {successMsg && (
          <div className="flex items-center gap-2 text-green-700 bg-green-50 border border-green-200 rounded-lg px-4 py-2.5 text-sm font-medium">
            <CheckCircle2 className="w-4 h-4" />
            {successMsg}
          </div>
        )}

        {/* Save button */}
        <div className="flex justify-end pt-2">
          <Button
            onClick={handleSave}
            loading={saveMutation.isPending}
            disabled={!selectedDistrictId || !selectedWardCode}
            className="flex items-center gap-2"
          >
            <Save className="w-4 h-4" />
            Lưu cấu hình kho
          </Button>
        </div>
      </div>
    </div>
  );
};

export default ShopSettingsPage;
