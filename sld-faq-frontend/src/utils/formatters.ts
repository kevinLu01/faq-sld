// File size formatting
export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

// Confidence to star rating (0-1 → 1-5 stars)
export function confidenceToStars(confidence: number): number {
  return Math.max(1, Math.min(5, Math.round(confidence * 5)))
}

// Truncate text
export function truncate(text: string, maxLength: number): string {
  if (!text || text.length <= maxLength) return text
  return text.slice(0, maxLength) + '...'
}
