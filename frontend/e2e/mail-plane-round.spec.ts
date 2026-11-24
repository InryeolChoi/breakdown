import {expect, test} from '@playwright/test';

test('mail plane finishes five deliveries and returns home', async ({page, isMobile}, testInfo) => {
  test.setTimeout(180_000);
  await page.goto('/games/mail-plane');
  await page.getByRole('button', {name: '시작', exact: true}).click({timeout: 60_000});
  const stage = page.getByRole('region', {name: '메일비행기 게임'});
  const target = page.getByRole('button', {name: '표시된 동료에게 메일 날리기'});

  let previousScore = 0;
  for (let shot = 1; shot <= 5; shot++) {
    await expect(target).toBeVisible({timeout: 20_000});
    if (shot === 2) {
      await expect(page.getByText(/배달 성공! \+\d+/)).toBeVisible();
    }
    if (isMobile) {
      const bounds = await target.boundingBox();
      if (!bounds) throw new Error('The target arrow is outside the viewport');
      await page.touchscreen.tap(bounds.x + bounds.width / 2, bounds.y + 16);
    } else {
      await stage.press('Enter');
    }
    const shotCount = page.getByText(`점수 · ${shot}/5번`, {exact: true});
    await expect(shotCount).toBeVisible();
    const score = shotCount.locator('..').locator('strong');
    await expect.poll(async () => Number(await score.innerText()), {timeout: 20_000}).toBeGreaterThan(previousScore);
    previousScore = Number(await score.innerText());
  }

  await expect(page.getByText('메일 5통 배달 완료')).toBeVisible();
  await expect(page.getByText(/[1-9][0-9]*점$/)).toBeVisible();
  const completed = testInfo.outputPath('five-deliveries.jpg');
  await page.screenshot({path: completed, type: 'jpeg', quality: 86});
  await testInfo.attach('five deliveries', {path: completed, contentType: 'image/jpeg'});

  await page.getByRole('button', {name: '다시 하기'}).click();
  await expect(page.getByText('점수 · 0/5번')).toBeVisible();
  await expect(page.getByText(/배달 성공! \+\d+/)).toHaveCount(0);
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
  // Respond in the frame that reveals the warning. Driver round trips can
  // exceed the brief reaction window on a shared software-rendered runner.
  await stage.evaluate((element, mobile) => new Promise<void>((resolve, reject) => {
    const observer = new MutationObserver(tryWork);
    const deadline = setTimeout(() => {
      observer.disconnect();
      reject(new Error('The warning did not offer an option to work'));
    }, 60_000);
    function tryWork() {
      const work = [...element.querySelectorAll('button')].find(button => button.textContent?.trim() === '일하는 척');
      if (!work || work.disabled) return;
      observer.disconnect();
      clearTimeout(deadline);
      if (mobile) work.click();
      else element.dispatchEvent(new KeyboardEvent('keydown', {key: 'f', bubbles: true}));
      resolve();
    }
    observer.observe(element, {childList: true, subtree: true});
    tryWork();
  }), isMobile);
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
