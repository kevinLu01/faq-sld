import request from './request'
import type { FileVO, PageResult, TaskStatus } from '@/types'

export const fileApi = {
  upload: (formData: FormData) =>
    request.post<FileVO, FileVO>('/files/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }),

  list: (page: number, size: number) =>
    request.get<PageResult<FileVO>, PageResult<FileVO>>('/files', {
      params: { page, size },
    }),

  getById: (id: number) =>
    request.get<FileVO, FileVO>(`/files/${id}`),

  generateFaq: (id: number) =>
    request.post<{ taskId: number }, { taskId: number }>(`/files/${id}/generate-faq`),

  getTaskStatus: (taskId: number) =>
    request.get<TaskStatus, TaskStatus>(`/tasks/${taskId}/status`),
}
