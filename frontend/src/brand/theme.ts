import { createTheme, type MantineColorsTuple } from '@mantine/core'

/**
 * TransitHub brand theme, light and dark. Deep navy is the foundation,
 * Electric Teal the signature colour, Warm Amber a small accent (attention
 * and "waiting" states), Sky Blue a supporting colour.
 *
 * Contrast (WCAG): white text on #12B8A6 is only 2.5:1, so teal surfaces
 * carry navy text (6.8:1). `autoContrast` with `black` = navy does this for
 * every filled component automatically, in both colour schemes.
 * Tinted ("light") badges and alerts use shade 9 text in light mode and
 * shade 0 in dark mode (Mantine's defaults), which stay readable in both.
 */

// Shade 6 is the exact brand colour; 8–9 are for text on pale backgrounds.
const teal: MantineColorsTuple = ['#E7FAF7', '#C8F1EB', '#97E4D9', '#62D5C6', '#38C9B7', '#1FC1AE', '#12B8A6', '#0E9C8D', '#0B7A6E', '#075B52']
const navy: MantineColorsTuple = ['#EEF2F8', '#DCE3EE', '#B7C3D8', '#8E9EBC', '#6C7FA2', '#4D6189', '#2E4268', '#1E2E50', '#101C35', '#0B1222']
const amber: MantineColorsTuple = ['#FEF6E8', '#FDEBCB', '#FAD79A', '#F7C266', '#F4B24A', '#F3AB3F', '#F2A93B', '#D58E24', '#A66A12', '#7A4D0A']
const sky: MantineColorsTuple = ['#EAF4FE', '#D3E7FD', '#A7CFFB', '#77B5F8', '#52A2F6', '#4299F5', '#3997F5', '#2280E0', '#1765B4', '#0E4C8A']

/**
 * Dark mode surfaces, built from the brand navy rather than neutral grey.
 * Mantine reads: 0 text, 2 dimmed text, 4 borders, 5 hover, 6 inputs,
 * 7 body (modals, menus), 8–9 deeper layers.
 */
const dark: MantineColorsTuple = ['#E6ECF5', '#C9D3E3', '#A9B6CC', '#7F8FAD', '#2E4066', '#25365A', '#1C2B4A', '#16233F', '#101C35', '#0B1222']

export const theme = createTheme({
  colors: { teal, navy, sky, blue: sky, amber, orange: amber, dark },
  primaryColor: 'teal',
  primaryShade: { light: 6, dark: 6 },
  black: '#101C35',
  white: '#FFFFFF',
  autoContrast: true,
  luminanceThreshold: 0.3,
  fontFamily: "'Inter Variable', 'Segoe UI', Arial, sans-serif",
  headings: { fontFamily: "'Inter Variable', 'Segoe UI', Arial, sans-serif", fontWeight: '700' },
  defaultRadius: 'md',
  components: {
    Button: { defaultProps: { fw: 600 } },
  },
})
