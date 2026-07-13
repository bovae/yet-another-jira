// Vitest global setup: jest-dom matchers + DOM cleanup between tests.
import '@testing-library/jest-dom/vitest'
import { afterEach } from 'vitest'
import { cleanup } from '@testing-library/react'

afterEach(() => {
  cleanup()
})

// jsdom lacks the pointer-capture, scroll, and resize-observer APIs that Radix UI primitives
// (shadcn's DropdownMenu, etc.) call during open/close. Stub them so those components can be tested.
Element.prototype.hasPointerCapture = () => false
Element.prototype.setPointerCapture = () => undefined
Element.prototype.releasePointerCapture = () => undefined
Element.prototype.scrollIntoView = () => undefined

globalThis.ResizeObserver = class {
  observe() {}
  unobserve() {}
  disconnect() {}
}
