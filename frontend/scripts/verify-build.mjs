import { chromium } from '@playwright/test'

const browser = await chromium.launch({ channel: process.env.PLAYWRIGHT_CHANNEL ?? 'chrome' })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
const errors=[]
page.on('pageerror', error => errors.push(error.message))
try {
  await page.goto(process.env.BUILD_CHECK_URL ?? 'http://127.0.0.1:5182/login')
  await page.waitForLoadState('networkidle')
  if(errors.length)throw new Error(errors.join('\n'))
  await page.getByLabel('用户名', {exact:true}).fill('admin')
  await page.getByLabel('密码', {exact:true}).fill('PetDemo123!')
  await page.getByRole('button',{name:'登录并进入'}).click()
  await page.waitForURL('**/workspace')
  await page.getByRole('link',{name:'平台管理'}).click()
  await page.getByRole('heading',{name:'喂养员资料审核'}).waitFor()
  await page.getByRole('link',{name:'服务订单',exact:true}).click()
  await page.getByRole('heading',{name:'服务订单',exact:true}).waitFor()
  if(errors.length)throw new Error(errors.join('\n'))
  console.log('生产构建浏览器验证通过：登录、工作台、管理页和订单页，无运行时异常。')
} catch(error) { console.error('浏览器异常：',errors);throw error }
finally { await browser.close() }
