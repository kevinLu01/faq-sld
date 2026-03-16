import request from './request'
import type { ProductCandidateVO, ProductVO, PageResult } from '@/types'

export const productCandidateApi = {
  list: (params: { status?: string; fileId?: number; page: number; size: number }) =>
    request.get<PageResult<ProductCandidateVO>, PageResult<ProductCandidateVO>>(
      '/product-candidates',
      { params },
    ),

  getById: (id: number) =>
    request.get<ProductCandidateVO, ProductCandidateVO>(`/product-candidates/${id}`),

  approve: (id: number) =>
    request.post<{ productId: number }, { productId: number }>(
      `/product-candidates/${id}/approve`,
    ),

  reject: (id: number, reason: string) =>
    request.post<void, void>(`/product-candidates/${id}/reject`, { reason }),
}

export const productApi = {
  list: (params: { keyword?: string; categoryId?: number; page: number; size: number }) =>
    request.get<PageResult<ProductVO>, PageResult<ProductVO>>('/products', { params }),

  getById: (id: number) => request.get<ProductVO, ProductVO>(`/products/${id}`),
}
