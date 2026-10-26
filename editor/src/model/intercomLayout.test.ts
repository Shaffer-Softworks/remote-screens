import { describe, expect, it } from 'vitest'
import { intercomStacks } from './intercomLayout'

describe('intercom page layout', () => {
  it('keeps the side column on a landscape page that can hold it', () => {
    expect(intercomStacks(1280, 650)).toBe(false)
    expect(intercomStacks(1024, 450)).toBe(false)
    expect(intercomStacks(853, 383)).toBe(false)
    expect(intercomStacks(800, 330)).toBe(false)
  })

  it('stacks the keys when the column does not fit', () => {
    expect(intercomStacks(360, 526)).toBe(true)
    expect(intercomStacks(480, 200)).toBe(true)
    expect(intercomStacks(900, 140)).toBe(true)
    expect(intercomStacks(533, 739)).toBe(true)
    expect(intercomStacks(500, 775)).toBe(true)
  })
})
