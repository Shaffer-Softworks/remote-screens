import { afterEach, describe, expect, it, vi } from 'vitest'
import { IntercomBrowser, intercomClientId, intercomSignalingUrl } from './intercomCall'

// Exercise media setup without a browser or a signaling server.
afterEach(() => vi.unstubAllGlobals())

describe('intercom browser audio', () => {
  function setup(secure = true) {
    const track = { enabled: true, stop: vi.fn() }
    const stream = { getTracks: () => [track], getAudioTracks: () => [track] }
    const audio = { autoplay: false, srcObject: null, play: vi.fn().mockResolvedValue(undefined), pause: vi.fn(), remove: vi.fn() }
    const peer = { addTrack: vi.fn(), close: vi.fn(), ontrack: null as null | ((event: { track: unknown }) => void) }
    const remoteStream = { addTrack: vi.fn() }
    vi.stubGlobal('window', { isSecureContext: secure, clearTimeout: vi.fn() })
    vi.stubGlobal('navigator', { mediaDevices: { getUserMedia: vi.fn().mockResolvedValue(stream) } })
    vi.stubGlobal('document', { createElement: () => audio, body: { appendChild: vi.fn() } })
    vi.stubGlobal('RTCPeerConnection', function () { return peer })
    vi.stubGlobal('MediaStream', function () { return remoteStream })
    vi.stubGlobal('WebSocket', { OPEN: 1 })
    const onChange = vi.fn()
    const browser = new IntercomBrowser('web', 'Browser', onChange)
    // Media setup is private; keep the test focused on the actual track and playback wiring.
    const media = browser as unknown as { openPeer(): Promise<void> }
    return { browser, media, audio, peer, remoteStream, track, onChange }
  }

  it('plays received tracks, offers an autoplay retry, and releases media on stop', async () => {
    const { browser, media, audio, peer, remoteStream, track, onChange } = setup()
    await media.openPeer()
    expect(peer.addTrack).toHaveBeenCalled()
    audio.play.mockRejectedValueOnce(new Error('Autoplay blocked'))
    peer.ontrack?.({ track: 'remote audio' })
    await vi.waitFor(() => expect(onChange).toHaveBeenLastCalledWith(expect.objectContaining({ playbackBlocked: true })))
    expect(remoteStream.addTrack).toHaveBeenCalledWith('remote audio')
    await browser.resumeAudio()
    expect(onChange).toHaveBeenLastCalledWith(expect.objectContaining({ playbackBlocked: false }))
    browser.stop()
    expect(track.stop).toHaveBeenCalled()
    expect(audio.pause).toHaveBeenCalled()
    expect(audio.srcObject).toBeNull()
    expect(audio.remove).toHaveBeenCalled()
  })

  it('rejects insecure microphone access instead of silently creating a one-way call', async () => {
    const { browser, media } = setup(false)
    await expect(media.openPeer()).rejects.toThrow('Microphone requires HTTPS')
    browser.stop()
  })

  it('reports microphone permission denial', async () => {
    const { browser, media } = setup()
    vi.mocked(navigator.mediaDevices.getUserMedia).mockRejectedValue(new DOMException('Denied', 'NotAllowedError'))
    await expect(media.openPeer()).rejects.toThrow('Allow microphone access')
    browser.stop()
  })
})

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
