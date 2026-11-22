import { expect, Page, test } from '@playwright/test';

async function signIn(page: Page, user: 'atlas' | 'medina' | 'ops', lang: 'en' | 'fr' = 'en') {
  await page.addInitScript((l) => localStorage.setItem('settlement-copilot.lang', l), lang);
  await page.goto('/login');
  await expect(page.getByTestId('mode-badge')).toContainText(lang === 'fr' ? 'Modèle démo hors ligne' : 'Offline demo model');
  await page.getByTestId(`account-${user}`).click();
  await page.getByTestId('sign-in').click();
  await expect(page.getByTestId('user-chip')).toContainText(user);
}

const lastAnswer = (page: Page) => page.getByTestId('assistant-message').last();

test('atlas asks which courier owes the most and gets a streamed, sourced, charted answer', async ({ page }) => {
  await signIn(page, 'atlas');
  await page.getByTestId('suggestion').filter({ hasText: 'Which courier owes me the most?' }).click();

  // live: the tool call shows up in the trace panel, then tokens stream in
  await expect(page.getByTestId('trace-item').first()).toContainText('getCourierBalances');
  await expect(lastAnswer(page).getByTestId('answer')).toContainText('Rapido Express');
  await expect(lastAnswer(page).getByTestId('answer')).toContainText('Total still owed by couriers: 4845.50 MAD.');
  await expect(lastAnswer(page).getByTestId('answer')).toContainText('4315.00 MAD');

  await expect(lastAnswer(page).getByTestId('chart').locator('canvas')).toBeVisible();
  await expect(lastAnswer(page).getByTestId('source-chip')).toHaveCount(2);
  await expect(lastAnswer(page).getByTestId('grounded')).toContainText('amounts verified');
});

test('a follow-up uses memory, and a flagged statement links back to MSE Settle', async ({ page }) => {
  await signIn(page, 'atlas');
  await page.getByTestId('question').fill('How much does Speedex owe me?');
  await page.getByTestId('send').click();
  await expect(lastAnswer(page).getByTestId('answer')).toContainText('530.50 MAD');
  await expect(page.getByTestId('trace-item')).toHaveCount(2); // findCourier, then getCourierBalance

  await page.getByTestId('question').fill('And Rapido?');
  await page.getByTestId('question').press('Enter');
  await expect(lastAnswer(page).getByTestId('answer')).toContainText('Rapido Express still owes you 4315.00 MAD');

  await page.getByTestId('question').fill('Why was statement #1 flagged?');
  await page.getByTestId('send').click();
  await expect(lastAnswer(page).getByTestId('answer')).toContainText('SPX-UNKNOWN01');
  await expect(lastAnswer(page).getByTestId('source-chip').first()).toHaveAttribute('href', /\/statements\/1$/);
});

test('French answers use French formatting', async ({ page }) => {
  await signIn(page, 'atlas', 'fr');
  await page.getByTestId('suggestion').filter({ hasText: 'Quel livreur me doit le plus ?' }).click();
  await expect(lastAnswer(page).getByTestId('answer')).toContainText('4315,00 MAD');
  await expect(lastAnswer(page).getByTestId('grounded')).toContainText('montants vérifiés');
});

test('medina cannot read atlas statement through the copilot, and the copilot refuses to guess', async ({ page }) => {
  await signIn(page, 'medina');
  await page.getByTestId('question').fill('Why was statement #1 flagged?');
  await page.getByTestId('send').click();
  await expect(lastAnswer(page).getByTestId('answer')).toContainText("couldn't find statement #1");
  await expect(page.getByTestId('trace-item').first()).toContainText('Not found');

  await page.getByTestId('question').fill('Write me a poem');
  await page.getByTestId('send').click();
  await expect(lastAnswer(page).getByTestId('answer')).toContainText("won't guess outside that");
});
