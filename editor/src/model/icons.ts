// MDI paths, fetched from the add-on in batches and kept, so the editor ships without the icon set.

import { reactive } from 'vue'
import { api } from '../api'

const paths = reactive(new Map<string, string>())
const wanted = new Set<string>()
let timer: ReturnType<typeof setTimeout> | undefined

export function iconKey(name: string): string {
  return name.replace(/^mdi:/, '')
}

/** The path of [name], or undefined until it has arrived. */
export function iconPath(name: string): string | undefined {
  const key = iconKey(name)
  const path = paths.get(key)
  if (path === undefined && !wanted.has(key)) {
    wanted.add(key)
    timer ??= setTimeout(flush, 20)
  }
  return path
}

async function flush(): Promise<void> {
  timer = undefined
  const names = [...wanted].filter(name => !paths.has(name)).slice(0, 400)
  if (names.length === 0) return
  try {
    const got = await api.iconPaths(names)
    for (const [key, path] of Object.entries(got)) paths.set(key, path)
  } catch {
    for (const name of names) wanted.delete(name)
  }
}
