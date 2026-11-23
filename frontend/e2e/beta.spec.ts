import {expect, test} from '@playwright/test';

test('café is usable before choosing chat or a game', async ({page}) => {
  await page.goto('/');
  await expect(page.getByRole('heading', {name: '잠깐 쉬어요.'})).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(page.viewportSize()!.width);
  await page.getByRole('button', {name: '채팅', exact: true}).click();
  await expect(page.getByRole('heading', {name: '채팅방'})).toBeVisible();
  await expect(page.getByRole('radio').first()).toBeVisible();
  await page.getByRole('link', {name: 'breakground'}).click();
  await expect(page.getByRole('heading', {name: '잠깐 쉬어요.'})).toBeVisible();
  await page.getByRole('button', {name: '게임', exact: true}).click();
  await page.getByRole('link', {name: /메일비행기/}).click();
  await expect(page.getByRole('heading', {name: '메일비행기'})).toBeVisible();
});

test('mail plane delivers a shot and returns to the game shelf', async ({page, isMobile}) => {
  await page.goto('/games/mail-plane');
  await page.getByRole('button', {name: '시작', exact: true}).click({timeout: 60_000});
  const target = page.getByRole('button', {name: '표시된 동료에게 메일 날리기'});
  await expect(target).toBeVisible({timeout: 30_000});
  if (isMobile) {
    const bounds = await target.boundingBox();
    expect(bounds).not.toBeNull();
    await page.touchscreen.tap(bounds!.x + bounds!.width / 2, bounds!.y + 16);
  } else {
    await page.getByRole('region', {name: '메일비행기 게임'}).press('Enter');
  }
  await expect(page.getByText('점수 · 1/5번')).toBeVisible();
  await expect(page.getByText(/배달 성공/)).toBeVisible({timeout: 20_000});
  await page.getByRole('link', {name: '게임 목록'}).click();
  await expect(page.getByRole('list', {name: '게임 목록'})).toBeVisible();
});
