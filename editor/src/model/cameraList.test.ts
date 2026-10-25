import { describe, expect, it } from 'vitest'
import { formatCameraList, parseCameraList } from './cameraList'

describe('camera list', () => {
  it('reads the option the server reads', () => {
    expect(parseCameraList('camera.front_door=Front Door; camera.garage ;')).toEqual([
      { entityId: 'camera.front_door', label: 'Front Door' },
      { entityId: 'camera.garage', label: '' },
    ])
    expect(parseCameraList(undefined)).toEqual([])
  })

  it('writes labels that cannot split the list', () => {
    expect(
      formatCameraList([
        { entityId: 'camera.back_yard', label: 'Back;yard=1' },
        { entityId: '', label: 'Unchosen' },
        { entityId: 'camera.garage', label: ' ' },
      ]),
    ).toBe('camera.back_yard=Back yard 1;camera.garage')
  })
})
