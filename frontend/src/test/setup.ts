import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'

import { resetClientState } from '../api/client'

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  // El access token vive en una variable del módulo: cada test empieza sin sesión.
  resetClientState()
})
