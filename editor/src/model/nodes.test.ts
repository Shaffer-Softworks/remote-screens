import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { history } from './history'
import {
  COMPONENTS,
  MAX_DEPTH,
  clone,
  count,
  depth,
  dropSpot,
  duplicate,
  find,
  fromGrid,
  insert,
  move,
  moveTo,
  newComponent,
  newNode,
  newRoot,
  normalize,
  paddingOf,
  parentOf,
  pathTo,
  problems,
  remove,
  sibling,
  walk,
  wrap,
} from './nodes'
import type { Node, Page } from './types'

function tree(): { root: Node; row: Node; a: Node; b: Node; c: Node } {
  const root = newRoot()
  const row = newNode('row')
  const a = newNode('text')
  const b = newNode('icon')
  const c = newNode('button')
  insert(root, root.id, row)
  insert(root, row.id, a)
  insert(root, row.id, b)
  insert(root, root.id, c)
  return { root, row, a, b, c }
}

const ids = (n: Node) => [...walk(n)].map((x) => x.id)

describe('the component tree', () => {
  it('finds components, their parents and paths', () => {
    const { root, row, a, b, c } = tree()
    expect(count(root)).toBe(5)
    expect(depth(root)).toBe(3)
    expect(find(root, b.id)).toBe(b)
    expect(parentOf(root, b.id)).toEqual({ parent: row, index: 1 })
    expect(parentOf(root, root.id)).toBeUndefined()
    expect(pathTo(root, b.id)).toEqual([root.id, row.id, b.id])
    expect(sibling(root, a.id, 1)).toBe(b)
    expect(sibling(root, row.id, 1)).toBe(c)
    expect(sibling(root, a.id, -1)).toBeUndefined()
  })

  it('only puts components into containers', () => {
    const { root, a } = tree()
    expect(insert(root, a.id, newNode('text'))).toBe(false)
    expect(count(root)).toBe(5)
  })

  it('moves and reparents, but never into itself', () => {
    const { root, row, a, c } = tree()
    expect(move(root, c.id, row.id, 0)).toBe(true)
    expect(row.children.map((x) => x.id)[0]).toBe(c.id)
    expect(move(root, row.id, row.id, 0)).toBe(false)
    const inner = newNode('column')
    insert(root, row.id, inner)
    expect(move(root, row.id, inner.id, 0)).toBe(false)
    expect(move(root, a.id, inner.id, 0)).toBe(true)
    expect(pathTo(root, a.id)).toEqual([root.id, row.id, inner.id, a.id])
  })

  it('turns a drop beside or into a component into a spot, and moves there', () => {
    const { root, row, a, b, c } = tree()
    expect(dropSpot(root, row.id, 'inside')).toEqual({ parentId: row.id, index: 2 })
    expect(dropSpot(root, a.id, 'inside')).toBeUndefined()
    expect(dropSpot(root, b.id, 'before')).toEqual({ parentId: row.id, index: 1 })
    expect(dropSpot(root, root.id, 'after')).toBeUndefined()
    // a dropped after b, in the same row: counted with a still in place.
    expect(moveTo(root, a.id, dropSpot(root, b.id, 'after')!)).toBe(true)
    expect(row.children.map((x) => x.id)).toEqual([b.id, a.id])
    expect(moveTo(root, c.id, dropSpot(root, b.id, 'before')!)).toBe(true)
    expect(row.children.map((x) => x.id)).toEqual([c.id, b.id, a.id])
  })

  it('removes anything but the root', () => {
    const { root, row } = tree()
    expect(remove(root, root.id)).toBeUndefined()
    expect(remove(root, row.id)).toBe(row)
    expect(count(root)).toBe(2)
  })

  it('duplicates with new ids, cards too', () => {
    const { root, row } = tree()
    const tileNode = newNode('tile')
    insert(root, row.id, tileNode)
    const copy = duplicate(root, row.id)!
    expect(parentOf(root, copy.id)?.index).toBe(1)
    const before = ids(row)
    const after = ids(copy)
    expect(after).toHaveLength(before.length)
    expect(after.some((id) => before.includes(id))).toBe(false)
    expect(copy.children[2].tile!.id).not.toBe(tileNode.tile!.id)
    expect(clone(row).id).not.toBe(row.id)
  })

  it('wraps a component in a new container in its place', () => {
    const { root, row, b } = tree()
    const box = wrap(root, b.id, 'box')!
    expect(row.children[1]).toBe(box)
    expect(box.children[0]).toBe(b)
  })

  it('keeps to the depth the add-on allows', () => {
    const root = newRoot()
    let parent = root
    for (let i = 1; i < MAX_DEPTH; i++) {
      const next = newNode('column')
      expect(insert(root, parent.id, next)).toBe(true)
      parent = next
    }
    expect(insert(root, parent.id, newNode('column'))).toBe(false)
    expect(depth(root)).toBe(MAX_DEPTH)
  })
})

