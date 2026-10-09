// Undo and redo for the screen being edited: snapshots of its JSON, taken when an edit settles.

export interface History<T> {
  /** Records [value] as the newest state, unless it is the same as the last. */
  record(value: T): void
  undo(): T | undefined
  redo(): T | undefined
  canUndo(): boolean
  canRedo(): boolean
  reset(value: T): void
}

export function history<T>(limit = 100): History<T> {
  let past: string[] = []
  let future: string[] = []
  let current: string | undefined

  return {
    record(value) {
      const s = JSON.stringify(value)
      if (s === current) return
      if (current !== undefined) past.push(current)
      if (past.length > limit) past = past.slice(past.length - limit)
      current = s
      future = []
    },
    undo() {
      const prev = past.pop()
      if (prev === undefined || current === undefined) return undefined
      future.push(current)
      current = prev
      return JSON.parse(prev) as T
    },
    redo() {
      const next = future.pop()
      if (next === undefined || current === undefined) return undefined
      past.push(current)
      current = next
      return JSON.parse(next) as T
    },
    canUndo: () => past.length > 0,
    canRedo: () => future.length > 0,
    reset(value) {
      past = []
      future = []
      current = JSON.stringify(value)
    },
  }
}
