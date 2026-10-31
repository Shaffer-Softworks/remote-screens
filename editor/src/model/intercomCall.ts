/** A browser station on the add-on's intercom. Audio goes directly to the tablet; this only exchanges signaling. */

export interface IntercomStationRow {
  clientId: string
  displayName: string
  inCall: boolean
}

export interface IntercomDeskState {
  status: string
  isMuted: boolean
  isBusy: boolean
  playbackBlocked: boolean
  stations: IntercomStationRow[]
}

type Frame = Record<string, unknown>

const HEARTBEAT_MS = 30_000
const CALL_TIMEOUT_MS = 30_000
const RECONNECT_MS = 2_000
const GATHER_MS = 1_500
const CLIENT_ID_BYTES = 16
const RANDOM_BYTE = 256

/**
 * The signaling socket beside this page.
 * Ingress has no trailing slash on some installs, and `new URL('webrtc', href)` would then
 * replace the ingress token. Append the path instead.
 */
export function intercomSignalingUrl(pageHref: string = window.location.href): string {
  const url = new URL(pageHref)
  const directory = url.pathname.endsWith('/') ? url.pathname : `${url.pathname}/`
  url.pathname = `${directory}webrtc`
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  url.search = ''
  url.hash = ''
  return url.toString()
}

/** An id for a page served over plain HTTP. `crypto.randomUUID` throws there and blanks the desk. */
export function intercomClientId(prefix: string): string {
  const bytes = new Uint8Array(CLIENT_ID_BYTES)
  if (typeof crypto !== 'undefined' && typeof crypto.getRandomValues === 'function') {
    crypto.getRandomValues(bytes)
  } else {
    for (let index = 0; index < bytes.length; index += 1) {
      bytes[index] = Math.floor(Math.random() * RANDOM_BYTE)
    }
  }
  const hex = [...bytes].map(byte => byte.toString(16).padStart(2, '0')).join('')
  return `${prefix}-${hex}`
}

export class IntercomBrowser {
  private socket: WebSocket | null = null
  private peer: RTCPeerConnection | null = null
  private localStream: MediaStream | null = null
  private remoteAudio: HTMLAudioElement | null = null
  private playbackBlocked = false
  private heartbeat: ReturnType<typeof setInterval> | null = null
  private callTimer: ReturnType<typeof setTimeout> | null = null
  private peerId: string | null = null
  private phase: 'offline' | 'idle' | 'outgoing' | 'incoming' | 'live' = 'offline'
  private pendingCandidates: RTCIceCandidateInit[] = []
  private remoteIsSet = false
  private canSendCandidates = false
  private isMuted = false
  private stations: IntercomStationRow[] = []
  private names = new Map<string, string>()
  private status = 'Connecting…'
  private stopped = false

  constructor(
    private readonly clientId: string,
    private readonly displayName: string,
    private readonly onChange: (state: IntercomDeskState) => void,
  ) {}

  start(url: string): void {
    this.stopped = false
    this.open(url)
  }

  stop(): void {
    this.stopped = true
    this.clearCall(false)
    this.socket?.close()
    this.socket = null
  }

  call(clientId: string): void {
    if (this.phase !== 'idle' || clientId === '') return
    void this.placeCall(clientId)
  }

  hangUp(): void {
    if (this.peerId === null) return
    this.clearCall(true, 'Call ended')
  }

  async resumeAudio(): Promise<void> {
    const audio = this.remoteAudio
    if (audio === null) return
    try {
      await audio.play()
      if (this.remoteAudio !== audio) return
      this.playbackBlocked = false
    } catch {
      if (this.remoteAudio !== audio) return
      this.playbackBlocked = true
    }
    this.publish()
  }

  toggleMute(): void {
    this.isMuted = !this.isMuted
    this.localStream?.getAudioTracks().forEach(track => {
      track.enabled = !this.isMuted
    })
    this.publish()
  }

  private open(url: string): void {
    let socket: WebSocket
    try {
      socket = new WebSocket(url)
    } catch {
      this.status = 'Could not open the intercom'
      this.publish()
      return
    }
    this.socket = socket
    socket.onopen = () => {
      this.send({ type: 'register', clientId: this.clientId, displayName: this.displayName })
      this.heartbeat = setInterval(() => this.send({ type: 'heartbeat' }), HEARTBEAT_MS)
    }
    socket.onmessage = event => {
      if (typeof event.data === 'string') void this.handle(JSON.parse(event.data) as Frame)
    }
    socket.onclose = () => {
      this.clearTimer(this.heartbeat)
      this.heartbeat = null
      if (!this.stopped) window.setTimeout(() => this.open(url), RECONNECT_MS)
    }
  }

