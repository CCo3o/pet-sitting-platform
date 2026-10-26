import { expect, test } from '@playwright/test'
import { mkdir } from 'node:fs/promises'

test.beforeAll(async () => { await mkdir('../docs/screenshots', { recursive: true }) })

test('登录页面、空表单校验和错误密码提示', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: '很高兴，再次见到你' })).toBeVisible()
  await page.screenshot({ path: '../docs/screenshots/login-desktop.png', fullPage: true })
  await page.getByRole('button', { name: '登录并进入' }).click()
  await expect(page.getByText('请输入用户名', { exact: true })).toBeVisible()
  await page.getByLabel('用户名', { exact: true }).fill('owner')
  await page.getByLabel('密码', { exact: true }).fill('Wrong123!')
  await page.getByRole('button', { name: '登录并进入' }).click()
  await expect(page.locator('.form-error')).toHaveText('用户名或密码错误')
  expect(errors).toEqual([])
})

for (const [label, title] of [['宠物主人', '宠物照护助手'], ['喂养员', '服务执行助手'], ['管理员', '平台运营助手']] as const) {
  test(`${label}登录、刷新恢复身份、退出后令牌失效`, async ({ page, request }) => {
    await page.goto('/login')
    await page.getByRole('button', { name: new RegExp(`^${label}`) }).click()
    await page.getByRole('button', { name: '登录并进入' }).click()
    await expect(page).toHaveURL('/workspace')
    await expect(page.locator('.role-badge')).toHaveText(label)
    await expect(page.getByRole('heading', { name: title, exact: true })).toBeVisible()
    await expect(page.locator('.el-loading-mask')).toHaveCount(0)
    const token = await page.evaluate(() => sessionStorage.getItem('pet-access-token'))
    if (label === '宠物主人') await page.screenshot({ path: '../docs/screenshots/owner-workspace.png', fullPage: true })
    if (label === '管理员') {
      await expect(page.locator('.agent-overview')).toBeVisible()
      await page.screenshot({ path: '../docs/screenshots/admin-workspace.png', fullPage: true })
    }
    await page.reload()
    await expect(page.locator('.role-badge')).toHaveText(label)
    await page.getByRole('button', { name: '退出登录' }).click()
    await expect(page).toHaveURL('/login')
    const replay = await request.get(`${process.env.E2E_BACKEND_URL ?? 'http://127.0.0.1:8080'}/api/auth/me`, { headers: { Authorization: `Bearer ${token}` } })
    expect(replay.status()).toBe(401)
    await page.goto('/workspace')
    await expect(page).toHaveURL('/login')
  })
}

test('注册喂养员并检查重复用户名错误', async ({ page }) => {
  const username = `e2e_${Date.now()}`
  await page.goto('/register')
  await page.screenshot({ path: '../docs/screenshots/register-desktop.png', fullPage: true })
  await page.getByRole('button', { name: /喂养员/ }).click()
  await page.getByLabel('昵称', { exact: true }).fill('浏览器测试喂养员')
  await page.getByLabel('用户名', { exact: true }).fill(username)
  await page.getByLabel('密码', { exact: true }).fill('Correct123!')
  await page.getByRole('button', { name: '创建账号并进入' }).click()
  await expect(page).toHaveURL('/workspace')
  await expect(page.locator('.role-badge')).toHaveText('喂养员')
  await page.getByRole('button', { name: '退出登录' }).click()
  await page.getByRole('link', { name: '创建账号', exact: true }).click()
  await page.getByLabel('昵称', { exact: true }).fill('另一位用户')
  await page.getByLabel('用户名', { exact: true }).fill(username)
  await page.getByLabel('密码', { exact: true }).fill('Correct123!')
  await page.getByRole('button', { name: '创建账号并进入' }).click()
  await expect(page.locator('.form-error')).toHaveText('用户名已被注册')
})

test('手机布局没有横向溢出且能够登录', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/login')
  await page.screenshot({ path: '../docs/screenshots/login-mobile.png', fullPage: true })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
  await page.getByRole('button', { name: /^宠物主人/ }).click()
  await page.getByRole('button', { name: '登录并进入' }).click()
  await expect(page).toHaveURL('/workspace')
  await expect(page.locator('.role-badge')).toHaveText('宠物主人')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
})
