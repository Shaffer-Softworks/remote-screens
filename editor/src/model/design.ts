// The designer of a free-form page: which component is selected, where a drag would land, and the edits the
// palette, the tree, the canvas and the keyboard make. Provided by the screen editor, injected by its panes.

import { computed, ref, watch, type ComputedRef, type InjectionKey, type Ref } from 'vue'
import { dragged, type Dragged } from './drag'
import {
  dropSpot,
  duplicate,
  find,
  insert,
  isContainer,
  moveTo,
  newComponent,
  newNode,
  nodeForEntity,
  parentOf,
  remove,
  sibling,
  wrap,
  type Where,
} from './nodes'
import { newTile } from './tiles'
import type { Node, Page } from './types'

export interface DesignContext {
  root: ComputedRef<Node | undefined>
  selectedId: Ref<string | null>
  selected: ComputedRef<Node | undefined>
  hint: Ref<{ targetId: string; where: Where } | null>
  select(id: string | null): void
  /** Whether what is being dragged may land at [where] of [targetId]. */
  canDrop(targetId: string, where: Where): boolean
  drop(targetId: string, where: Where): void
  add(node: Node): void
  /** Adds the palette's component [id] after the selection. */
  addComponent(id: string): void
  addEntity(source: { entityId?: string; kind?: string }): void
  removeSelected(): void
  duplicateSelected(): void
  wrapSelected(type: 'column' | 'row' | 'box' | 'flow'): void
  /** Moves the selection to a sibling, its parent or its first child. */
  step(direction: 'prev' | 'next' | 'parent' | 'child'): void
  /** Moves the selected component one place earlier or later among its siblings. */
  shift(by: -1 | 1): void
}

export const DESIGN: InjectionKey<DesignContext> = Symbol('design')

export function useDesigner(page: ComputedRef<Page>, toast: (message: string) => void): DesignContext {
  const root = computed(() => page.value.root)
  const selectedId = ref<string | null>(null)
  const selected = computed(() => (root.value && selectedId.value ? find(root.value, selectedId.value) : undefined))
  const hint = ref<{ targetId: string; where: Where } | null>(null)
  watch(dragged, (d) => {
    if (!d) hint.value = null
  })

  function fresh(d: Dragged): Node | undefined {
    if (d.type === 'component') return newComponent(d.component)
    if (d.type === 'new') return d.entityId ? nodeForEntity(d.entityId) : newNode('tile', newTile({ kind: d.kind }, 0, 0))
    return undefined
  }

  function canDrop(targetId: string, where: Where): boolean {
    const r = root.value
    const d = dragged.value
    if (!r || !d || d.type === 'tile') return false
    const spot = dropSpot(r, targetId, where)
    if (!spot) return false
    if (d.type !== 'node') return true
    const moving = find(r, d.id)
    return !!moving && d.id !== targetId && !find(moving, spot.parentId)
  }

  function drop(targetId: string, where: Where): void {
    const r = root.value
    const d = dragged.value
    hint.value = null
    dragged.value = null
    if (!r || !d) return
    const spot = dropSpot(r, targetId, where)
    if (!spot) return
    if (d.type === 'node') {
      if (!moveTo(r, d.id, spot)) toast('It cannot go there')
      selectedId.value = d.id
      return
    }
    const n = fresh(d)
    if (!n) return
    if (!insert(r, spot.parentId, n, spot.index)) return toast('No room for it there')
    selectedId.value = n.id
  }

  function add(node: Node): void {
    const r = root.value
    if (!r) return
    const s = selected.value
    let ok: boolean
    if (s && isContainer(s)) ok = insert(r, s.id, node)
    else if (s && parentOf(r, s.id)) {
      const p = parentOf(r, s.id)!
      ok = insert(r, p.parent.id, node, p.index + 1)
    } else ok = insert(r, r.id, node)
    if (!ok) return toast('No room for it there')
    selectedId.value = node.id
  }

  return {
    root,
    selectedId,
    selected,
    hint,
    select: (id) => (selectedId.value = id),
    canDrop,
    drop,
    add,
    addComponent: (id) => {
      const n = newComponent(id)
      if (n) add(n)
    },
    addEntity: (source) => add(source.entityId ? nodeForEntity(source.entityId) : newNode('tile', newTile({ kind: source.kind }, 0, 0))),
    removeSelected() {
      const r = root.value
      const s = selectedId.value
      if (!r || !s) return
      if (s === r.id) return toast('The page itself cannot be removed')
      const p = parentOf(r, s)
      remove(r, s)
      selectedId.value = p ? (p.parent.children[p.index] ?? p.parent.children[p.index - 1] ?? p.parent).id : null
    },
    duplicateSelected() {
      const r = root.value
      const s = selectedId.value
      if (!r || !s) return
      const copy = duplicate(r, s)
      if (copy) selectedId.value = copy.id
      else toast(s === r.id ? 'The page itself cannot be copied' : 'No room for a copy')
    },
    wrapSelected(type) {
      const r = root.value
      const s = selectedId.value
      if (!r || !s) return
      const box = wrap(r, s, type)
      if (box) selectedId.value = box.id
      else toast('The page itself cannot be wrapped')
    },
    step(direction) {
      const r = root.value
      const s = selectedId.value
      if (!r) return
      if (!s) {
        selectedId.value = r.id
        return
      }
      const n = find(r, s)
      const next =
        direction === 'parent' ? parentOf(r, s)?.parent
        : direction === 'child' ? n?.children[0]
        : sibling(r, s, direction === 'prev' ? -1 : 1)
      if (next) selectedId.value = next.id
    },
    shift(by) {
      const r = root.value
      const s = selectedId.value
      if (!r || !s) return
      const p = parentOf(r, s)
      if (!p) return
      const j = p.index + by
      if (j < 0 || j >= p.parent.children.length) return
      const list = p.parent.children
      ;[list[p.index], list[j]] = [list[j], list[p.index]]
    },
  }
}
