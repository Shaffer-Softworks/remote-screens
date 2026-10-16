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

function tree(): { root: Node; row: Node; textNode: Node; iconNode: Node; buttonNode: Node } {
  const root = newRoot()
  const row = newNode('row')
  const textNode = newNode('text')
  const iconNode = newNode('icon')
  const buttonNode = newNode('button')
  insert(root, root.id, row)
  insert(root, row.id, textNode)
  insert(root, row.id, iconNode)
  insert(root, root.id, buttonNode)
  return { root, row, textNode, iconNode, buttonNode }
}

const ids = (node: Node) => [...walk(node)].map(item => item.id)

describe('the component tree', () => {
  it('finds components, their parents and paths', () => {
    const { root, row, textNode, iconNode, buttonNode } = tree()
    expect(count(root)).toBe(5)
    expect(depth(root)).toBe(3)
    expect(find(root, iconNode.id)).toBe(iconNode)
    expect(parentOf(root, iconNode.id)).toEqual({ parent: row, index: 1 })
    expect(parentOf(root, root.id)).toBeUndefined()
    expect(pathTo(root, iconNode.id)).toEqual([root.id, row.id, iconNode.id])
    expect(sibling(root, textNode.id, 1)).toBe(iconNode)
    expect(sibling(root, row.id, 1)).toBe(buttonNode)
    expect(sibling(root, textNode.id, -1)).toBeUndefined()
  })

  it('only puts components into containers', () => {
    const { root, textNode } = tree()
    expect(insert(root, textNode.id, newNode('text'))).toBe(false)
    expect(count(root)).toBe(5)
  })

  it('moves and reparents, but never into itself', () => {
    const { root, row, textNode, buttonNode } = tree()
    expect(move(root, buttonNode.id, row.id, 0)).toBe(true)
    expect(row.children.map(item => item.id)[0]).toBe(buttonNode.id)
    expect(move(root, row.id, row.id, 0)).toBe(false)
    const inner = newNode('column')
    insert(root, row.id, inner)
    expect(move(root, row.id, inner.id, 0)).toBe(false)
    expect(move(root, textNode.id, inner.id, 0)).toBe(true)
    expect(pathTo(root, textNode.id)).toEqual([root.id, row.id, inner.id, textNode.id])
  })

  it('turns a drop beside or into a component into a spot, and moves there', () => {
    const { root, row, textNode, iconNode, buttonNode } = tree()
    expect(dropSpot(root, row.id, 'inside')).toEqual({ parentId: row.id, index: 2 })
    expect(dropSpot(root, textNode.id, 'inside')).toBeUndefined()
    expect(dropSpot(root, iconNode.id, 'before')).toEqual({ parentId: row.id, index: 1 })
    expect(dropSpot(root, root.id, 'after')).toBeUndefined()
    // textNode dropped after iconNode, in the same row: counted with textNode still in place.
    expect(moveTo(root, textNode.id, dropSpot(root, iconNode.id, 'after')!)).toBe(true)
    expect(row.children.map(item => item.id)).toEqual([iconNode.id, textNode.id])
    expect(moveTo(root, buttonNode.id, dropSpot(root, iconNode.id, 'before')!)).toBe(true)
    expect(row.children.map(item => item.id)).toEqual([buttonNode.id, iconNode.id, textNode.id])
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
    expect(after.some(id => before.includes(id))).toBe(false)
    expect(copy.children[2].tile!.id).not.toBe(tileNode.tile!.id)
    expect(clone(row).id).not.toBe(row.id)
  })

  it('wraps a component in a new container in its place', () => {
    const { root, row, iconNode } = tree()
    const box = wrap(root, iconNode.id, 'box')!
    expect(row.children[1]).toBe(box)
    expect(box.children[0]).toBe(iconNode)
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
    const kotlin = readFileSync(
      fileURLToPath(
        new URL(
          '../../../shared/src/main/kotlin/tech/shaffersoftworks/remotescreens/shared/Layout.kt',
          import.meta.url,
        ),
      ),
      'utf8',
    )
    const block = kotlin.slice(kotlin.indexOf('object NodeTypes'), kotlin.indexOf('val ALL'))
    const addOn = new Set([...block.matchAll(/"([a-z]+)"/g)].map(match => match[1]))
    addOn.delete('tile')
    expect(new Set(COMPONENTS.map(spec => spec.type))).toEqual(addOn)
  })

  it('takes each side its own padding, else the padding all round', () => {
    const node = newNode('box')
    node.style = { padding: 8, corner: 0, paddingTop: 0, paddingEnd: 20 }
    expect(paddingOf(node)).toEqual([8, 0, 20, 8])
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
    const badge = newNode('badge')
    root.children.push(badge)
    const flow = wrap(root, badge.id, 'flow')!
    expect(flow.type).toBe('flow')
    expect(flow.children[0].id).toBe(badge.id)
    expect(problems(root)).toBeUndefined()
  })
})