  private async handle(frame: Frame): Promise<void> {
    const type = String(frame.type ?? '')
    if (type === 'registered') {
      this.phase = 'idle'
      this.status = 'Online'
      this.send({ type: 'getClients' })
      this.publish()
    } else if (type === 'ping') {
      this.send({ type: 'pong', ts: frame.ts ?? 0 })
    } else if (type === 'clientsList') {
      this.takeRoster(frame)
    } else if (type === 'offer') {
      await this.onOffer(String(frame.sdp ?? ''), String(frame.from ?? ''))
    } else if (type === 'answer' && this.phase === 'outgoing') {
      await this.peer?.setRemoteDescription({ type: 'answer', sdp: String(frame.sdp ?? '') })
      await this.flushCandidates()
    } else if (type === 'candidate') {
      this.rememberCandidate(frame)
    } else if (type === 'hangup') {
      this.clearCall(false, 'Call ended')
    } else if (type === 'unavailable' && frame.targetId === this.peerId) {
      const next = frame.reason === 'busy' ? `${this.nameOf(String(frame.targetId))} is busy` : 'No answer'
      this.clearCall(true, next)
    } else if (type === 'replaced' || (type === 'error' && frame.message === 'not_registered')) {
      this.socket?.close()
    }
  }

  private async placeCall(targetId: string): Promise<void> {
    this.peerId = targetId
    try {
      await this.openPeer()
      const offer = await this.peer?.createOffer()
      if (!offer) throw new Error('No offer')
      await this.peer?.setLocalDescription(offer)
      const sdp = await this.gatheredSdp()
      if (sdp === '') throw new Error('No offer')
      this.phase = 'outgoing'
      this.canSendCandidates = true
      this.send({ type: 'offer', to: targetId, sdp })
      this.status = `Calling ${this.nameOf(targetId)}`
      this.armTimeout()
      this.publish()
    } catch (error) {
      this.clearCall(true, error instanceof Error ? error.message : 'Call failed')
    }
  }

  private async onOffer(sdp: string, from: string): Promise<void> {
    if (this.phase === 'outgoing' && this.clientId < from) return
    if (this.phase !== 'idle' && this.phase !== 'outgoing') return
    this.peerId = from
    try {
      if (this.phase === 'outgoing') await this.peer?.setLocalDescription({ type: 'rollback' })
      else await this.openPeer()
      await this.peer?.setRemoteDescription({ type: 'offer', sdp })
      await this.flushCandidates()
      const answer = await this.peer?.createAnswer()
      if (!answer) throw new Error('No answer')
      await this.peer?.setLocalDescription(answer)
      const answerSdp = await this.gatheredSdp()
      if (answerSdp === '') throw new Error('No answer')
      this.phase = 'incoming'
      this.canSendCandidates = true
      this.send({ type: 'answer', to: from, sdp: answerSdp })
      this.status = `Incoming from ${this.nameOf(from)}`
      this.armTimeout()
      this.publish()
    } catch (error) {
      this.clearCall(true, error instanceof Error ? error.message : 'Call failed')
    }
  }

  private async openPeer(): Promise<void> {
    if (typeof RTCPeerConnection !== 'function') throw new Error('WebRTC is unavailable')
    const peer = new RTCPeerConnection({ iceServers: [] })
    this.peer = peer
    this.canSendCandidates = false
    const audio = document.createElement('audio')
    audio.autoplay = true
    document.body.appendChild(audio)
    this.remoteAudio = audio
    const remoteStream = new MediaStream()
    audio.srcObject = remoteStream
    peer.ontrack = event => {
      if (this.peer !== peer) return
      remoteStream.addTrack(event.track)
      void this.resumeAudio()
    }
    const stream = await this.captureMicrophone()
    if (this.peer !== peer) {
      stream.getTracks().forEach(track => track.stop())
      throw new Error('Call ended')
    }
    this.localStream = stream
    stream.getAudioTracks().forEach(track => {
      track.enabled = !this.isMuted
      peer.addTrack(track, stream)
    })
    peer.onicecandidate = event => {
      const candidate = event.candidate
      const target = this.peerId
      if (candidate === null || target === null || !this.canSendCandidates) return
      this.send({
        type: 'candidate',
        to: target,
        candidate: candidate.candidate,
        sdpMid: candidate.sdpMid,
        sdpMLineIndex: candidate.sdpMLineIndex,
      })
    }
    peer.oniceconnectionstatechange = () => {
      if (this.peer !== peer) return
      if (peer.iceConnectionState === 'connected' || peer.iceConnectionState === 'completed') {
        const target = this.peerId
        if (target === null) return
        this.phase = 'live'
        this.clearTimer(this.callTimer)
        this.status = `Connected to ${this.nameOf(target)}`
        this.publish()
      } else if (peer.iceConnectionState === 'failed') {
        this.clearCall(true, 'Call failed')
      }
    }
  }

