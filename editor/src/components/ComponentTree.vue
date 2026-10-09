<script setup lang="ts">
// The page's components as a tree, as Android Studio's Component Tree: select, drag to reorder or move into another
// container, rename, hide and remove.
import { computed, inject, nextTick, ref } from 'vue'
import { DESIGN } from '../model/design'
import { dragged } from '../model/drag'
import { componentOf, isContainer, type Where } from '../model/nodes'
import type { Node } from '../model/types'
import { entityById } from '../store'
import MdiIcon from './MdiIcon.vue'

const ctx = inject(DESIGN)!
const collapsed = ref(new Set<string>())

interface Row {
  node: Node
  level: number
}

const rows = computed<Row[]>(() => {
  const out: Row[] = []
  const visit = (n: Node, level: number) => {
    out.push({ node: n, level })
    if (!collapsed.value.has(n.id)) for (const c of n.children) visit(c, level + 1)
  }
  if (ctx.root.value) visit(ctx.root.value, 0)
  return out
})

function label(n: Node): string {
  if (n.props.label) return n.props.label
  if (n.type === 'tile' && n.tile) {
    const e = n.tile.entityId ? entityById.value.get(n.tile.entityId) : undefined
    return n.tile.name ?? e?.name ?? n.tile.entityId ?? n.tile.kind ?? 'Tile'
  }
  if ((n.type === 'text' || n.type === 'button') && n.props.text) return `${componentOf(n.type)?.label}: ${n.props.text}`
  if (n.bind) return `${componentOf(n.type)?.label}: ${entityById.value.get(n.bind.entityId)?.name ?? n.bind.entityId}`
  return componentOf(n.type)?.label ?? n.type
}

function icon(n: Node): string {
  if (n.type === 'tile') return 'view-grid-outline'
  return componentOf(n.type)?.icon ?? 'shape-outline'
}

function toggle(id: string): void {
  const s = new Set(collapsed.value)
  if (s.has(id)) s.delete(id)
  else s.add(id)
  collapsed.value = s
}

// Renaming sets `props.label`, which only the editor reads.
const renaming = ref<string | null>(null)
const draft = ref('')
async function rename(n: Node): Promise<void> {
  renaming.value = n.id
  draft.value = n.props.label ?? ''
  await nextTick()
  ;(document.querySelector('.tree input.rename') as HTMLInputElement | null)?.select()
}
function commit(n: Node): void {
  if (draft.value.trim()) n.props.label = draft.value.trim()
  else delete n.props.label
  renaming.value = null
}

function where(e: DragEvent, n: Node, isRoot: boolean): Where {
  if (isRoot) return 'inside'
  const r = (e.currentTarget as HTMLElement).getBoundingClientRect()
  const f = (e.clientY - r.top) / r.height
  if (isContainer(n)) return f < 0.25 ? 'before' : f > 0.75 ? 'after' : 'inside'
  return f < 0.5 ? 'before' : 'after'
}

function over(e: DragEvent, n: Node, isRoot: boolean): void {
  if (!dragged.value) return
  const w = where(e, n, isRoot)
  if (!ctx.canDrop(n.id, w)) return
  e.preventDefault()
  ctx.hint.value = { targetId: n.id, where: w }
}

function drop(n: Node): void {
  const h = ctx.hint.value
  if (h?.targetId === n.id) ctx.drop(n.id, h.where)
}

function start(e: DragEvent, n: Node): void {
  e.dataTransfer?.setData('text/plain', n.id)
  setTimeout(() => {
    dragged.value = { type: 'node', id: n.id }
    ctx.select(n.id)
  })
}
</script>

<template>
  <div class="tree">
    <h3>Component tree</h3>
    <div class="rows" role="tree">
      <div
        v-for="r in rows"
        :key="r.node.id"
        class="row"
        role="treeitem"
        :aria-selected="ctx.selectedId.value === r.node.id"
        :class="{
          selected: ctx.selectedId.value === r.node.id,
          hidden: r.node.hidden,
          [`drop-${ctx.hint.value?.where}`]: ctx.hint.value?.targetId === r.node.id,
        }"
        :style="{ paddingLeft: `${4 + r.level * 14}px` }"
        :draggable="r.level > 0"
        @click="ctx.select(r.node.id)"
        @dblclick="rename(r.node)"
        @dragstart="start($event, r.node)"
        @dragend="dragged = null"
        @dragover="over($event, r.node, r.level === 0)"
        @drop.prevent="drop(r.node)"
      >
        <button
          v-if="r.node.children.length"
          class="twisty"
          :title="collapsed.has(r.node.id) ? 'Expand' : 'Collapse'"
          @click.stop="toggle(r.node.id)"
        >
          <MdiIcon :name="collapsed.has(r.node.id) ? 'chevron-right' : 'chevron-down'" :size="16" />
        </button>
        <span v-else class="twisty" />
        <MdiIcon :name="icon(r.node)" :size="16" />
        <input
          v-if="renaming === r.node.id"
          v-model="draft"
          class="rename grow"
          @keydown.enter="commit(r.node)"
          @keydown.esc="renaming = null"
          @blur="commit(r.node)"
          @click.stop
        />
        <span v-else class="grow name">{{ r.level === 0 ? 'Page' : label(r.node) }}</span>
        <button
          v-if="r.level > 0"
          class="eye"
          :title="r.node.hidden ? 'Show' : 'Hide'"
          @click.stop="r.node.hidden = r.node.hidden ? undefined : true"
        >
          <MdiIcon :name="r.node.hidden ? 'eye-off-outline' : 'eye-outline'" :size="15" />
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.tree {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 0;
}

.rows {
  display: flex;
  flex-direction: column;
  overflow: auto;
}

.row {
  position: relative;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 3px 4px;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  user-select: none;
}

.row:hover {
  background: var(--bg);
}

.row.selected {
  background: var(--accent);
  color: var(--accent-ink);
}

.row.hidden .name {
  opacity: 0.5;
}

.name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.twisty,
.eye {
  width: 18px;
  height: 18px;
  padding: 0;
  border: none;
  background: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  color: inherit;
}

.eye {
  opacity: 0;
}

.row:hover .eye,
.row.hidden .eye {
  opacity: 0.8;
}

.rename {
  padding: 1px 6px;
  font-size: 13px;
}

.drop-inside {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}

.drop-before::before,
.drop-after::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  height: 2px;
  background: var(--accent);
}

.drop-before::before {
  top: -1px;
}

.drop-after::after {
  bottom: -1px;
}
</style>
