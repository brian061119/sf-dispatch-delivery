// Single source of truth for the product brand name.
// Rename here once — the header logo and the browser tab title follow.
// Scope note: this constant covers UI-rendered brand text. Prose in docs
// (README/HANDOFF/walkthrough) and mock seed data carry the name as plain
// text — update those too on a real rename. package.json "name" is an
// internal npm identifier and is intentionally NOT coupled to the brand.
export const BRAND_NAME = 'Dispatch & Delivery';

// ---------- Shared design tokens (page-level styling reads from here) ----------
// Brand gradient (matches the antd colorPrimary #1677ff on the left end).
export const BRAND_GRADIENT = 'linear-gradient(135deg, #1677ff 0%, #722ed1 100%)';
// Gradient + subtle dot grid, used by every "designed surface" (track hero,
// auth brand panel) so public pages read as one family.
export const BRAND_HERO_BG = `radial-gradient(rgba(255,255,255,.18) 1.5px, transparent 1.5px), ${BRAND_GRADIENT}`;
export const HERO_BG_SIZE = '22px 22px, 100% 100%';
export const CARD_SHADOW = '0 2px 12px rgba(15,40,80,.06)';
// Pages render inside a centered 1100px content column (App.jsx). Pages that
// need edge-to-edge visuals (track hero, auth split-screen) spread back to the
// real viewport with this symmetric trick: shift left by half the container,
// then translate back by half the viewport. More reliable than negative
// margin-left percentages, which resolve against the wrapper and can leave a
// gap on one side. body{overflow-x:hidden} (index.css) eats the scrollbar
// rounding error.
export const FULL_BLEED = {
    width: '100vw',
    position: 'relative',
    left: '50%',
    transform: 'translateX(-50%)',
};
