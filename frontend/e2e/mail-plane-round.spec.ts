import {expect, test} from '@playwright/test';

test('mail plane finishes five deliveries and returns home', async ({page, isMobile}, testInfo) => {
  test.setTimeout(180_000);
  await page.goto('/games/mail-plane');
  await page.getByRole('button', {name: '시작', exact: true}).click({timeout: 60_000});
  const stage = page.getByRole('region', {name: '메일비행기 게임'});
  const target = page.getByRole('button', {name: '표시된 동료에게 메일 날리기'});

  for (let shot = 1; shot <= 5; shot++) {
    await expect(target).toBeVisible({timeout: 20_000});
    if (isMobile) {
      const bounds = await target.boundingBox();
      if (!bounds) throw new Error('The target arrow is outside the viewport');
      await page.touchscreen.tap(bounds.x + bounds.width / 2, bounds.y + 16);
    } else {
      await stage.press('Enter');
    }
    await expect(page.getByText(`점수 · ${shot}/5번`)).toBeVisible();
    await expect(page.getByText(/배달 성공/)).toBeVisible({timeout: 20_000});
  }

  await expect(page.getByText('메일 5통 배달 완료')).toBeVisible();
  await expect(page.getByText(/[1-9][0-9]*점$/)).toBeVisible();
  const completed = testInfo.outputPath('five-deliveries.jpg');
  await page.screenshot({path: completed, type: 'jpeg', quality: 86});
  await testInfo.attach('five deliveries', {path: completed, contentType: 'image/jpeg'});

  await page.getByRole('button', {name: '다시 하기'}).click();
  await expect(page.getByText('점수 · 0/5번')).toBeVisible();
  await expect(target).toBeVisible({timeout: 20_000});
  await page.getByRole('link', {name: '게임 목록'}).click();
  await expect(page.getByRole('list', {name: '게임 목록'})).toBeVisible();
  await page.getByRole('link', {name: 'breakground'}).click();
  await expect(page.getByRole('heading', {name: '잠깐 쉬어요.'})).toBeVisible();
});

test('mail plane warns before getting caught and can restart', async ({page, isMobile}, testInfo) => {
  test.setTimeout(180_000);
  await page.goto('/games/mail-plane');
  await page.getByRole('button', {name: '시작', exact: true}).click({timeout: 60_000});
  const stage = page.getByRole('region', {name: '메일비행기 게임'});
  const target = page.getByRole('button', {name: '표시된 동료에게 메일 날리기'});
  await expect(target).toBeVisible({timeout: 20_000});
  const work = page.getByRole('button', {name: '일하는 척', exact: true});
  await expect(work).toBeVisible({timeout: 60_000});
  if (isMobile) await work.tap();
  else await stage.press('f');
  await expect(page.getByText(/일하는 척하는 중/)).toBeVisible();
  await expect(target).toBeHidden();
  await expect(target).toBeVisible({timeout: 60_000});

  await expect(page.getByText('들켰어요 · 게임 오버')).toBeVisible({timeout: 60_000});
  const caught = testInfo.outputPath('game-over.jpg');
  await page.screenshot({path: caught, type: 'jpeg', quality: 86});
  await testInfo.attach('caught by a coworker', {path: caught, contentType: 'image/jpeg'});
  await page.getByRole('button', {name: '다시 하기'}).click();
  await expect(page.getByText('점수 · 0/5번')).toBeVisible();
  await expect(target).toBeVisible({timeout: 20_000});
});
