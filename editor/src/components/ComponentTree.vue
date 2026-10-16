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
  const visit = (node: Node, level: number) => {
    out.push({ node, level })
    if (!collapsed.value.has(node.id)) for (const child of node.children) visit(child, level + 1)
  }
  if (ctx.root.value) visit(ctx.root.value, 0)
  return out
})

function label(node: Node): string {
  if (node.props.label) return node.props.label
  if (node.type === 'tile' && node.tile) {
    const entity = node.tile.entityId ? entityById.value.get(node.tile.entityId) : undefined
    return node.tile.name ?? entity?.name ?? node.tile.entityId ?? node.tile.kind ?? 'Tile'
  }
  if ((node.type === 'text' || node.type === 'button') && node.props.text)
    return `${componentOf(node.type)?.label}: ${node.props.text}`
  if (node.bind)
    return `${componentOf(node.type)?.label}: ${entityById.value.get(node.bind.entityId)?.name ?? node.bind.entityId}`
  return componentOf(node.type)?.label ?? node.type
}

function icon(node: Node): string {
  if (node.type === 'tile') return 'view-grid-outline'
  return componentOf(node.type)?.icon ?? 'shape-outline'
}

function toggle(id: string): void {
  const next = new Set(collapsed.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  collapsed.value = next
}

// Renaming sets `props.label`, which only the editor reads.
const renaming = ref<string | null>(null)
const draft = ref('')
async function rename(node: Node): Promise<void> {
  renaming.value = node.id
  draft.value = node.props.label ?? ''
  await nextTick()
  ;(document.querySelector('.tree input.rename') as HTMLInputElement | null)?.select()
}
function commit(node: Node): void {
  if (draft.value.trim()) node.props.label = draft.value.trim()
  else delete node.props.label
  renaming.value = null
}

function where(e: DragEvent, node: Node, isRoot: boolean): Where {
  if (isRoot) return 'inside'
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
  const fraction = (e.clientY - rect.top) / rect.height
  if (isContainer(node)) return fraction < 0.25 ? 'before' : fraction > 0.75 ? 'after' : 'inside'
  return fraction < 0.5 ? 'before' : 'after'
}

function over(e: DragEvent, node: Node, isRoot: boolean): void {
  if (!dragged.value) return
  const whereAt = where(e, node, isRoot)
  if (!ctx.canDrop(node.id, whereAt)) return
  e.preventDefault()
  ctx.hint.value = { targetId: node.id, where: whereAt }
}

function drop(node: Node): void {
  const dropHint = ctx.hint.value
  if (dropHint?.targetId === node.id) ctx.drop(node.id, dropHint.where)
}

function start(e: DragEvent, node: Node): void {
  e.dataTransfer?.setData('text/plain', node.id)
  setTimeout(() => {
    dragged.value = { type: 'node', id: node.id }
    ctx.select(node.id)
  })
}
</script>

<template>
  <div class="tree">
    <h3>Component tree</h3>
    <div class="rows" role="tree">
      <div
        v-for="row in rows"
        :key="row.node.id"
        class="row"
        role="treeitem"
        :aria-selected="ctx.selectedId.value === row.node.id"
        :class="{
          selected: ctx.selectedId.value === row.node.id,
          hidden: row.node.hidden,
          [`drop-${ctx.hint.value?.where}`]: ctx.hint.value?.targetId === row.node.id,
        }"
        :style="{ paddingLeft: `${4 + row.level * 14}px` }"
        :draggable="row.level > 0"
        @click="ctx.select(row.node.id)"
        @dblclick="rename(row.node)"
        @dragstart="start($event, row.node)"
        @dragend="dragged = null"
        @dragover="over($event, row.node, row.level === 0)"
        @drop.prevent="drop(row.node)"
      >
        <button
          v-if="row.node.children.length"
          class="twisty"
          :title="collapsed.has(row.node.id) ? 'Expand' : 'Collapse'"
          @click.stop="toggle(row.node.id)"
        >
          <MdiIcon
            :name="collapsed.has(row.node.id) ? 'chevron-right' : 'chevron-down'"
            :size="16"
          />
        </button>
        <span v-else class="twisty" />
        <MdiIcon :name="icon(row.node)" :size="16" />
        <input
          v-if="renaming === row.node.id"
          v-model="draft"
          class="rename grow"
          @keydown.enter="commit(row.node)"
          @keydown.esc="renaming = null"
          @blur="commit(row.node)"
          @click.stop
        />
        <span v-else class="grow name">{{ row.level === 0 ? 'Page' : label(row.node) }}</span>
        <button
          v-if="row.level > 0"
          class="eye"
          :title="row.node.hidden ? 'Show' : 'Hide'"
          @click.stop="row.node.hidden = row.node.hidden ? undefined : true"
        >
          <MdiIcon :name="row.node.hidden ? 'eye-off-outline' : 'eye-outline'" :size="15" />
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
  flex: 1;
  min-height: 0;
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
