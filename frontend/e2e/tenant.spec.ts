import {test, expect} from '@playwright/test';

test('chaque cabinet a son annuaire et sa couleur', async ({page}) => {
  await page.goto('/c/cabinet-a/');
  await expect(page.getByText('4 praticiens à découvrir')).toBeVisible();
  const colorOfA = await page.locator('.hero').evaluate(element => getComputedStyle(element).backgroundColor);

  await page.goto('/c/cabinet-b/');
  await expect(page.getByText('7 praticiens à découvrir')).toBeVisible();
  const colorOfB = await page.locator('.hero').evaluate(element => getComputedStyle(element).backgroundColor);

  expect(colorOfA).toBe('rgb(8, 115, 185)');
  expect(colorOfB).toBe('rgb(11, 122, 92)');
});

test('un rendez-vous pris dans A reste invisible et injoignable depuis B', async ({page}) => {
  await page.goto('/c/cabinet-a/');
  await page.getByRole('button', {name: 'Se connecter', exact: true}).click();
  await page.getByLabel('Adresse e-mail', {exact: true}).fill('patient@medislot.test');
  await page.getByLabel('Mot de passe', {exact: true}).fill('MediSlot-demo-2026!');
  await page.getByRole('dialog').getByRole('button', {name: 'Se connecter', exact: true}).click();
  await expect(page.getByRole('button', {name: 'Camille · Déconnexion'})).toBeVisible();

  const booked = page.waitForResponse(
    response => response.url().endsWith('/api/appointments') && response.request().method() === 'POST',
  );
  await page.getByRole('button', {name: /Réserver avec/}).first().click();
  await page.getByRole('button', {name: 'Confirmer le rendez-vous', exact: true}).click();
  const appointment = await (await booked).json();
  expect((await booked).request().headers()['x-clinic']).toBe('cabinet-a');
  await expect(page.getByText('Votre rendez-vous est confirmé.', {exact: true})).toBeVisible();
  await expect(page.getByText(appointment.professionalName).first()).toBeVisible();

  await page.goto('/c/cabinet-b/');
  await expect(page).toHaveURL(/\/c\/cabinet-b\/$/);
  await expect(page.locator('.hero, .appointment').first()).toBeVisible();
  await page.getByRole('button', {name: 'Mes rendez-vous'}).click();
  await expect(page.getByRole('heading', {name: /rendez-vous/i}).first()).toBeVisible();
  await expect(page.getByText(appointment.professionalName)).toHaveCount(0);

  const fromA = await page.request.get(`/api/appointments/${appointment.id}`, {headers: {'X-Clinic': 'cabinet-a'}});
  const fromB = await page.request.get(`/api/appointments/${appointment.id}`, {headers: {'X-Clinic': 'cabinet-b'}});
  const listOfB = await page.request.get('/api/appointments', {headers: {'X-Clinic': 'cabinet-b'}});
  expect(fromA.status()).toBe(200);
  expect(fromB.status()).toBe(404);
  expect((await listOfB.json()).map((item: {id: number}) => item.id)).not.toContain(appointment.id);

  const csrf = await (await page.request.get('/api/csrf')).json();
  const cancelled = await page.request.post(`/api/appointments/${appointment.id}/cancel`, {
    headers: {'X-Clinic': 'cabinet-a', [csrf.headerName]: csrf.token},
  });
  expect(cancelled.status()).toBe(200);
});

test('une personne connectée ne lit rien d’un cabinet qui n’est pas le sien', async ({page}) => {
  await page.goto('/c/cabinet-a/');
  await page.getByRole('button', {name: 'Se connecter', exact: true}).click();
  await page.getByLabel('Adresse e-mail', {exact: true}).fill('autre@medislot.test');
  await page.getByLabel('Mot de passe', {exact: true}).fill('MediSlot-demo-2026!');
  await page.getByRole('dialog').getByRole('button', {name: 'Se connecter', exact: true}).click();
  await expect(page.getByRole('button', {name: 'Alex · Déconnexion'})).toBeVisible();

  const refused = await page.request.get('/api/appointments', {headers: {'X-Clinic': 'cabinet-b'}});
  expect(refused.status()).toBe(404);
});

test('an administrator sees the clinic list', async ({page}) => {
  await page.goto('/c/cabinet-a/');
  await page.getByRole('button', {name: 'Se connecter', exact: true}).click();
  await page.getByLabel('Adresse e-mail', {exact: true}).fill('admin@medislot.test');
  await page.getByLabel('Mot de passe', {exact: true}).fill('MediSlot-demo-2026!');
  await page.getByRole('dialog').getByRole('button', {name: 'Se connecter', exact: true}).click();
  await expect(page.getByRole('button', {name: 'Sacha · Déconnexion'})).toBeVisible();

  await page.getByRole('button', {name: 'Admin', exact: true}).click();
  await expect(page.getByRole('heading', {name: 'Cabinets (2)'})).toBeVisible();
  await expect(page.getByRole('row', {name: /Cabinet des Tilleuls/})).toContainText('4');
  await expect(page.getByRole('row', {name: /Maison de santé du Canal/})).toContainText('7');
  await page.getByLabel('Ville').selectOption('Lyon');
  await expect(page.getByRole('row', {name: /Cabinet des Tilleuls/})).toHaveCount(0);
});

test('a patient is refused the clinic list', async ({page}) => {
  await page.goto('/c/cabinet-a/');
  await page.getByRole('button', {name: 'Se connecter', exact: true}).click();
  await page.getByLabel('Adresse e-mail', {exact: true}).fill('patient@medislot.test');
  await page.getByLabel('Mot de passe', {exact: true}).fill('MediSlot-demo-2026!');
  await page.getByRole('dialog').getByRole('button', {name: 'Se connecter', exact: true}).click();
  await expect(page.getByRole('button', {name: 'Camille · Déconnexion'})).toBeVisible();
  expect((await page.request.get('/api/platform/clinics')).status()).toBe(403);
});
