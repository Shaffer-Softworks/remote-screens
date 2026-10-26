import { describe, expect, it } from 'vitest'
import { intercomStacks } from './intercomLayout'

describe('intercom page layout', () => {
  it('keeps the side column on a wide landscape page', () => {
    expect(intercomStacks(1280, 650)).toBe(false)
    expect(intercomStacks(1024, 450)).toBe(false)
  })

  it('stacks the keys on a phone and on a short landscape page', () => {
    expect(intercomStacks(360, 526)).toBe(true)
    expect(intercomStacks(800, 330)).toBe(true)
    expect(intercomStacks(480, 200)).toBe(true)
  })
})