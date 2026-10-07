import { z } from 'zod'

// La CSP de producción (default-src 'self', sin 'unsafe-eval') no permite new Function: sin esto Zod
// lo intentaría para optimizar y el navegador registraría una violación de CSP en cada carga.
z.config({ jitless: true })