  private takeRoster(frame: Frame): void {
    const clients = Array.isArray(frame.clients) ? frame.clients : []
    this.stations = clients.flatMap(client => {
      if (typeof client !== 'object' || client === null) return []
      const row = client as Frame
      const clientId = String(row.clientId ?? '')
      if (clientId === '' || clientId === this.clientId) return []
      const displayName = String(row.displayName ?? clientId)
      this.names.set(clientId, displayName)
      return [{ clientId, displayName, inCall: row.inCall === true }]
    })
    if (this.phase === 'idle' && (this.status === 'Connecting…' || this.status === 'Online')) {
      this.status = 'Online'
    }
    this.publish()
  }

  /** A candidate can arrive before the offer has been applied. Hold it until then. */
  private rememberCandidate(frame: Frame): void {
    const init: RTCIceCandidateInit = {
      candidate: String(frame.candidate ?? ''),
      sdpMid: frame.sdpMid == null ? null : String(frame.sdpMid),
      sdpMLineIndex: Number(frame.sdpMLineIndex ?? 0),
    }
    if (!this.remoteIsSet || this.peer === null) {
      this.pendingCandidates.push(init)
      return
    }
    void this.peer.addIceCandidate(init).catch(() => undefined)
  }

  private async flushCandidates(): Promise<void> {
    this.remoteIsSet = true
    const queued = this.pendingCandidates
    this.pendingCandidates = []
    for (const init of queued) {
      await this.peer?.addIceCandidate(init).catch(() => undefined)
    }
  }

  private async captureMicrophone(): Promise<MediaStream> {
    if (!window.isSecureContext) {
      throw new Error('Microphone requires HTTPS. Open Home Assistant over HTTPS (or localhost).')
    }
    const devices = navigator.mediaDevices
    if (typeof devices?.getUserMedia !== 'function') {
      throw new Error('Microphone access is unavailable in this browser.')
    }
    try {
      return await devices.getUserMedia({ audio: true, video: false })
    } catch (error) {
      const name = error instanceof Error ? error.name : ''
      if (name === 'NotAllowedError' || name === 'SecurityError') {
        throw new Error('Microphone blocked. Allow microphone access for this page and try again.')
      }
      if (name === 'NotFoundError') throw new Error('No microphone found. Connect a microphone and try again.')
      throw new Error('Could not open the microphone. Check that it is available and try again.')
    }
  }

  /** Host candidates are often still arriving when setLocalDescription returns. */
  private async gatheredSdp(): Promise<string> {
    const peer = this.peer
    if (peer === null) return ''
    if (peer.iceGatheringState !== 'complete') {
      await new Promise<void>(resolve => {
        const finish = () => {
          window.clearTimeout(timer)
          peer.removeEventListener('icegatheringstatechange', onChange)
          resolve()
        }
        const onChange = () => {
          if (peer.iceGatheringState === 'complete') finish()
        }
        const timer = window.setTimeout(finish, GATHER_MS)
        peer.addEventListener('icegatheringstatechange', onChange)
      })
    }
    return peer.localDescription?.sdp ?? ''
  }

  private clearCall(notifyPeer: boolean, nextStatus = 'Online'): void {
    const target = this.peerId
    const closing = this.peer
    this.clearTimer(this.callTimer)
    if (notifyPeer && target !== null) this.send({ type: 'hangup', to: target })
    this.peer = null
    this.localStream?.getTracks().forEach(track => track.stop())
    this.localStream = null
    if (this.remoteAudio !== null) {
      this.remoteAudio.pause()
      this.remoteAudio.srcObject = null
      this.remoteAudio.remove()
      this.remoteAudio = null
    }
    this.playbackBlocked = false
    this.pendingCandidates = []
    this.remoteIsSet = false
    this.canSendCandidates = false
    this.peerId = null
    this.phase = this.socket?.readyState === WebSocket.OPEN ? 'idle' : 'offline'
    this.status = nextStatus
    this.publish()
    if (closing) {
      closing.ontrack = null
      closing.oniceconnectionstatechange = null
      closing.close()
    }
  }

  private armTimeout(): void {
    this.clearTimer(this.callTimer)
    this.callTimer = setTimeout(() => this.clearCall(true, 'No answer'), CALL_TIMEOUT_MS)
  }

  private send(frame: Frame): void {
    if (this.socket?.readyState === WebSocket.OPEN) this.socket.send(JSON.stringify(frame))
  }

  private nameOf(clientId: string): string {
    return this.names.get(clientId) ?? clientId
  }

  private publish(): void {
    const isBusy = this.phase === 'outgoing' || this.phase === 'incoming' || this.phase === 'live'
    this.onChange({ status: this.status, isMuted: this.isMuted, isBusy, playbackBlocked: this.playbackBlocked, stations: this.stations })
  }

  private clearTimer(timer: ReturnType<typeof setInterval> | ReturnType<typeof setTimeout> | null): void {
    if (timer !== null) window.clearTimeout(timer)
  }
}
