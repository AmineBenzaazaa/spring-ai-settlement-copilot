import { expect, Page, test } from '@playwright/test';

// Captures the README screenshots. Run with: SCREENSHOTS=1 npx playwright test screenshots
test.skip(!process.env['SCREENSHOTS'], 'set SCREENSHOTS=1 to capture docs/screenshots');

const out = (name: string) => `../docs/screenshots/${name}.png`;

async function askAndWait(page: Page, question: string) {
  const before = await page.getByTestId('assistant-message').count();
  await page.getByTestId('question').fill(question);
  await page.getByTestId('send').click();
  const answer = page.getByTestId('assistant-message').nth(before);
  await expect(answer.getByTestId('grounded')).toBeVisible();
  await page.waitForTimeout(900); // let the chart animation finish
}

test.use({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 2 });

test('capture screenshots', async ({ page }) => {
  await page.addInitScript(() => {
    if (!sessionStorage.getItem('shots')) {
      localStorage.setItem('settlement-copilot.lang', 'en');
      localStorage.setItem('mse-copilot.theme', 'light');
      sessionStorage.setItem('shots', '1');
    }
  });
  await page.goto('/login');
  await expect(page.getByTestId('mode-badge')).toBeVisible();
  await page.screenshot({ path: out('login') });

  await page.getByTestId('sign-in').click();
  await expect(page.getByTestId('suggestion').first()).toBeVisible();
  await page.screenshot({ path: out('welcome') });
  await askAndWait(page, 'Why was statement #1 flagged?');
  await page.screenshot({ path: out('chat-statement-flagged') });

  await page.getByRole('button', { name: 'New chat' }).click();
  await askAndWait(page, 'Which courier owes me the most?');
  await page.getByTestId('theme-toggle').click();
  await page.waitForTimeout(900);
  await page.screenshot({ path: out('chat-courier-ranking-dark') });
  await page.getByTestId('theme-toggle').click();

  await page.getByRole('button', { name: 'New chat' }).click();
  await page.getByTestId('lang-fr').click();
  await askAndWait(page, 'Quel livreur me doit le plus ?');
  await page.screenshot({ path: out('chat-courier-ranking-fr') });
});
