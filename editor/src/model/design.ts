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
import type { Node, Page, TileSource } from './types'

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
  addEntity(source: TileSource): void
  removeSelected(): void
  duplicateSelected(): void
  wrapSelected(type: 'column' | 'row' | 'box' | 'flow'): void
  /** Moves the selection to a sibling, its parent or its first child. */
  step(direction: 'prev' | 'next' | 'parent' | 'child'): void
  /** Moves the selected component one place earlier or later among its siblings. */
  shift(by: -1 | 1): void
}

export const DESIGN: InjectionKey<DesignContext> = Symbol('design')

export function useDesigner(
  page: ComputedRef<Page>,
  toast: (message: string) => void,
): DesignContext {
  const root = computed(() => page.value.root)
  const selectedId = ref<string | null>(null)
  const selected = computed(() =>
    root.value && selectedId.value ? find(root.value, selectedId.value) : undefined,
  )
  const hint = ref<{ targetId: string; where: Where } | null>(null)
  watch(dragged, drag => {
    if (!drag) hint.value = null
  })

  function fresh(drag: Dragged): Node | undefined {
    if (drag.type === 'component') return newComponent(drag.component)
    if (drag.type === 'new')
      return drag.entityId
        ? nodeForEntity(drag.entityId)
        : newNode('tile', newTile({ kind: drag.kind }, 0, 0))
    return undefined
  }

  function canDrop(targetId: string, where: Where): boolean {
    const rootNode = root.value
    const drag = dragged.value
    if (!rootNode || !drag || drag.type === 'tile') return false
    const spot = dropSpot(rootNode, targetId, where)
    if (!spot) return false
    if (drag.type !== 'node') return true
    const moving = find(rootNode, drag.id)
    return !!moving && drag.id !== targetId && !find(moving, spot.parentId)
  }

  function drop(targetId: string, where: Where): void {
    const rootNode = root.value
    const drag = dragged.value
    hint.value = null
    dragged.value = null
    if (!rootNode || !drag) return
    const spot = dropSpot(rootNode, targetId, where)
    if (!spot) return
    if (drag.type === 'node') {
      if (!moveTo(rootNode, drag.id, spot)) toast('It cannot go there')
      selectedId.value = drag.id
      return
    }
    const node = fresh(drag)
    if (!node) return
    if (!insert(rootNode, spot.parentId, node, spot.index)) return toast('No room for it there')
    selectedId.value = node.id
  }

  function add(node: Node): void {
    const rootNode = root.value
    if (!rootNode) return
    const selectedNode = selected.value
    let ok: boolean
    if (selectedNode && isContainer(selectedNode)) ok = insert(rootNode, selectedNode.id, node)
    else if (selectedNode && parentOf(rootNode, selectedNode.id)) {
      const parent = parentOf(rootNode, selectedNode.id)!
      ok = insert(rootNode, parent.parent.id, node, parent.index + 1)
    } else ok = insert(rootNode, rootNode.id, node)
    if (!ok) return toast('No room for it there')
    selectedId.value = node.id
  }

  return {
    root,
    selectedId,
    selected,
    hint,
    select: id => (selectedId.value = id),
    canDrop,
    drop,
    add,
    addComponent: id => {
      const node = newComponent(id)
      if (node) add(node)
    },
    addEntity: source =>
      add(
        source.entityId
          ? nodeForEntity(source.entityId)
          : newNode('tile', newTile({ kind: source.kind }, 0, 0)),
      ),
    removeSelected() {
      const rootNode = root.value
      const id = selectedId.value
      if (!rootNode || !id) return
      if (id === rootNode.id) return toast('The page itself cannot be removed')
      const parent = parentOf(rootNode, id)
      remove(rootNode, id)
      selectedId.value = parent
        ? (
            parent.parent.children[parent.index] ??
            parent.parent.children[parent.index - 1] ??
            parent.parent
          ).id
        : null
    },
    duplicateSelected() {
      const rootNode = root.value
      const id = selectedId.value
      if (!rootNode || !id) return
      const copy = duplicate(rootNode, id)
      if (copy) selectedId.value = copy.id
      else toast(id === rootNode.id ? 'The page itself cannot be copied' : 'No room for a copy')
    },
    wrapSelected(type) {
      const rootNode = root.value
      const id = selectedId.value
      if (!rootNode || !id) return
      const box = wrap(rootNode, id, type)
      if (box) selectedId.value = box.id
      else toast('The page itself cannot be wrapped')
    },
    step(direction) {
      const rootNode = root.value
      const id = selectedId.value
      if (!rootNode) return
      if (!id) {
        selectedId.value = rootNode.id
        return
      }
      const node = find(rootNode, id)
      const next =
        direction === 'parent'
          ? parentOf(rootNode, id)?.parent
          : direction === 'child'
            ? node?.children[0]
            : sibling(rootNode, id, direction === 'prev' ? -1 : 1)
      if (next) selectedId.value = next.id
    },
    shift(by) {
      const rootNode = root.value
      const id = selectedId.value
      if (!rootNode || !id) return
      const parent = parentOf(rootNode, id)
      if (!parent) return
      const nextIndex = parent.index + by
      if (nextIndex < 0 || nextIndex >= parent.parent.children.length) return
      const list = parent.parent.children
      ;[list[parent.index], list[nextIndex]] = [list[nextIndex], list[parent.index]]
    },
  }
}
