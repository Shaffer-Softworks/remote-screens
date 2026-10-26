import { describe, expect, it } from 'vitest'
import { intercomClientId, intercomSignalingUrl } from './intercomCall'

describe('intercom signaling url', () => {
  it('keeps the ingress token when the page has no trailing slash', () => {
    expect(
      intercomSignalingUrl('http://10.11.0.17:8123/api/hassio_ingress/TOKEN'),
    ).toBe('ws://10.11.0.17:8123/api/hassio_ingress/TOKEN/webrtc')
  })

  it('appends webrtc under a trailing slash and uses wss on https', () => {
    expect(
      intercomSignalingUrl('https://ha.example/api/hassio_ingress/TOKEN/?edit=1#page'),
    ).toBe('wss://ha.example/api/hassio_ingress/TOKEN/webrtc')
  })

  it('uses the editor root when the page is already the directory', () => {
    expect(intercomSignalingUrl('http://127.0.0.1:8099/')).toBe('ws://127.0.0.1:8099/webrtc')
  })
})

describe('intercom client id', () => {
  it('does not need crypto.randomUUID', () => {
    const clientId = intercomClientId('ha')
    expect(clientId).toMatch(/^ha-[0-9a-f]{32}$/)
    expect(clientId).not.toBe(intercomClientId('ha'))
  })
})
