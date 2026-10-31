import { describe, expect, it } from 'vitest'
import { notificationError, type NotificationRequest } from './notifications'

const draft = (): NotificationRequest => ({
  title: 'Laundry', message: 'Finished', targets: ['tablet'], all: false,
  presentation: 'banner', level: 'info', timeout: 10, wake: false,
})

describe('notification validation', () => {
  it('requires explicit recipients or an explicit broadcast', () => {
    expect(notificationError(draft())).toBeUndefined()
    expect(notificationError({ ...draft(), targets: [] })).toBeDefined()
    expect(notificationError({ ...draft(), all: true })).toBeDefined()
    expect(notificationError({ ...draft(), all: true, targets: [] })).toBeUndefined()
  })
  it('restricts cameras and persistent duration to alerts', () => {
    expect(notificationError({ ...draft(), camera: 'camera.door' })).toBeDefined()
    expect(notificationError({ ...draft(), timeout: 0 })).toBeDefined()
    expect(notificationError({ ...draft(), presentation: 'alert', timeout: 0, camera: 'camera.door' })).toBeUndefined()
  })
  it('rejects invalid duration and action services', () => {
    expect(notificationError({ ...draft(), timeout: 1.5 })).toBeDefined()
    expect(notificationError({ ...draft(), timeout: 3601 })).toBeDefined()
    expect(notificationError({ ...draft(), buttons: [{ title: 'Open', service: 'lockXopen' }] })).toBeDefined()
    expect(notificationError({ ...draft(), buttons: [{ title: 'Open', service: 'lock.open' }] })).toBeUndefined()
  })
})
