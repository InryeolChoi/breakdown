import {expect, test} from '@playwright/test';

test('café is usable before choosing chat or a game', async ({page}) => {
  await page.goto('/');
  await expect(page.getByRole('heading', {name: '잠깐 쉬어요.'})).toBeVisible();
  await expect(page.getByLabel('닉네임')).toHaveCount(0);
  const shelf = page.getByRole('group', {name: '쉬는 방법', exact: true});
  await expect(shelf).toBeInViewport();
  const theme = page.locator('summary[aria-label="화면 분위기"]');
  await theme.focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('button', {name: '온기'})).toHaveAttribute('aria-pressed', 'true');
  const glacier = page.getByRole('button', {name: '빙하'});
  await glacier.focus();
  await page.keyboard.press('Enter');
  await expect(glacier).toHaveAttribute('aria-pressed', 'true');
  await page.keyboard.press('Escape');
  await expect(theme).toBeFocused();
  await expect(page.getByRole('group', {name: '분위기 선택'})).toBeHidden();
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(page.viewportSize()!.width);
  await page.getByRole('button', {name: '채팅', exact: true}).click();
  await expect(page.getByRole('heading', {name: '채팅방'})).toBeVisible();
  await expect(page.getByRole('radio').first()).toBeVisible();
  await page.getByRole('link', {name: 'breakground'}).click();
  await expect(page.getByRole('heading', {name: '잠깐 쉬어요.'})).toBeVisible();
  const game = page.getByRole('button', {name: '게임', exact: true});
  await game.focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('heading', {name: '게임', exact: true})).toBeFocused();
  const mailPlane = page.getByRole('link', {name: /메일비행기/});
  await page.keyboard.press('Tab');
  await expect(mailPlane).toBeFocused();
  await page.keyboard.press('Tab');
  await expect(page.getByRole('button', {name: '선반 열기'})).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(game).toBeFocused();
  await game.click();
  await mailPlane.click();
  await expect(page.getByRole('heading', {name: '메일비행기'})).toBeVisible();
});

test('the shelf remains usable when room loading fails or returns no rooms', async ({page}) => {
  let roomsAvailable = false;
  await page.route('**/api/rooms', async route => {
    await route.fulfill(roomsAvailable
      ? {json: []}
      : {status: 503, json: {message: '방 목록을 불러오지 못했어요.'}});
  });
  await page.goto('/');
  await page.getByRole('button', {name: '채팅', exact: true}).click();
  await expect(page.getByRole('status')).toContainText('방 목록을 불러오지 못했어요.');
  roomsAvailable = true;
  await page.getByRole('button', {name: '다시 시도'}).click();
  await expect(page.getByText('지금은 열린 방이 없어요.')).toBeVisible();
  await page.getByRole('button', {name: '선반 열기'}).click();
  await page.getByRole('button', {name: '명상', exact: true}).click();
  await expect(page.getByRole('heading', {name: '명상', exact: true})).toBeVisible();
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

test('a failed report stays readable and preserves the reason for retry', async ({page, browser, baseURL}) => {
  const senderContext = await browser.newContext({baseURL});
  try {
    const sender = await senderContext.newPage();
    await Promise.all([page.goto('/minimal'), sender.goto('/minimal')]);
    await Promise.all([page, sender].map(async participant => {
      await participant.getByRole('button', {name: '채팅', exact: true}).click();
      await participant.getByRole('radio').first().check();
      await participant.getByRole('button', {name: '입장하기'}).click();
      await expect(participant.getByRole('textbox', {name: '메시지', exact: true})).toBeEnabled();
    }));
    const message = '베타 신고 검증 메시지';
    await sender.getByRole('textbox', {name: '메시지', exact: true}).fill(message);
    await sender.getByRole('button', {name: '보내기', exact: true}).click();
    const article = page.getByRole('article').filter({hasText: message});
    await article.getByRole('button', {name: '신고', exact: true}).click();
    const report = page.getByRole('dialog', {name: '이 메시지로 신고할까요?'});
    const reason = report.getByRole('textbox', {name: '신고 사유'});
    const submit = report.getByRole('button', {name: '신고하기'});
    await reason.fill('신고 처리 검증용');
    await page.route('**/api/rooms/*/reports', route => route.fulfill({
      status: 429, json: {message: '신고 요청이 너무 많아요. 잠시 후 다시 시도해주세요.'},
    }), {times: 1});
    await submit.click();
    await expect(report.getByRole('alert')).toHaveText(/신고 요청이 너무 많아요/);
    await expect(reason).toHaveValue('신고 처리 검증용');
    await expect(article.getByRole('button', {name: '신고됨'})).toHaveCount(0);
    await submit.click();
    await expect(report).toBeHidden();
    await expect(article.getByRole('button', {name: '신고됨'})).toBeDisabled();
    await expect(page.getByText('신고가 접수됐어요.', {exact: true})).toBeVisible();
  } finally {
    await senderContext.close();
  }
});
