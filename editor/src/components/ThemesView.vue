<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { api } from '../api'
import { PASTEL_KEYS, cssColor, themeById, themeVars, themes } from '../model/palette'
import type { ResolvedTheme, Theme } from '../model/types'
import { attempt, refreshThemes, state, toast } from '../store'
import MdiIcon from './MdiIcon.vue'

const emit = defineEmits<{ close: [] }>()

const builtins = computed(() => themes.value.filter((t) => t.builtin))
const selectedId = ref(themes.value[0]?.id ?? '')
const selected = computed(() => themes.value.find((t) => t.id === selectedId.value))

/** The custom theme being edited: its own colours over its base, as stored. */
const draft = ref<Theme | null>(null)
const stored = computed(() => (selected.value && !selected.value.builtin ? JSON.stringify(toTheme(selected.value)) : ''))
const dirty = computed(() => draft.value !== null && JSON.stringify(draft.value) !== stored.value)

function toTheme(t: ResolvedTheme): Theme {
  return { id: t.id, name: t.name, base: t.base, roles: { ...t.overrides.roles }, pastels: { ...t.overrides.pastels } }
}

watch(
  selected,
  (t) => {
    draft.value = t && !t.builtin ? toTheme(t) : null
  },
  { immediate: true },
)

function pick(id: string): void {
  if (dirty.value && !confirm('Leave this theme without saving?')) return
  selectedId.value = id
}

/** The colours drawn: the draft's over its base, or the selected built-in's. */
const shown = computed<ResolvedTheme | undefined>(() => {
  const d = draft.value
  if (!d) return selected.value
  const base = themeById(d.base)
  if (!base) return selected.value
  return { ...base, id: d.id, name: d.name, builtin: false, roles: { ...base.roles, ...d.roles }, pastels: { ...base.pastels, ...d.pastels } }
})

const roleKeys = computed(() => Object.keys(shown.value?.roles ?? {}))
const label = (k: string) => (k === 'card_edge' ? 'card edge (tile border)' : k.replace(/_/g, ' '))

/** A colour input takes `#RRGGBB`: the add-on's `#AARRGGBB` loses its alpha here and keeps it when set. */
const rgb = (hex: string | undefined) => (hex && hex.length === 9 ? `#${hex.slice(3)}` : (hex ?? '#000000'))

function setColour(kind: 'roles' | 'pastels', key: string, value: string): void {
  const d = draft.value
  if (!d) return
  const was = shown.value?.[kind][key] ?? ''
  const hex = (was.length === 9 ? `#${was.slice(1, 3)}${value.slice(1)}` : value).toUpperCase()
  const base = themeById(d.base)?.[kind][key]
  if (base === hex) delete d[kind][key]
  else d[kind][key] = hex
}

function reset(kind: 'roles' | 'pastels', key: string): void {
  if (draft.value) delete draft.value[kind][key]
}

function usedBy(id: string): string[] {
  return (state.layout?.screens ?? []).filter((s) => s.settings.theme === id || s.settings.darkTheme === id).map((s) => s.name)
}

function newId(name: string): string {
  const slug = name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 30) || 'theme'
  const taken = new Set(themes.value.map((t) => t.id))
  let id = slug
  for (let i = 2; taken.has(id); i++) id = `${slug}-${i}`
  return id
}

async function duplicate(t: ResolvedTheme): Promise<void> {
  if (dirty.value && !confirm('Leave this theme without saving?')) return
  const name = `${t.name} copy`
  const copy: Theme = t.builtin
    ? { id: newId(name), name, base: t.id, roles: {}, pastels: {} }
    : { ...toTheme(t), id: newId(name), name }
  const saved = await attempt(() => api.saveTheme(copy))
  if (!saved) return
  await refreshThemes()
  selectedId.value = saved.id
}

async function save(): Promise<void> {
  const d = draft.value
  if (!d) return
  const saved = await attempt(() => api.saveTheme(JSON.parse(JSON.stringify(d))))
  if (!saved) return
  await refreshThemes()
  toast('Theme saved and sent')
}

