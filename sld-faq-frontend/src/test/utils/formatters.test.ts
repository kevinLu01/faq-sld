import { describe, it, expect } from 'vitest'
import { formatFileSize, confidenceToStars, truncate } from '@/utils/formatters'

// --------------------------------------------------------------------------
// formatFileSize
// --------------------------------------------------------------------------

describe('formatFileSize', () => {
  it('returns bytes label for values below 1024', () => {
    expect(formatFileSize(0)).toBe('0 B')
    expect(formatFileSize(1)).toBe('1 B')
    expect(formatFileSize(1023)).toBe('1023 B')
  })

  it('returns KB label for values between 1024 and 1 MB', () => {
    expect(formatFileSize(1024)).toBe('1.0 KB')
    expect(formatFileSize(1536)).toBe('1.5 KB')
    expect(formatFileSize(1024 * 1024 - 1)).toBe('1024.0 KB')
  })

  it('returns MB label for values at or above 1 MB', () => {
    expect(formatFileSize(1024 * 1024)).toBe('1.0 MB')
    expect(formatFileSize(1024 * 1024 * 2.5)).toBe('2.5 MB')
    expect(formatFileSize(1024 * 1024 * 100)).toBe('100.0 MB')
  })
})

// --------------------------------------------------------------------------
// confidenceToStars
// --------------------------------------------------------------------------

describe('confidenceToStars', () => {
  it('maps 0 to minimum 1 star', () => {
    expect(confidenceToStars(0)).toBe(1)
  })

  it('maps 1 to maximum 5 stars', () => {
    expect(confidenceToStars(1)).toBe(5)
  })

  it('maps mid-range values correctly', () => {
    // 0.5 * 5 = 2.5 → rounds to 3
    expect(confidenceToStars(0.5)).toBe(3)
    // 0.2 * 5 = 1.0 → rounds to 1
    expect(confidenceToStars(0.2)).toBe(1)
    // 0.8 * 5 = 4.0 → rounds to 4
    expect(confidenceToStars(0.8)).toBe(4)
  })

  it('clamps values outside [0, 1]', () => {
    expect(confidenceToStars(-0.5)).toBe(1)
    expect(confidenceToStars(1.5)).toBe(5)
  })
})

// --------------------------------------------------------------------------
// truncate
// --------------------------------------------------------------------------

describe('truncate', () => {
  it('returns text unchanged when shorter than maxLength', () => {
    expect(truncate('hello', 10)).toBe('hello')
  })

  it('returns text unchanged when equal to maxLength', () => {
    expect(truncate('hello', 5)).toBe('hello')
  })

  it('truncates text and appends ellipsis when longer than maxLength', () => {
    expect(truncate('hello world', 5)).toBe('hello...')
  })

  it('returns empty string for empty input', () => {
    expect(truncate('', 10)).toBe('')
  })

  it('handles maxLength of 0', () => {
    expect(truncate('any text', 0)).toBe('...')
  })

  it('handles multi-byte friendly slicing', () => {
    const text = 'ABCDEFGHIJ'
    expect(truncate(text, 3)).toBe('ABC...')
  })
})