describe('the palette', () => {
  it('offers every component type the add-on draws, and no other', () => {
    const kotlin = readFileSync(fileURLToPath(new URL('../../../shared/src/main/kotlin/tech/shaffersoftworks/remotescreens/shared/Layout.kt', import.meta.url)), 'utf8')
    const block = kotlin.slice(kotlin.indexOf('object NodeTypes'), kotlin.indexOf('val ALL'))
    const addOn = new Set([...block.matchAll(/"([a-z]+)"/g)].map((m) => m[1]))
    addOn.delete('tile')
    expect(new Set(COMPONENTS.map((c) => c.type))).toEqual(addOn)
  })

  it('takes each side its own padding, else the padding all round', () => {
    const n = newNode('box')
    n.style = { padding: 8, corner: 0, paddingTop: 0, paddingEnd: 20 }
    expect(paddingOf(n)).toEqual([8, 0, 20, 8])
  })

  it('makes a card as a padded, rounded column', () => {
    const card = newComponent('card')!
    expect(card.type).toBe('column')
    expect(card.style).toMatchObject({ padding: 16, corner: 16, background: 'card' })
    expect(card.props.label).toBe('Card')
    expect(problems(card)).toBeUndefined()
    expect(newComponent('marquee')).toBeUndefined()
  })

  it('makes scrolling containers, a ticker, and a flow that takes children', () => {
    const col = newComponent('scrollcolumn')!
    expect([col.type, col.props.scroll, col.size.height]).toEqual(['column', 'true', 'dp:240'])
    expect(newComponent('scrollrow')!.props.scroll).toBe('true')
    expect(newComponent('ticker')!.props.marquee).toBe('true')
    const root = newRoot()
    const a = newNode('badge')
    root.children.push(a)
    const flow = wrap(root, a.id, 'flow')!
    expect(flow.type).toBe('flow')
    expect(flow.children[0].id).toBe(a.id)
    expect(problems(root)).toBeUndefined()
  })
})

describe('typed trees', () => {
  it('accept the add-on fixture once defaults are filled in', () => {
    const layout = JSON.parse(
      readFileSync(fileURLToPath(new URL('../../../tests/fixtures/layouts/v2.json', import.meta.url)), 'utf8'),
    )
    expect(problems(normalize(layout.screens[0].pages[0].root))).toBeUndefined()
  })

  it('reject what the add-on would', () => {
    const ok = normalize({ id: 'a', type: 'column', children: [{ id: 'b', type: 'text' }] })
    expect(problems(ok)).toBeUndefined()
    expect(problems(normalize({ id: 'a', type: 'marquee' }))).toMatch(/Unknown/)
    expect(problems(normalize({ id: 'a', type: 'text', children: [{ id: 'b', type: 'text' }] }))).toMatch(/holds no/)
    expect(problems(normalize({ id: 'a', type: 'column', children: [{ id: 'a', type: 'text' }] }))).toMatch(/twice/)
    expect(problems(normalize({ id: 'a', type: 'text', size: { width: 'huge' } }))).toMatch(/sizes/)
    expect(problems(normalize({ id: 'a', type: 'tile' }))).toMatch(/card/)
  })
})

describe('a grid page as a free-form one', () => {
  it('keeps every card, in rows weighted like the grid', () => {
    const tile = (id: string, col: number, row: number, w = 1, h = 1) => ({
      id, col, row, w, h, kind: 'clock', tap: { type: 'default' as const }, hold: { type: 'card' as const }, options: {},
    })
    const page: Page = { id: 'p', title: 'P', tiles: [tile('t1', 0, 0, 2), tile('t2', 2, 1), tile('t3', 0, 1)] }
    const root = fromGrid(page, 3, 2)
    expect(problems(root)).toBeUndefined()
    const cards = [...walk(root)].filter((n) => n.type === 'tile').map((n) => n.tile!.id)
    expect(cards.sort()).toEqual(['t1', 't2', 't3'])
    expect(root.children).toHaveLength(2)
    expect(root.children[0].children.map((n) => n.size.width)).toEqual(['weight:2', 'weight:1'])
    expect(root.children[1].children.map((n) => n.type)).toEqual(['tile', 'spacer', 'tile'])
  })
})

describe('history', () => {
  it('undoes and redoes, and a new edit drops what was undone', () => {
    const h = history<{ v: number }>()
    h.reset({ v: 0 })
    h.record({ v: 1 })
    h.record({ v: 1 })
    h.record({ v: 2 })
    expect(h.undo()).toEqual({ v: 1 })
    expect(h.undo()).toEqual({ v: 0 })
    expect(h.undo()).toBeUndefined()
    expect(h.redo()).toEqual({ v: 1 })
    h.record({ v: 5 })
    expect(h.canRedo()).toBe(false)
    expect(h.undo()).toEqual({ v: 1 })
  })
})
