// What is being dragged. A dragover handler cannot read the drag's data, so it is kept here.

import { shallowRef } from 'vue'

export type Dragged =
  | { type: 'tile'; id: string; w: number; h: number; grabCol: number; grabRow: number }
  | { type: 'new'; entityId?: string; kind?: string; w: number; h: number }
  /** A component from the palette of a free-form page. */
  | { type: 'component'; component: string }
  /** A component already on a free-form page. */
  | { type: 'node'; id: string }

export const dragged = shallowRef<Dragged | null>(null)
