/** A zone of a full alarm tile: another alarm panel and the label on its chip (AlarmZones.kt). */
export interface AlarmZone {
  entityId: string
  label: string
}

/** The tile option that lists the zones as `entity=Label;entity=Label`. */
export const ALARM_ZONES_OPTION = 'zones'

export function parseAlarmZones(text: string | undefined): AlarmZone[] {
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

/** Labels lose `;` and `=`, which would split them. A zone without an entity is left out. */
export function formatAlarmZones(zones: AlarmZone[]): string {
  return zones
    .filter(zone => zone.entityId.length > 0)
    .map(zone => {
      const label = zone.label.replace(/[;=]/g, ' ').trim()
      return label ? `${zone.entityId}=${label}` : zone.entityId
    })
    .join(';')
}
