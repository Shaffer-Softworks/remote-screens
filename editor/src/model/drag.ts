// What is being dragged. A dragover handler cannot read the drag's data, so it is kept here.

import { shallowRef } from 'vue'

export type Dragged =
  | { type: 'tile'; id: string; w: number; h: number; grabCol: number; grabRow: number }
  | { type: 'new'; entityId?: string; kind?: string; w: number; h: number }

export const dragged = shallowRef<Dragged | null>(null)
