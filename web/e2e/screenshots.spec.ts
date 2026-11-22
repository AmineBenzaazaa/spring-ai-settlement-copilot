import { expect, Page, test } from '@playwright/test';

// Captures the README screenshots. Run with: SCREENSHOTS=1 npx playwright test screenshots
test.skip(!process.env['SCREENSHOTS'], 'set SCREENSHOTS=1 to capture docs/screenshots');

const out = (name: string) => `../docs/screenshots/${name}.png`;

async function signIn(page: Page, lang: 'en' | 'fr') {
  await page.addInitScript((l) => localStorage.setItem('settlement-copilot.lang', l), lang);
  await page.goto('/login');
  await expect(page.getByTestId('mode-badge')).toBeVisible();
}

test('capture screenshots', async ({ page }) => {
  await signIn(page, 'en');
  await page.screenshot({ path: out('login') });

  await page.getByTestId('sign-in').click();
  await page.getByTestId('suggestion').filter({ hasText: 'Which courier owes me the most?' }).click();
  await expect(page.getByTestId('grounded').last()).toBeVisible();
  await page.getByTestId('question').fill('Why was statement #1 flagged?');
  await page.getByTestId('send').click();
  await expect(page.getByTestId('grounded').last()).toBeVisible();
  await page.waitForTimeout(800); // chart animation
  await page.screenshot({ path: out('chat-statement-flagged') });

  await page.getByRole('button', { name: 'New chat' }).click();
  await page.getByTestId('lang-fr').click();
  await page.getByTestId('suggestion').filter({ hasText: 'Quel livreur me doit le plus ?' }).click();
  await expect(page.getByTestId('grounded').last()).toBeVisible();
  await page.waitForTimeout(800);
  await page.screenshot({ path: out('chat-courier-ranking-fr') });
});
