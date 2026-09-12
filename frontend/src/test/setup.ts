import '@testing-library/jest-dom/vitest'
import { cleanup, configure } from '@testing-library/react'
import { afterAll, afterEach, beforeAll, beforeEach } from 'vitest'
import { server } from './handlers'

// findBy and waitFor give up after 1 s by default. A cold first render passed that while the element was
// still on its way (see testTimeout in vite.config.ts). Waiting longer changes nothing for a passing
// assertion and only delays a failing one.
configure({ asyncUtilTimeout: 5_000 })

/**
 * jsdom implements none of matchMedia, ResizeObserver, or a failing localStorage, and all three are
 * load-bearing: antd's Grid.useBreakpoint reads matchMedia, antd's Table and Segmented observe size,
 * and the blocked-storage edge case cannot be tested against a store that always succeeds. Without
 * these, antd components fail to render under test and the failure looks like a component bug.
 */

type Listener = (event: MediaQueryListEvent) => void

const media = {
  colorScheme: null as 'light' | 'dark' | null,
  reducedMotion: false,
  width: 1280,
  listeners: new Set<{ query: string; listener: Listener }>(),
}

function evaluate(query: string): boolean {
  if (query.includes('prefers-color-scheme: dark')) return media.colorScheme === 'dark'
  if (query.includes('prefers-color-scheme: light')) return media.colorScheme === 'light'
  if (query.includes('prefers-reduced-motion: reduce')) return media.reducedMotion
  const min = query.match(/min-width:\s*(\d+)/)
  const max = query.match(/max-width:\s*(\d+)/)
  if (min && Number(media.width) < Number(min[1])) return false
  if (max && Number(media.width) > Number(max[1])) return false
  return Boolean(min || max)
}

function installMatchMedia() {
  window.matchMedia = (query: string) => {
    const mql = {
      get matches() {
        return evaluate(query)
      },
      media: query,
      onchange: null,
      addEventListener: (_: string, listener: Listener) => media.listeners.add({ query, listener }),
      removeEventListener: (_: string, listener: Listener) => {
        media.listeners.forEach((entry) => entry.listener === listener && media.listeners.delete(entry))
      },
      addListener: (listener: Listener) => media.listeners.add({ query, listener }),
      removeListener: (listener: Listener) => {
        media.listeners.forEach((entry) => entry.listener === listener && media.listeners.delete(entry))
      },
      dispatchEvent: () => true,
    }
    return mql as unknown as MediaQueryList
  }
}

function notify() {
  media.listeners.forEach(({ query, listener }) =>
    listener({ matches: evaluate(query), media: query } as MediaQueryListEvent),
  )
}

/** Sets what the operating system reports as its colour scheme, and fires change events. */
export function setSystemColorScheme(scheme: 'light' | 'dark' | null) {
  media.colorScheme = scheme
  notify()
}

/** Sets the viewport width antd's breakpoints see, and fires change events. */
export function setViewportWidth(width: number) {
  media.width = width
  window.innerWidth = width
  notify()
  window.dispatchEvent(new Event('resize'))
}

export function setReducedMotion(reduce: boolean) {
  media.reducedMotion = reduce
  notify()
}

/** Makes every localStorage read, write, and removal throw, as a blocked store does. */
export function blockStorage() {
  const fail = () => {
    throw new DOMException('The operation is insecure.', 'SecurityError')
  }
  Object.defineProperty(window, 'localStorage', {
    configurable: true,
    value: { getItem: fail, setItem: fail, removeItem: fail, clear: fail, key: fail, length: 0 },
  })
}

const realStorage = window.localStorage

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

beforeAll(() => {
  installMatchMedia()
  globalThis.ResizeObserver = ResizeObserverStub as unknown as typeof ResizeObserver
  // antd's Table measures scrollbars through getComputedStyle on pseudo-elements, which jsdom rejects.
  const original = window.getComputedStyle
  window.getComputedStyle = (element: Element) => original(element)
  server.listen({ onUnhandledRequest: 'error' })
})

beforeEach(() => {
  media.colorScheme = null
  media.reducedMotion = false
  media.width = 1280
  media.listeners.clear()
  Object.defineProperty(window, 'localStorage', { configurable: true, value: realStorage })
  realStorage.clear()
})

afterEach(() => {
  cleanup()
  server.resetHandlers()
})

afterAll(() => server.close())
