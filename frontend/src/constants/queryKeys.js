export const QUERY_KEYS = {
  // === PUBLIC ===
  products:          (filters = {}) => ['products', filters],
  product:           (id)           => ['products', String(id)],
  outfitSuggestions: (productId)    => ['outfitSuggestions', String(productId)],
  categories:        ()             => ['categories'],

  // === CUSTOMER ===
  cart:              ()             => ['cart'],
  myOrders:          (params = {})  => ['myOrders', params],
  myOrder:           (id)           => ['myOrders', id],
  chatMessages:      (sessionId)    => ['chatMessages', sessionId],

  // === GHN ===
  ghnProvinces:      ()             => ['ghn', 'provinces'],
  ghnDistricts:      (provinceId)   => ['ghn', 'districts', provinceId],
  ghnWards:          (districtId)   => ['ghn', 'wards', districtId],
  shippingFee:       (params)       => ['shippingFee', params],

  // === STAFF ===
  staffOrders:       (params = {})  => ['staff', 'orders', params],
  staffOrder:        (id)           => ['staff', 'orders', id],
  returns:           (params = {})  => ['staff', 'returns', params],
  return:            (id)           => ['staff', 'returns', id],

  // === ADMIN ===
  stats:             (period)       => ['admin', 'stats', period],
  adminProducts:     (params = {})  => ['admin', 'products', params],
  adminProduct:      (id)           => ['admin', 'products', String(id)],
  users:             (params = {})  => ['admin', 'users', params],
};
