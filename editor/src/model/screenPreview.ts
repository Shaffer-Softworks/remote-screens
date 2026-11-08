import type { PlayerStatus, Screen } from './types'

/** Use the configured home page, with a safe fallback for older layouts. */
export function previewPage(screen: Screen) {
  return screen.pages.find(page => page.id === screen.homePage) ?? screen.pages[0]
}

export function previewDevice(screen: Screen, players: PlayerStatus[]) {
  const own = screen.designSize
  if (own && own.widthDp > 0 && own.heightDp > 0)
    return { w: own.widthDp, h: own.heightDp }
  const player = players.find(player =>
    player.screenId === screen.id && player.widthDp > 0 && player.heightDp > 0,
  )
  return player ? { w: player.widthDp, h: player.heightDp } : { w: 1280, h: 800 }
}

export function previewThemeId(screen: Screen) {
  return (screen.settings.darkMode ? screen.settings.darkTheme : screen.settings.theme) ?? 'rpi'
}