describe('typed trees', () => {
  it('accept the add-on fixture once defaults are filled in', () => {
    const layout = JSON.parse(
      readFileSync(
        fileURLToPath(new URL('../../../tests/fixtures/layouts/v2.json', import.meta.url)),
        'utf8',
      ),
    )
    expect(problems(normalize(layout.screens[0].pages[0].root))).toBeUndefined()
  })

  it('reject what the add-on would', () => {
    const ok = normalize({ id: 'a', type: 'column', children: [{ id: 'b', type: 'text' }] })
    expect(problems(ok)).toBeUndefined()
    expect(problems(normalize({ id: 'a', type: 'marquee' }))).toMatch(/Unknown/)
    expect(
      problems(normalize({ id: 'a', type: 'text', children: [{ id: 'b', type: 'text' }] })),
    ).toMatch(/holds no/)
    expect(
      problems(normalize({ id: 'a', type: 'column', children: [{ id: 'a', type: 'text' }] })),
    ).toMatch(/twice/)
    expect(problems(normalize({ id: 'a', type: 'text', size: { width: 'huge' } }))).toMatch(/sizes/)
    expect(problems(normalize({ id: 'a', type: 'tile' }))).toMatch(/card/)
  })
})

describe('a grid page as a free-form one', () => {
  it('keeps every card, in rows weighted like the grid', () => {
    const tile = (id: string, col: number, row: number, w = 1, h = 1) => ({
      id,
      col,
      row,
      w,
      h,
      kind: 'clock',
      tap: { type: 'default' as const },
      hold: { type: 'card' as const },
      options: {},
    })
    const page: Page = {
      id: 'p',
      title: 'P',
      tiles: [tile('t1', 0, 0, 2), tile('t2', 2, 1), tile('t3', 0, 1)],
    }
    const root = fromGrid(page, 3, 2)
    expect(problems(root)).toBeUndefined()
    const cards = [...walk(root)].filter(node => node.type === 'tile').map(node => node.tile!.id)
    expect(cards.sort()).toEqual(['t1', 't2', 't3'])
    expect(root.children).toHaveLength(2)
    expect(root.children[0].children.map(node => node.size.width)).toEqual(['weight:2', 'weight:1'])
    expect(root.children[1].children.map(node => node.type)).toEqual(['tile', 'spacer', 'tile'])
  })
})

describe('history', () => {
  it('undoes and redoes, and a new edit drops what was undone', () => {
    const hist = history<{ v: number }>()
    hist.reset({ v: 0 })
    hist.record({ v: 1 })
    hist.record({ v: 1 })
    hist.record({ v: 2 })
    expect(hist.undo()).toEqual({ v: 1 })
    expect(hist.undo()).toEqual({ v: 0 })
    expect(hist.undo()).toBeUndefined()
    expect(hist.redo()).toEqual({ v: 1 })
    hist.record({ v: 5 })
    expect(hist.canRedo()).toBe(false)
    expect(hist.undo()).toEqual({ v: 1 })
  })
})
