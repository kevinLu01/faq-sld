export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface PageResult<T> {
  total: number
  items: T[]
}

export interface UserInfo {
  id: number
  name: string
  avatar?: string
  mobile?: string
  roles: string[]
  department?: string
}

export interface FileVO {
  id: number
  originalName: string
  fileType: string
  fileSize: number
  parseStatus: 'PENDING' | 'PARSING' | 'SUCCESS' | 'FAILED' | 'SCAN_PDF'
  chunkCount: number
  createdAt: string
}

export interface TaskStatus {
  id: number
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED'
  progress: number
  errorMsg?: string
}

export interface CandidateVO {
  id: number
  question: string
  answer: string
  category?: string
  keywords?: string
  sourceSummary?: string
  confidence?: number
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'MERGED'
  rejectReason?: string
  sourceChunk?: string
  fileName?: string
  createdAt: string
}

export interface FaqVO {
  id: number
  question: string
  answer: string
  categoryName?: string
  keywords?: string
  status: number
  viewCount: number
  publishedAt?: string
  sourceRefs?: SourceRef[]
}

export interface SourceRef {
  fileName: string
  chunkContent: string
}

export interface ProductCandidateVO {
  id: number
  fileId: number
  fileName?: string
  name: string
  model: string
  brand: string
  specs?: string        // JSON 字符串
  compatModels?: string // 逗号分隔
  category?: string
  sourceSummary?: string
  confidence?: number
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  rejectReason?: string
  createdAt: string
}

export interface ProductVO {
  id: number
  name: string
  model: string
  brand: string
  categoryId?: number
  categoryName?: string
  specs?: string
  compatModels?: string
  description?: string
  status: number
  publishedAt?: string
  createdAt: string
}

export interface LoginResponse {
  token: string
  user: UserInfo
}
