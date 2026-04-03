import axiosInstance from './axiosInstance';

export const returnApi = {
  getAll:  (params)    => axiosInstance.get('/api/staff/returns', { params }),
  getById: (id)        => axiosInstance.get(`/api/staff/returns/${id}`),
  process: (id, data)  => axiosInstance.patch(`/api/staff/returns/${id}`, data),
};
