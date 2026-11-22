/**
 * The product brand, in one place. Every screen, the page title and the product switcher read
 * from here; the logo lives in shared/logo.ts and /brand.
 */
export const BRAND = {
  name: 'MSE',
  suite: 'MSE Transport',
  product: 'Copilot',
} as const;

export type ProductId = 'dispatch' | 'settle' | 'copilot';

/**
 * The three apps of the suite. Defaults are the local docker compose ports; this stack runs its
 * own Settle web on :3201 (the copilot API also reports it as `deskUrl`, which wins when present).
 * Override at runtime with window.__MSE_PRODUCTS__ = { dispatch: 'https://...' }.
 */
const overrides = (globalThis as { __MSE_PRODUCTS__?: Partial<Record<ProductId, string>> }).__MSE_PRODUCTS__ ?? {};

export const PRODUCTS: Array<{ id: ProductId; name: string; url: string }> = [
  { id: 'dispatch', name: 'Dispatch', url: overrides.dispatch ?? 'http://localhost:3100' },
  { id: 'settle', name: 'Settle', url: overrides.settle ?? 'http://localhost:3201' },
  { id: 'copilot', name: 'Copilot', url: overrides.copilot ?? 'http://localhost:3300' },
];

export const CURRENT_PRODUCT: ProductId = 'copilot';
export const PRODUCT_TITLE = `${BRAND.name} ${BRAND.product}`;