async function remove(t: ResolvedTheme): Promise<void> {
  const users = usedBy(t.id)
  const warning = users.length ? `\n\n${users.join(', ')} will go back to RPI dark.` : ''
  if (!confirm(`Delete the theme “${t.name}”?${warning}`)) return
  const deleted = await attempt(() => api.deleteTheme(t.id).then(() => true))
  if (!deleted) return
  await refreshThemes()
  selectedId.value = themes.value[0]?.id ?? ''
}

const vars = computed(() => themeVars(shown.value))
</script>

<template>
  <div class="page">
    <header class="row">
      <button class="icon" title="Back to screens" @click="emit('close')"><MdiIcon name="arrow-left" /></button>
      <MdiIcon name="palette-outline" :size="28" />
      <h1 class="grow">Themes</h1>
    </header>
    <p class="muted intro">
      A screen wears one theme by day and another in dark mode and at night (pick them in the screen's settings). Duplicate a
      theme to make your own; its colours are laid over the theme it was made from.
    </p>

    <div class="layout">
      <aside class="card list">
        <div v-for="t in themes" :key="t.id" class="item" :class="{ active: t.id === selectedId }" @click="pick(t.id)">
          <span class="chips">
            <i :style="{ background: cssColor(t.roles.page) }" />
            <i :style="{ background: cssColor(t.roles.card) }" />
            <i :style="{ background: cssColor(t.roles.accent) }" />
          </span>
          <span class="grow">
            {{ t.name }}
            <small class="muted">{{ t.builtin ? 'Built-in' : `From ${themeById(t.base)?.name ?? t.base}` }}</small>
          </span>
          <button class="icon quiet" title="Duplicate" @click.stop="duplicate(t)"><MdiIcon name="content-copy" :size="16" /></button>
          <button v-if="!t.builtin" class="icon quiet" title="Delete" @click.stop="remove(t)"><MdiIcon name="delete-outline" :size="16" /></button>
        </div>
      </aside>

      <section v-if="shown" class="card editor">
        <div class="row">
          <template v-if="draft">
            <label class="field grow">Name<input v-model="draft.name" maxlength="60" /></label>
            <label class="field">
              Made from
              <select v-model="draft.base">
                <option v-for="b in builtins" :key="b.id" :value="b.id">{{ b.name }}</option>
              </select>
            </label>
            <button class="primary save" :disabled="!dirty" @click="save">Save &amp; send</button>
          </template>
          <template v-else>
            <h2 class="grow">{{ shown.name }}</h2>
            <button class="primary" @click="duplicate(shown)"><MdiIcon name="content-copy" :size="16" /> Duplicate to edit</button>
          </template>
        </div>
        <p v-if="usedBy(shown.id).length" class="muted">Worn by {{ usedBy(shown.id).join(', ') }}</p>

        <div class="preview" :style="vars">
          <div class="pv-top"><span class="pv-when">12:34</span><span class="grow" /><span class="pv-blue">14°</span></div>
          <div class="pv-body">
            <div class="pv-card">
              <div class="pv-head">Ceiling light</div>
              <i class="pv-line" :style="{ background: 'var(--rc-p-yellow)' }" />
              <div class="pv-in"><MdiIcon name="lightbulb" :size="26" :style="{ color: 'var(--rc-p-yellow)' }" /><span class="grow" />On</div>
            </div>
            <div class="pv-card">
              <div class="pv-head">Fan</div>
              <i class="pv-line" />
              <div class="pv-in"><MdiIcon name="fan" :size="26" class="pv-dim" /><span class="grow" /><span class="pv-soft">Off</span></div>
            </div>
            <div class="pv-card pv-keys">
              <span class="pv-key on">Heat</span><span class="pv-key">Cool</span>
              <span class="pv-track"><i /></span>
            </div>
          </div>
          <div class="pv-nav"><span class="on"><MdiIcon name="home" :size="18" /></span><span><MdiIcon name="view-dashboard" :size="18" /></span><span><MdiIcon name="cog" :size="18" /></span></div>
        </div>

        <h3>Pastels</h3>
        <div class="colours">
          <label v-for="k in PASTEL_KEYS" :key="k" class="colour" :class="{ own: draft && draft.pastels[k] }">
            <input type="color" :value="rgb(shown.pastels[k])" :disabled="!draft" @input="setColour('pastels', k, ($event.target as HTMLInputElement).value)" />
            <span class="grow">{{ label(k) }}</span>
            <button v-if="draft && draft.pastels[k]" class="icon quiet" title="Back to the base's" @click.prevent="reset('pastels', k)"><MdiIcon name="restore" :size="14" /></button>
          </label>
        </div>

        <h3>Roles</h3>
        <p class="muted hint">Card edge is every tile's border; a tile can override it with its own border colour.</p>
        <div class="colours">
          <label v-for="k in roleKeys" :key="k" class="colour" :class="{ own: draft && draft.roles[k] }">
            <input type="color" :value="rgb(shown.roles[k])" :disabled="!draft" @input="setColour('roles', k, ($event.target as HTMLInputElement).value)" />
            <span class="grow">{{ label(k) }}</span>
            <button v-if="draft && draft.roles[k]" class="icon quiet" title="Back to the base's" @click.prevent="reset('roles', k)"><MdiIcon name="restore" :size="14" /></button>
          </label>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.page {
  max-width: 1040px;
  margin: 0 auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.intro,
.hint {
  margin: 0;
}

.layout {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 14px;
  align-items: start;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px;
}

.item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  border-radius: 10px;
  cursor: pointer;
}

.item.active {
  background: color-mix(in srgb, var(--accent) 14%, transparent);
}

.item small {
  display: block;
}

.chips {
  display: flex;
}

.chips i {
  width: 14px;
  height: 28px;
}

.chips i:first-child {
  border-radius: 6px 0 0 6px;
}

.chips i:last-child {
  border-radius: 0 6px 6px 0;
}

.quiet {
  border-color: transparent;
  background: transparent;
}

.editor {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.editor > .row {
  align-items: flex-end;
}

.save {
  margin-bottom: 1px;
}

.colours {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  gap: 6px;
}

.colour {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 6px;
  border-radius: 8px;
  text-transform: capitalize;
}

.colour.own {
  background: color-mix(in srgb, var(--accent) 10%, transparent);
  font-weight: 500;
}

.colour input {
  width: 34px;
  height: 26px;
  padding: 0 2px;
  flex: none;
}

/* The preview, drawn in the theme's colours. */
.preview {
  border-radius: 14px;
  overflow: hidden;
  background: var(--rc-page);
  color: var(--rc-ink);
  box-shadow: 0 0 0 6px #1d1d1f;
  margin: 6px;
  font-size: 13px;
}

.pv-top {
  display: flex;
  align-items: center;
  height: 34px;
  padding-right: 12px;
  background: var(--rc-header);
}

.pv-when {
  align-self: stretch;
  display: flex;
  align-items: center;
  padding: 0 18px;
  background: var(--rc-header-dark);
}

.pv-blue {
  color: var(--rc-blue);
}

.pv-body {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  padding: 12px;
}

.pv-card {
  border: 2px solid var(--rc-card-edge);
  border-radius: 10px;
  background: var(--rc-card);
  overflow: hidden;
  min-height: 110px;
  display: flex;
  flex-direction: column;
}

.pv-head {
  padding: 6px 10px;
  background: var(--rc-card-header);
}

.pv-line {
  height: 3px;
  background: var(--rc-card-header);
}

.pv-in {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 10px;
}

.pv-dim {
  color: var(--rc-ink-dim);
}

.pv-soft {
  color: var(--rc-ink-soft);
}

.pv-keys {
  padding: 10px;
  gap: 8px;
  justify-content: center;
}

.pv-key {
  padding: 6px;
  border-radius: 14px;
  text-align: center;
  background: var(--rc-key);
}

.pv-key.on {
  background: var(--rc-key-on);
  color: var(--rc-key-on-ink);
}

.pv-track {
  height: 8px;
  border-radius: 4px;
  background: var(--rc-track);
  overflow: hidden;
}

.pv-track i {
  display: block;
  width: 60%;
  height: 100%;
  background: var(--rc-accent);
}

.pv-nav {
  display: flex;
  justify-content: space-evenly;
  border-top: 2px solid var(--rc-nav-line);
  background: var(--rc-nav);
}

.pv-nav span {
  display: grid;
  place-items: center;
  width: 56px;
  height: 36px;
}

.pv-nav span.on {
  background: var(--rc-nav-on);
}
</style>
