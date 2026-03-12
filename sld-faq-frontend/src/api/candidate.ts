import request from './request'
import type { CandidateVO, PageResult } from '@/types'

export const candidateApi = {
  list: (params: { status?: string; fileId?: number; page: number; size: number }) =>
    request.get<PageResult<CandidateVO>, PageResult<CandidateVO>>('/faq-candidates', { params }),

  getById: (id: number) =>
    request.get<CandidateVO, CandidateVO>(`/faq-candidates/${id}`),

  approve: (id: number) =>
    request.post<{ faqId: number }, { faqId: number }>(`/faq-candidates/${id}/approve`),

  reject: (id: number, reason: string) =>
    request.post<void, void>(`/faq-candidates/${id}/reject`, { reason }),

  editApprove: (id: number, question: string, answer: string) =>
    request.post<{ faqId: number }, { faqId: number }>(`/faq-candidates/${id}/edit-approve`, {
      question,
      answer,
    }),

  merge: (id: number, targetFaqId: number) =>
    request.post<void, void>(`/faq-candidates/${id}/merge`, { targetFaqId }),
}
