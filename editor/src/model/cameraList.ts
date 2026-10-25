/** A camera of a full cameras tile and the name on its row (CamerasList.kt). An empty label means the entity's name. */
export interface CameraEntry {
  entityId: string
  label: string
}

/** The tile option that lists the cameras as `camera.a=Label;camera.b`. */
export const CAMERAS_OPTION = 'cameras'

export function parseCameraList(text: string | undefined): CameraEntry[] {
  return (text ?? '')
    .split(';')
    .map(entry => entry.trim())
    .filter(entry => entry.length > 0)
    .map(entry => {
      const separator = entry.indexOf('=')
      return separator < 0
        ? { entityId: entry, label: '' }
        : { entityId: entry.slice(0, separator).trim(), label: entry.slice(separator + 1).trim() }
    })
}

/** Labels lose `;` and `=`, which would split them. A camera without an entity is left out. */
export function formatCameraList(cameras: CameraEntry[]): string {
  return cameras
    .filter(camera => camera.entityId.length > 0)
    .map(camera => {
      const label = camera.label.replace(/[;=]/g, ' ').trim()
      return label ? `${camera.entityId}=${label}` : camera.entityId
    })
    .join(';')
}
