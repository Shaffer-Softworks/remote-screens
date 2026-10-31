export interface NotificationButton {
  title: string
  service?: string
  data?: Record<string, unknown>
  action?: string
}

export interface NotificationRequest {
  title: string
  message: string
  targets: string[]
  all: boolean
  presentation: 'banner' | 'alert'
  level: 'info' | 'warning' | 'danger'
  timeout: number
  wake: boolean
  icon?: string
  camera?: string
  buttons?: NotificationButton[]
  tag?: string
}

export interface NotificationResult {
  devices: { deviceId: string; name: string; status: 'sent' | 'offline' | 'unsupported' | 'failed' }[]
}

export function notificationError(request: NotificationRequest): string | undefined {
  if (!request.title.trim() || request.title.length > 100) return 'Enter a title of up to 100 characters.'
  if (request.message.length > 500) return 'Keep the message within 500 characters.'
  if (request.all === (request.targets.length > 0)) return 'Choose recipients or select all paired tablets.'
  if (!Number.isInteger(request.timeout) || request.timeout < (request.presentation === 'banner' ? 1 : 0) || request.timeout > 3600)
    return 'Choose a duration within 1–3600 seconds, or until acknowledged for alerts.'
  if (request.camera && (request.presentation !== 'alert' || !request.camera.startsWith('camera.')))
    return 'Choose a camera entity for the alert.'
  if ((request.buttons?.length ?? 0) > 2 || request.buttons?.some(button => !button.title.trim() || button.title.length > 60))
    return 'Provide up to two buttons with titles of up to 60 characters.'
  if (request.buttons?.some(button => button.service && !/^[a-z0-9_]+\.[a-z0-9_]+$/.test(button.service)))
    return 'Button services must use domain.service.'
}
