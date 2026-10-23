/** Where each theme role lands on the tablet — Themes editor sections and blurbs. */

export interface ThemeRoleEntry {
  key: string
  /** Short title shown next to the colour picker. */
  title: string
  /** One line: where this colour is drawn. */
  applies: string
}

export interface ThemeRoleSection {
  id: string
  title: string
  roles: ThemeRoleEntry[]
}

/** Pastels section intro under Themes. */
export const PASTELS_INTRO =
  "Tile accent when the entity is on (by domain or the tile's colour setting)."

/** Ordered role sections for the Themes colour list. */
export const THEME_ROLE_SECTIONS: ThemeRoleSection[] = [
  {
    id: 'chrome',
    title: 'Chrome',
    roles: [
      { key: 'page', title: 'Page', applies: 'Screen background behind every page' },
      { key: 'header', title: 'Header', applies: 'Top bar behind time, date, and stats' },
      {
        key: 'header_dark',
        title: 'Header dark',
        applies: 'Clock and date block on the left of the top bar',
      },
      { key: 'nav', title: 'Nav', applies: 'Bottom navigation bar background' },
      { key: 'nav_on', title: 'Nav on', applies: 'Active bottom-nav pill fill' },
      { key: 'nav_line', title: 'Nav line', applies: 'Thin accent strip above the nav bar' },
    ],
  },
  {
    id: 'surfaces',
    title: 'Surfaces',
    roles: [
      { key: 'card', title: 'Card', applies: 'Domain tile body and detail-card panels' },
      { key: 'card_header', title: 'Card header', applies: 'Tile title strip and card header chrome' },
      {
        key: 'card_edge',
        title: 'Card edge',
        applies: 'Default tile border (a tile can override with its own border colour)',
      },
      { key: 'home', title: 'Home', applies: 'Home composite tile background' },
      {
        key: 'home_strip',
        title: 'Home strip',
        applies: 'People-at-home strip across the bottom of the home tile',
      },
      { key: 'strip', title: 'Strip', applies: 'Weather panel forecast day row background' },
      {
        key: 'track',
        title: 'Track',
        applies: 'Recessed wells: sliders, progress bars, empty choice rows',
      },
    ],
  },
  {
    id: 'ink',
    title: 'Ink',
    roles: [
      {
        key: 'ink',
        title: 'Ink',
        applies: 'Primary text and icons (including selected nav on the teal pill)',
      },
      { key: 'ink_soft', title: 'Ink soft', applies: 'Secondary copy: subtitles, empty states, meta' },
      {
        key: 'ink_dim',
        title: 'Ink dim',
        applies: 'Tertiary: off-state tile icons and inactive labels',
      },
      {
        key: 'ink_faint',
        title: 'Ink faint',
        applies: 'Unavailable entity name, secondary text, and icons',
      },
      {
        key: 'ink_dark',
        title: 'Ink dark',
        applies: 'Text on light strips (person names on the home strip)',
      },
    ],
  },
  {
    id: 'accents',
    title: 'Accents',
    roles: [
      {
        key: 'accent',
        title: 'Accent',
        applies: 'Brand teal: level bars, graphs, spinners, rain %, badges',
      },
      {
        key: 'accent_dark',
        title: 'Accent dark',
        applies: 'Stronger teal: home bars, thermostat mode chrome',
      },
      {
        key: 'accent_soft',
        title: 'Accent soft',
        applies: 'Softer teal: off tile header line, header stats',
      },
      {
        key: 'blue',
        title: 'Blue',
        applies: 'Cool climate: outdoor temp in the header, cool dial arc',
      },
    ],
  },
  {
    id: 'keys',
    title: 'Keys',
    roles: [
      {
        key: 'key',
        title: 'Key',
        applies: 'Default button and control fill (keys, steppers, off badges)',
      },
      {
        key: 'key_on',
        title: 'Key on',
        applies: 'Selected or lit control fill (choice rows, play, primary alert)',
      },
      {
        key: 'key_on_ink',
        title: 'Key on ink',
        applies: 'Text and icons on lit keys and coloured badges (not nav)',
      },
    ],
  },
  {
    id: 'status',
    title: 'Status',
    roles: [
      {
        key: 'ok',
        title: 'Ok',
        applies: 'Reserved for custom fills in the colour picker (not drawn by core UI yet)',
      },
      {
        key: 'warn',
        title: 'Warn',
        applies: 'Heat and warning: thermostat heat, lock card, forecast bars',
      },
      {
        key: 'danger',
        title: 'Danger',
        applies: 'Critical labels and danger-level alert badges',
      },
    ],
  },
  {
    id: 'overlays',
    title: 'Overlays & lines',
    roles: [
      {
        key: 'divider',
        title: 'Divider',
        applies: 'Free-form dividers, forecast bar tracks, energy connectors',
      },
      {
        key: 'scrim',
        title: 'Scrim',
        applies: 'Dim overlay behind detail cards, alerts, and camera',
      },
      {
        key: 'weather_title',
        title: 'Weather title',
        applies: 'Large centred entity name on the weather panel',
      },
    ],
  },
]
