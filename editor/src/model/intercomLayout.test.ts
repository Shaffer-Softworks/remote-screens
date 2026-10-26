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

  it('stacks the BLU tablet in both orientations and the keypad', () => {
    expect(intercomStacks(853, 383)).toBe(true)
    expect(intercomStacks(533, 739)).toBe(true)
    expect(intercomStacks(500, 775)).toBe(true)
  })
})