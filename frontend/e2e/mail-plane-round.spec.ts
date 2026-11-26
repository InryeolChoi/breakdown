import {expect, test} from '@playwright/test';

test.describe('mail plane touch input', () => {
  test.use({hasTouch: true});

  test('a pull steers left and right before it fires without a follow-up click', async ({page, context}) => {
    await page.goto('/games/mail-plane');
    await page.getByRole('button', {name: '시작', exact: true}).click({timeout: 60_000});
    const arrow = page.getByRole('button', {name: '표시된 동료에게 메일 날리기'});
    await expect(arrow).toBeVisible({timeout: 30_000});
    // Control the arrow's motion here so only the move during the tap is under test.
    await page.addStyleTag({content: 'button[aria-label="표시된 동료에게 메일 날리기"] { left: 50% !important; top: 45% !important; }'});
    const cdp = await context.newCDPSession(page);
    const point = await arrow.evaluate(element => {
      const bounds = element.getBoundingClientRect();
      return {x: bounds.left + bounds.width / 2, y: bounds.top + 16};
    });
    await cdp.send('Input.dispatchTouchEvent', {type: 'touchStart', touchPoints: [point]});
    await cdp.send('Input.dispatchTouchEvent', {type: 'touchMove', touchPoints: [{x: point.x, y: point.y + 55}]});
    await cdp.send('Input.dispatchTouchEvent', {type: 'touchCancel', touchPoints: []});
    await expect(page.getByText('점수 · 0/5번', {exact: true})).toBeVisible();
    await page.touchscreen.tap(point.x, point.y);
    await expect(page.getByText('점수 · 0/5번', {exact: true})).toBeVisible();

    await page.getByRole('button', {name: '처음부터', exact: true}).click();
    await expect(arrow).toBeVisible({timeout: 30_000});
    const start = await arrow.evaluate(element => {
      const reticle = element.closest('section')?.querySelector<HTMLElement>('div[class*="_crosshair"]');
      if (!reticle) throw new Error('No aiming reticle');
      let samples: number[] = [];
      element.addEventListener('pointerdown', () => {
        element.style.translate = '-100px';
      }, {once: true});
      window.addEventListener('pointermove', () => {
        samples = [...samples, parseFloat(reticle.style.left)];
        reticle.dataset.observedAim = JSON.stringify(samples);
      });
      element.addEventListener('touchend', event => event.preventDefault(), {once: true, passive: false});
      const bounds = element.getBoundingClientRect();
      return {x: bounds.left + bounds.width / 2, y: bounds.top + 16};
    });
    await cdp.send('Input.dispatchTouchEvent', {type: 'touchStart', touchPoints: [start]});
    await cdp.send('Input.dispatchTouchEvent', {type: 'touchMove', touchPoints: [{x: start.x - 26, y: start.y + 65}]});
    await cdp.send('Input.dispatchTouchEvent', {type: 'touchMove', touchPoints: [{x: start.x + 26, y: start.y + 65}]});
    await cdp.send('Input.dispatchTouchEvent', {type: 'touchEnd', touchPoints: []});
    await expect(page.getByText('점수 · 1/5번', {exact: true})).toBeVisible();
    const reticle = page.locator('div[class*="_crosshair"]');
    const aims = JSON.parse(await reticle.getAttribute('data-observed-aim') ?? '[]');
    expect(aims).toHaveLength(2);
    expect(aims[1]).toBeGreaterThan(aims[0]);
    await expect(page.getByRole('status').filter({hasText: /배달 성공!|빗나갔어요/})).toBeVisible();
  });
});

test('mail plane finishes five deliveries and returns home', async ({page}, testInfo) => {
  test.setTimeout(180_000);
  await page.goto('/games/mail-plane');
  await page.getByRole('button', {name: '시작', exact: true}).click({timeout: 60_000});
  const target = page.getByRole('button', {name: '표시된 동료에게 메일 날리기'});

  let previousScore = 0;
  for (let shot = 1; shot <= 5; shot++) {
    await expect(target).toBeVisible({timeout: 20_000});
    if (shot === 2) {
      await expect(page.getByText(/배달 성공! \+\d+/)).toBeVisible();
    }
    // Activate in the frame that shows alignment. Separate tests exercise actual touch and keyboard input.
    await target.evaluate(arrow => new Promise<void>((resolve, reject) => {
      const stage = arrow.closest('section');
      if (!stage || !(arrow instanceof HTMLButtonElement)) return reject(new Error('No mail plane target button'));
      const button = arrow;
      const observer = new MutationObserver(fireWhenAligned);
      const timeout = setTimeout(() => {
        observer.disconnect();
        reject(new Error('No visible target during the aiming window'));
      }, 20_000);
      function fireWhenAligned() {
        if (!stage?.querySelector('[data-aligned=true]') || button.hidden || getComputedStyle(button).visibility !== 'visible') return;
        observer.disconnect();
        clearTimeout(timeout);
        button.click();
        resolve();
      }
      observer.observe(stage, {attributes: true, attributeFilter: ['data-aligned', 'style', 'hidden'], childList: true, subtree: true});
      fireWhenAligned();
    }));
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

test('mail plane misses a shot taken outside the aiming window', async ({page}) => {
  await page.goto('/games/mail-plane');
  await page.getByRole('button', {name: '시작', exact: true}).click({timeout: 60_000});
  const stage = page.getByRole('region', {name: '메일비행기 게임'});
  await stage.evaluate(element => new Promise<void>(resolve => {
    const observer = new MutationObserver(fireOutsideWindow);
    function fireOutsideWindow() {
      if (!element.querySelector('[data-aligned=false]')) return;
      observer.disconnect();
      element.dispatchEvent(new KeyboardEvent('keydown', {key: 'Enter', bubbles: true}));
      resolve();
    }
    observer.observe(element, {attributes: true, attributeFilter: ['data-aligned'], childList: true, subtree: true});
    fireOutsideWindow();
  }));
  await expect(page.getByText('점수 · 1/5번', {exact: true})).toBeVisible();
  await expect(page.getByRole('status').filter({hasText: '빗나갔어요'})).toBeVisible();
  await expect(page.getByText('점수 · 1/5번', {exact: true}).locator('..').locator('strong')).toHaveText('0');
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
