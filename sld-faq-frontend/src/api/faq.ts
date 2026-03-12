import request from './request'
import type { FaqVO, PageResult } from '@/types'

export const faqApi = {
  list: (params: { keyword?: string; categoryId?: number; page: number; size: number }) =>
    request.get<PageResult<FaqVO>, PageResult<FaqVO>>('/faqs', { params }),

  getById: (id: number) =>
    request.get<FaqVO, FaqVO>(`/faqs/${id}`),
}
